package com.andreaserick.languagecoast.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.AddedCard
import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.data.ExportFormat
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.ImportedCard
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.exportCards
import com.andreaserick.languagecoast.data.parseCardFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A file read for import, waiting for the user to confirm.
 *
 * @property cards Every card found in the file, as read.
 * @property coastId The coast the cards will be added to.
 * @property swapColumns Whether the file has the translation first, so the columns must be swapped.
 * @property skipFirstRow Whether the first row is a header rather than a card.
 */
data class ImportPreview(
    val cards: List<ImportedCard>,
    val coastId: Int,
    val swapColumns: Boolean = false,
    val skipFirstRow: Boolean = false
) {
    /** The cards that will be imported, with the options applied. */
    val cardsToImport: List<ImportedCard>
        get() = cards.drop(if (skipFirstRow) 1 else 0).map {
            // Alternatives are translations of the back, so they don't survive swapping the sides.
            if (swapColumns) it.copy(nativeText = it.targetText, targetText = it.nativeText, alternatives = emptyList(), note = null) else it
        }
}

data class CardTransferUiState(
    val coasts: List<Coast> = emptyList(),
    val importPreview: ImportPreview? = null,
    val isImporting: Boolean = false,
    /** A one-off message for the user (e.g. why a file couldn't be imported), cleared by [CardTransferViewModel.messageShown]. */
    val message: String? = null
)

/** An export ready to be written to a file. */
data class ExportFile(val text: String, val cardCount: Int)

/** What an import added, so it can be undone, and how many cards it skipped because the coast already had them. */
data class ImportResult(val added: List<AddedCard>, val duplicates: Int)

/** Island for imported cards whose file doesn't name one. */
const val DEFAULT_IMPORT_ISLAND = "Imported"

/** Exports cards to Anki or Markdown files, and imports cards from CSV / tab-separated files. */
@HiltViewModel
class CardTransferViewModel @Inject constructor(
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    private val state = MutableStateFlow(CardTransferUiState())

    val uiState: StateFlow<CardTransferUiState> =
        combine(state, flashcards.observeCoasts()) { s, coasts -> s.copy(coasts = coasts) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardTransferUiState())

    /** The cards of coast [coastId], or of every coast if null, written in [format]. */
    suspend fun buildExport(coastId: Int?, format: ExportFormat): ExportFile {
        val contents = flashcards.getAllContent().filter { coastId == null || it.coast.coastId == coastId }
        return ExportFile(exportCards(contents, format, settings.nativeLanguage.first()), contents.sumOf { it.cards.size })
    }

    /** A file name like "language-coast-german.txt", or "language-coast.md" for all coasts. */
    fun exportFileName(coastId: Int?, format: ExportFormat): String {
        val language = uiState.value.coasts.firstOrNull { it.coastId == coastId }?.language
        val suffix = language?.let { "-" + it.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') }.orEmpty()
        return "language-coast$suffix.${format.extension}"
    }

    /** Reads the cards in [text] and opens the import preview, or explains why there is nothing to import. */
    fun onImportFileRead(text: String) {
        viewModelScope.launch {
            val coasts = flashcards.observeCoasts().first()
            val cards = parseCardFile(text)
            when {
                coasts.isEmpty() -> showMessage("Start a coast first, then import cards into it.")
                cards.isEmpty() -> showMessage("No cards found. The file needs a column for each side of the card.")
                else -> {
                    val coastId = settings.activeCoastId.first()?.takeIf { id -> coasts.any { it.coastId == id } }
                        ?: coasts.first().coastId
                    val header = looksLikeHeader(cards.first(), settings.nativeLanguage.first(), coasts)
                    state.update { it.copy(importPreview = ImportPreview(cards, coastId, skipFirstRow = header)) }
                }
            }
        }
    }

    fun onImportFileUnreadable() = showMessage("That file couldn't be read.")

    fun setImportCoast(coastId: Int) = updatePreview { it.copy(coastId = coastId) }

    fun setSwapColumns(swap: Boolean) = updatePreview { it.copy(swapColumns = swap) }

    fun setSkipFirstRow(skip: Boolean) = updatePreview { it.copy(skipFirstRow = skip) }

    fun cancelImport() = state.update { it.copy(importPreview = null) }

    /**
     * Adds the previewed cards to their coast, each into the island the file names (or [DEFAULT_IMPORT_ISLAND]).
     * Cards the coast already has, or that appear twice in the file, are skipped.
     */
    fun confirmImport(onImported: (ImportResult) -> Unit) {
        val preview = state.value.importPreview ?: return
        state.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            val existing = flashcards.getAllContent().firstOrNull { it.coast.coastId == preview.coastId }?.cards.orEmpty()
            val seen = existing.mapTo(HashSet()) { duplicateKey(it.nativeText, it.targetText) }
            val added = mutableListOf<AddedCard>()
            var duplicates = 0
            preview.cardsToImport.forEach { card ->
                if (!seen.add(duplicateKey(card.nativeText, card.targetText))) {
                    duplicates++
                } else {
                    added += flashcards.addFlashcard(
                        coastId = preview.coastId,
                        nativeText = card.nativeText,
                        targetText = card.targetText,
                        category = card.island ?: DEFAULT_IMPORT_ISLAND,
                        alternatives = card.alternatives,
                        note = card.note
                    )
                }
            }
            state.update { it.copy(importPreview = null, isImporting = false) }
            onImported(ImportResult(added, duplicates))
        }
    }

    /** Removes what an import added. Not tied to this ViewModel's scope, so undo works after leaving the screen. */
    suspend fun undoImport(result: ImportResult) {
        // Newest first, so each island created by the import is empty (and removed) when its first card is undone.
        result.added.asReversed().forEach { flashcards.undoAdd(it) }
    }

    fun messageShown() = state.update { it.copy(message = null) }

    private fun showMessage(message: String) = state.update { it.copy(message = message) }

    private fun updatePreview(transform: (ImportPreview) -> ImportPreview) =
        state.update { s -> s.copy(importPreview = s.importPreview?.let(transform)) }

    /** A first row like "English, German" or "Front, Back" names the columns rather than being a card. */
    private fun looksLikeHeader(first: ImportedCard, nativeLanguage: String, coasts: List<Coast>): Boolean {
        val names = (listOf(nativeLanguage, "front", "back", "native", "translation") + coasts.map { it.language })
            .mapTo(HashSet()) { it.lowercase() }
        return first.nativeText.lowercase() in names && first.targetText.lowercase() in names
    }

    private fun duplicateKey(nativeText: String, targetText: String) =
        nativeText.trim().lowercase() to targetText.trim().lowercase()
}
