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
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.WORKSHEET_COUNT_RANGE
import com.andreaserick.languagecoast.data.WorksheetGenerator
import com.andreaserick.languagecoast.data.WorksheetOptions
import com.andreaserick.languagecoast.data.WorksheetPdf
import com.andreaserick.languagecoast.data.WorksheetPrinter
import com.andreaserick.languagecoast.data.WorksheetResult
import com.andreaserick.languagecoast.data.starterTopicKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * The coast's practice sheet: what the user picked for it, and how making it is going.
 *
 * @property written Sentences the AI provider has written so far, while [isCreating].
 * @property pdf The finished sheet, until the user starts another.
 * @property sentenceCount How many sentences [pdf] has, which can be fewer than were asked for.
 * @property error Why the last attempt gave no sheet.
 */
data class WorksheetUiState(
    val options: WorksheetOptions = WorksheetOptions(),
    val isCreating: Boolean = false,
    val written: Int = 0,
    val pdf: WorksheetPdf? = null,
    val sentenceCount: Int = 0,
    val error: String? = null
)

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
    val translationProvider: TranslationProvider = TranslationProvider.OnDevice,
    /** The user's blanks from Settings, by placeholder, which a topic sheet starts from. */
    val profile: Map<String, String> = emptyMap(),
    val nativeLanguage: String = SettingsDefaults.NATIVE_LANGUAGE,
    /** Whether this platform can make a practice sheet (a PDF) from the coast's cards. */
    val canPrintWorksheets: Boolean = false,
    val worksheet: WorksheetUiState = WorksheetUiState()
) {
    /** Cards on all of the coast's islands. */
    val cardCount: Int get() = islands.sumOf { it.cardCount }
}

/**
 * Shows the islands of one coast, with the starter topics it can still add whole, and makes the coast's
 * practice sheets. Opening a coast makes it the one new cards are added to.
 */
class CoastViewModel(
    savedStateHandle: SavedStateHandle,
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val importer: StarterImporter,
    private val worksheets: WorksheetGenerator,
    private val printer: WorksheetPrinter
) : ViewModel() {

    // Populated by Navigation from the CoastScreenRoute properties.
    private val coastId: Int = checkNotNull(savedStateHandle["coastId"])
    private val coastName: String = savedStateHandle["coastName"] ?: ""

    private val worksheet = MutableStateFlow(WorksheetUiState())
    private var worksheetJob: Job? = null

    val uiState: StateFlow<CoastUiState> =
        combine(
            flashcards.observeCoast(coastId),
            flashcards.observeIslandSummaries(coastId, now = clock.now().toEpochMilliseconds()),
            combine(settings.starterTopicsAdded, settings.profile, settings.nativeLanguage, ::Triple),
            combine(importer.progress, worksheet, ::Pair),
            settings.translationProvider
        ) { coast, islands, (added, profile, nativeLanguage), (progress, worksheet), provider ->
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
                translationProvider = provider,
                profile = profile.fillers(),
                nativeLanguage = nativeLanguage,
                canPrintWorksheets = printer.isAvailable,
                worksheet = worksheet
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

    /** Changes what the next practice sheet will be like; the number of sentences is kept within [WORKSHEET_COUNT_RANGE]. */
    fun setWorksheetOptions(options: WorksheetOptions) {
        worksheet.update { it.copy(options = options.copy(count = options.count.coerceIn(WORKSHEET_COUNT_RANGE)), error = null) }
    }

    /**
     * Has the AI provider write a practice sheet from this coast's cards and draws it as a PDF. How far it
     * is, and the file or why there is none, arrive in [CoastUiState.worksheet].
     */
    fun createWorksheet() {
        if (worksheet.value.isCreating) return
        val options = worksheet.value.options
        worksheet.update { it.copy(isCreating = true, written = 0, pdf = null, error = null) }
        worksheetJob = viewModelScope.launch {
            val result = worksheets.generate(coastId, options) { written -> worksheet.update { it.copy(written = written) } }
            val finished = when (result) {
                is WorksheetResult.Failed -> worksheet.value.copy(error = result.message)
                is WorksheetResult.Ready -> {
                    val pdf = printer.print(result.worksheet)
                    if (pdf == null) worksheet.value.copy(error = "The PDF couldn't be made. Check that the phone has storage left.")
                    else worksheet.value.copy(pdf = pdf, sentenceCount = result.worksheet.sentences.size)
                }
            }
            worksheet.value = finished.copy(isCreating = false)
        }
    }

    /** Stops a practice sheet that is being made. */
    fun cancelWorksheet() {
        worksheetJob?.cancel()
        worksheet.update { it.copy(isCreating = false, written = 0) }
    }

    /** Puts the finished practice sheet aside, to make another. */
    fun startNewWorksheet() {
        worksheet.update { it.copy(pdf = null, error = null) }
    }

    /** Deletes [island] right away; [onDeleted] receives what was removed so it can be restored. */
    fun deleteIsland(island: LanguageIsland, onDeleted: (DeletedContent) -> Unit) {
        viewModelScope.launch { onDeleted(flashcards.deleteIsland(island)) }
    }

    /** Puts back a deleted island. Not tied to this ViewModel's scope, so undo works after leaving the screen. */
    suspend fun restore(content: DeletedContent) = flashcards.restore(content)
}
