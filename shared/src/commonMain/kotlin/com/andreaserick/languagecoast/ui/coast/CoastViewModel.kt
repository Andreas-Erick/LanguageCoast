package com.andreaserick.languagecoast.ui.coast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.DeletedContent
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.ImportProgress
import com.andreaserick.languagecoast.data.IslandSummary
import com.andreaserick.languagecoast.data.LanguageIsland
import com.andreaserick.languagecoast.data.Languages
import com.andreaserick.languagecoast.data.PROFILE_PLACEHOLDERS
import com.andreaserick.languagecoast.data.PhraseBank
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.StarterImporter
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.starterTopicKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class CoastUiState(
    val coastId: Int = 0,
    val coastName: String = "",
    /** The coast language's name for itself (e.g. "Íslenska"), if it differs from the English name. */
    val nativeName: String? = null,
    val islands: List<IslandSummary> = emptyList(),
    /** Null until loaded. */
    val today: LocalDate? = null,
    val isLoading: Boolean = true,
    val coastLanguage: String = "",
    /** The starter topics not added to this coast yet, in the bank's order. */
    val starterTopics: List<StarterTopicOption> = emptyList(),
    /** Starter topics being added to this coast right now, or just added. */
    val import: ImportProgress? = null,
    /** The provider that will translate them. */
    val translationProvider: TranslationProvider = TranslationProvider.OnDevice
)

/**
 * Shows the islands of one coast, with the starter topics it can still add whole. Opening a coast makes
 * it the one new cards are added to.
 */
class CoastViewModel(
    savedStateHandle: SavedStateHandle,
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val importer: StarterImporter
) : ViewModel() {

    // Populated by Navigation from the CoastScreenRoute properties.
    private val coastId: Int = checkNotNull(savedStateHandle["coastId"])
    private val coastName: String = savedStateHandle["coastName"] ?: ""

    val uiState: StateFlow<CoastUiState> =
        combine(
            flashcards.observeCoast(coastId),
            flashcards.observeIslandSummaries(coastId, now = clock.now().toEpochMilliseconds()),
            settings.starterTopicsAdded,
            importer.progress,
            settings.translationProvider
        ) { coast, islands, added, progress, provider ->
            val language = coast?.language.orEmpty()
            CoastUiState(
                coastId = coastId,
                coastName = coast?.displayName ?: coastName,
                nativeName = coast?.let { Languages.byName(it.language) }?.nativeName?.takeIf { it != coast?.language },
                islands = islands,
                today = clock.todayIn(timeZone),
                isLoading = false,
                coastLanguage = language,
                starterTopics = PhraseBank.THEMES
                    .filterNot { starterTopicKey(coastId, it.category) in added }
                    .map { theme ->
                        val blanks = theme.sentences.flatMap(PhraseBank::placeholders).distinct()
                        StarterTopicOption(
                            theme = theme,
                            sentenceCount = importer.usableSentences(theme, language, PROFILE_PLACEHOLDERS.associateWith { "x" }).size,
                            profileBlanks = PROFILE_PLACEHOLDERS.filter { it in blanks }
                        )
                    },
                import = progress?.takeIf { it.coastId == coastId },
                translationProvider = provider
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoastUiState(coastId = coastId, coastName = coastName))

    /** Adds the starter [topics] (by category) to this coast in the background, with the person's [fillers] (name, city, ...). */
    fun addStarterTopics(topics: List<String>, fillers: Map<String, String>) {
        importer.start(coastId, topics, fillers)
    }

    /** Clears a finished import from the screen. */
    fun dismissImport() = importer.dismiss()

    init {
        viewModelScope.launch { settings.setActiveCoastId(coastId) }
    }

    /** Deletes [island] right away; [onDeleted] receives what was removed so it can be restored. */
    fun deleteIsland(island: LanguageIsland, onDeleted: (DeletedContent) -> Unit) {
        viewModelScope.launch { onDeleted(flashcards.deleteIsland(island)) }
    }

    /** Puts back a deleted island. Not tied to this ViewModel's scope, so undo works after leaving the screen. */
    suspend fun restore(content: DeletedContent) = flashcards.restore(content)
}
