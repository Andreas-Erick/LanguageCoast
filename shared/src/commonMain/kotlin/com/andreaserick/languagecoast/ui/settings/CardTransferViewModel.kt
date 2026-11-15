package com.andreaserick.languagecoast.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.AddedCard
import com.andreaserick.languagecoast.data.AnkiNote
import com.andreaserick.languagecoast.data.logError
import com.andreaserick.languagecoast.data.CardEdit
import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.data.ExportFormat
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.ImportFileKind
import com.andreaserick.languagecoast.data.ImportedCard
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.ankiNoteCards
import com.andreaserick.languagecoast.data.exportCards
import com.andreaserick.languagecoast.data.importFileKind
import com.andreaserick.languagecoast.data.parseCardFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A file read for import, waiting for the user to confirm.
 *
 * @property cards Every card found in the file, as read.
 * @property coastId The coast the cards will be added to.
 * @property swapColumns Whether the file has the translation first, so the columns must be swapped.
 * @property skipFirstRow Whether the first row is a header rather than a card.
 * @property mayHaveHeader Whether the file is one that can start with a header row: a table of text can, an
 *     Anki package can't, so the option isn't offered for it.
 */
data class ImportPreview(
    val cards: List<ImportedCard>,
    val coastId: Int,
    val swapColumns: Boolean = false,
    val skipFirstRow: Boolean = false,
    val mayHaveHeader: Boolean = true
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

/**
 * What an import added, so it can be undone, and how many cards it skipped because the coast already had them.
 *
 * @property reformatted How many of the [duplicates] got their line breaks from the file: cards imported
 *     before imports kept them, whose sides ran together into one line. Undoing the import leaves them so.
 */
data class ImportResult(val added: List<AddedCard>, val duplicates: Int, val reformatted: Int = 0)

/** Island for imported cards whose file doesn't name one. */
const val DEFAULT_IMPORT_ISLAND = "Imported"

/** Exports cards to Anki, CSV or Markdown files, and imports cards from Anki packages and CSV / tab-separated files. */
class CardTransferViewModel(
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

    /** A file name like "language-coast-german.csv", or "language-coast.md" for all coasts. */
    fun exportFileName(coastId: Int?, format: ExportFormat): String {
        val language = uiState.value.coasts.firstOrNull { it.coastId == coastId }?.language
        val suffix = language?.let { "-" + it.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') }.orEmpty()
        return "language-coast$suffix.${format.extension}"
    }

    /*
     * Reading the cards out of a large file takes a moment (a shared Anki deck easily has 20,000 notes), so the
     * screen does that part away from the main thread and hands over the cards: [acceptsImportText] and
     * [parseCardFile] for a text file, then [onTextCardsRead]; [ankiNoteCards] for a package, then
     * [onAnkiCardsRead]. [onImportFileRead] and [onAnkiPackageRead] do both steps in one, on the caller's thread.
     */

    /**
     * Reads the cards in [text], a CSV or tab-separated file, and opens the import preview, or explains why
     * there is nothing to import. A file that isn't text is turned away: an Anki package has to come through
     * [onAnkiPackageRead], and a spreadsheet's own format can't be read at all.
     */
    fun onImportFileRead(text: String) {
        if (acceptsImportText(text)) onTextCardsRead(parseCardFile(text))
    }

    /**
     * Whether [text] is a file of text that cards can be read from. If it isn't, the user is told what to
     * pick instead. It only looks at the start of the file, so it is quick whatever the file's size.
     */
    fun acceptsImportText(text: String): Boolean {
        val refusal = when (importFileKind(text)) {
            ImportFileKind.Archive -> "That is a zip file, not a card file. Pick a CSV or tab-separated file, or an Anki package (.apkg)."
            ImportFileKind.Binary -> "That file isn't plain text. Pick a CSV or tab-separated file saved as UTF-8."
            ImportFileKind.Text -> return true
        }
        showMessage(refusal)
        return false
    }

    /** Opens the import preview for [cards], read from a CSV or tab-separated file with [parseCardFile]. */
    fun onTextCardsRead(cards: List<ImportedCard>) {
        preview(cards, mayHaveHeader = true, noCards = "No cards found. The file needs a column for each side of the card.")
    }

    /**
     * Opens the import preview for [notes], read from an Anki package (.apkg): each note's first two fields
     * with text become a card, and its deck names the island.
     */
    fun onAnkiPackageRead(notes: List<AnkiNote>) = onAnkiCardsRead(ankiNoteCards(notes))

    /** Opens the import preview for [cards], made from an Anki package's notes with [ankiNoteCards]. */
    fun onAnkiCardsRead(cards: List<ImportedCard>) {
        preview(cards, mayHaveHeader = false, noCards = "No cards found in that package. Its notes need two fields with text.")
    }

    /** Opens the import preview for [cards] into the active coast, or says why not: [noCards] when there are none. */
    private fun preview(cards: List<ImportedCard>, mayHaveHeader: Boolean, noCards: String) {
        viewModelScope.launch {
            val coasts = flashcards.observeCoasts().first()
            when {
                coasts.isEmpty() -> showMessage("Start a coast first, then import cards into it.")
                cards.isEmpty() -> showMessage(noCards)
                else -> {
                    val coastId = settings.activeCoastId.first()?.takeIf { id -> coasts.any { it.coastId == id } }
                        ?: coasts.first().coastId
                    val header = mayHaveHeader && looksLikeHeader(cards.first(), settings.nativeLanguage.first(), coasts)
                    state.update { it.copy(importPreview = ImportPreview(cards, coastId, skipFirstRow = header, mayHaveHeader = mayHaveHeader)) }
                }
            }
        }
    }

    /** Tells the user the picked file couldn't be read: with [reason] if there is more to say than that. */
    fun onImportFileUnreadable(reason: String? = null) = showMessage(reason ?: "That file couldn't be read.")

    /** Imports the previewed cards into coast [coastId]. */
    fun setImportCoast(coastId: Int) = updatePreview { it.copy(coastId = coastId) }

    /** Whether the file's first column is the translation rather than the native text. */
    fun setSwapColumns(swap: Boolean) = updatePreview { it.copy(swapColumns = swap) }

    /** Whether the file's first row is left out, e.g. because it names the columns. */
    fun setSkipFirstRow(skip: Boolean) = updatePreview { it.copy(skipFirstRow = skip) }

    /** Closes the import preview without adding any cards. */
    fun cancelImport() = state.update { it.copy(importPreview = null) }

    /**
     * Adds the previewed cards to their coast, each into the island the file names (or [DEFAULT_IMPORT_ISLAND]).
     * Cards the coast already has, or that appear twice in the file, are skipped; one the coast has in fewer
     * lines than the file is given the file's layout, and keeps everything else (see [ImportResult.reformatted]).
     */
    fun confirmImport(onImported: (ImportResult) -> Unit) {
        val preview = state.value.importPreview ?: return
        state.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            val existing = flashcards.getAllContent().firstOrNull { it.coast.coastId == preview.coastId }?.cards.orEmpty()
                .associateByTo(HashMap()) { duplicateKey(it.nativeText, it.targetText) }
            val seen = existing.keys.toHashSet()
            val newCards = mutableListOf<ImportedCard>()
            var duplicates = 0
            var reformatted = 0
            preview.cardsToImport.forEach { card ->
                val key = duplicateKey(card.nativeText, card.targetText)
                if (seen.add(key)) {
                    newCards += card
                } else {
                    duplicates++
                    // Once per card: the file may have it twice.
                    val old = existing.remove(key)
                    if (old != null && (hasNewLines(old.nativeText, card.nativeText) || hasNewLines(old.targetText, card.targetText))) {
                        flashcards.editCard(old.cardId, CardEdit(card.nativeText, card.targetText, old.alternatives, old.note, old.islandId))
                        reformatted++
                    }
                }
            }
            // All the new cards in one go: saved together or not at all, and the rest of the app hears of it
            // once instead of once per card, which is what made a large deck slow to import.
            val added = try {
                flashcards.addFlashcards(preview.coastId, newCards, DEFAULT_IMPORT_ISLAND)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError(TAG, "The imported cards couldn't be saved", e)
                state.update { it.copy(importPreview = null, isImporting = false, message = "The cards couldn't be saved. Nothing was added.") }
                return@launch
            }
            state.update { it.copy(importPreview = null, isImporting = false) }
            onImported(ImportResult(added, duplicates, reformatted))
        }
    }

    /** Removes what an import added. Not tied to this ViewModel's scope, so undo works after leaving the screen. */
    suspend fun undoImport(result: ImportResult) = flashcards.undoAddAll(result.added)

    /** Clears the message once it has been shown. */
    fun messageShown() = state.update { it.copy(message = null) }

    /** Shows [message] to the user once. */
    private fun showMessage(message: String) = state.update { it.copy(message = message) }

    /** Applies [transform] to the import preview, if one is open. */
    private fun updatePreview(transform: (ImportPreview) -> ImportPreview) =
        state.update { s -> s.copy(importPreview = s.importPreview?.let(transform)) }

    /** A first row like "English, German" or "Front, Back" names the columns rather than being a card. */
    private fun looksLikeHeader(first: ImportedCard, nativeLanguage: String, coasts: List<Coast>): Boolean {
        val names = (listOf(nativeLanguage, "front", "back", "native", "translation") + coasts.map { it.language })
            .mapTo(HashSet()) { it.lowercase() }
        return first.nativeText.lowercase() in names && first.targetText.lowercase() in names
    }

    /** Cards with the same texts, ignoring case and how the text is spaced and broken into lines, count as duplicates. */
    private fun duplicateKey(nativeText: String, targetText: String) =
        nativeText.trim().replace(WHITESPACE, " ").lowercase() to targetText.trim().replace(WHITESPACE, " ").lowercase()

    /** Whether [imported] has the same text as [old] (as duplicates do), but set in more lines. */
    private fun hasNewLines(old: String, imported: String) = imported.lines().size > old.lines().size

    private companion object {
        const val TAG = "CardTransferViewModel"
        val WHITESPACE = Regex("\\s+")
    }
}
