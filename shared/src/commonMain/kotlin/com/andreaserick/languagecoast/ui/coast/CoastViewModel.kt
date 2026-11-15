package com.andreaserick.languagecoast.ui.coast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.BuildProgress
import com.andreaserick.languagecoast.data.DeletedContent
import com.andreaserick.languagecoast.data.EngineChoice
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.ImportProgress
import com.andreaserick.languagecoast.data.IslandOrder
import com.andreaserick.languagecoast.data.IslandSummary
import com.andreaserick.languagecoast.data.IslandTalk
import com.andreaserick.languagecoast.data.LanguageIsland
import com.andreaserick.languagecoast.data.Level
import com.andreaserick.languagecoast.data.levelKey
import com.andreaserick.languagecoast.data.Languages
import com.andreaserick.languagecoast.data.PROFILE_PLACEHOLDERS
import com.andreaserick.languagecoast.data.Pace
import com.andreaserick.languagecoast.data.PersonalIslandBuilder
import com.andreaserick.languagecoast.data.PersonalIslands
import com.andreaserick.languagecoast.data.PhraseBank
import com.andreaserick.languagecoast.data.Profile
import com.andreaserick.languagecoast.data.Route
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.StarterImporter
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.TALK_CARD_LIMIT
import com.andreaserick.languagecoast.data.TranslationEngine
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.Translator
import com.andreaserick.languagecoast.data.WORKSHEET_COUNT_RANGE
import com.andreaserick.languagecoast.data.WorksheetGenerator
import com.andreaserick.languagecoast.data.WorksheetOptions
import com.andreaserick.languagecoast.data.WorksheetPdf
import com.andreaserick.languagecoast.data.WorksheetPrinter
import com.andreaserick.languagecoast.data.WorksheetResult
import com.andreaserick.languagecoast.data.buildTalkPrompt
import com.andreaserick.languagecoast.data.currentEngine
import com.andreaserick.languagecoast.data.parseTalk
import com.andreaserick.languagecoast.data.personalIslandKey
import com.andreaserick.languagecoast.data.routeFor
import com.andreaserick.languagecoast.data.starterTopicKey
import com.andreaserick.languagecoast.speech.Speaker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

/**
 * What a run of the island builder left on the coast, for the user to look over.
 *
 * @property added The cards it wrote.
 * @property kept Studied cards that stayed when an island was written again; the answers behind them may have changed.
 */
data class BuildReview(val added: List<Flashcard> = emptyList(), val kept: List<Flashcard> = emptyList())

/**
 * An island being turned into a talk to rehearse.
 *
 * @property talk The talk, once written.
 * @property error Why there is none.
 * @property canSpeak Whether the phone has a voice for the coast's language.
 */
data class TalkUiState(
    val island: LanguageIsland,
    val talk: IslandTalk? = null,
    val error: String? = null,
    val canSpeak: Boolean = false
) {
    val isWriting: Boolean get() = talk == null && error == null
}

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
    val worksheet: WorksheetUiState = WorksheetUiState(),
    /** The islands about the user this coast doesn't have yet, or whose answers changed since they were written. */
    val personalIslands: List<PersonalIslandOption> = emptyList(),
    /** Islands being written on this coast right now, or just written. */
    val build: BuildProgress? = null,
    /** What the finished [build] left on the coast, to look over. */
    val buildReview: BuildReview = BuildReview(),
    /** The coast's way through the levels; null until loaded. */
    val route: Route? = null,
    /** What each island about the user would be written from, by island id. */
    val islandFacts: Map<String, List<Pair<String, String>>> = emptyMap(),
    val pace: Pace = Pace.Steady,
    /** An island being turned into a talk, while its sheet is open. */
    val talk: TalkUiState? = null,
    /** A level the user has reached on this coast and hasn't been congratulated on yet (the highest, if several). */
    val levelReached: Level? = null
) {
    /** Cards on all of the coast's islands. */
    val cardCount: Int get() = islands.sumOf { it.cardCount }

    /** Whether an AI model is set up that can write islands and talks; on-device translation can't. */
    val canWrite: Boolean get() = translationProvider != TranslationProvider.OnDevice

    /** Whether islands are being written right now. */
    val isBuilding: Boolean get() = build?.isFinished == false
}

/**
 * Shows the islands of one coast and its route through the levels: the starter topics it can still add
 * whole and the islands it can have written. Makes the coast's practice sheets and turns an island into a
 * talk to rehearse. Opening a coast makes it the one new cards are added to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoastViewModel(
    savedStateHandle: SavedStateHandle,
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val importer: StarterImporter,
    private val builder: PersonalIslandBuilder,
    private val worksheets: WorksheetGenerator,
    private val printer: WorksheetPrinter,
    private val translator: Translator,
    private val speaker: Speaker
) : ViewModel() {

    // Populated by Navigation from the CoastScreenRoute properties.
    private val coastId: Int = checkNotNull(savedStateHandle["coastId"])
    private val coastName: String = savedStateHandle["coastName"] ?: ""

    private val worksheet = MutableStateFlow(WorksheetUiState())
    private var worksheetJob: Job? = null
    private val talk = MutableStateFlow<TalkUiState?>(null)
    private var talkJob: Job? = null

    /** The cards the finished build wrote and kept, as they are now (a removed one drops out). */
    private val buildReview = builder.progress.flatMapLatest { progress ->
        if (progress == null || !progress.isFinished || progress.coastId != coastId) flowOf(BuildReview())
        else flashcards.observeAllCards().map { cards ->
            val byId = cards.associate { it.card.cardId to it.card }
            BuildReview(added = progress.addedCardIds.mapNotNull(byId::get), kept = progress.keptCardIds.mapNotNull(byId::get))
        }
    }

    val uiState: StateFlow<CoastUiState> =
        combine(
            flashcards.observeCoast(coastId),
            flashcards.observeIslandSummaries(coastId, now = clock.now().toEpochMilliseconds()),
            combine(settings.starterTopicsAdded, settings.profile, settings.nativeLanguage, settings.personalAnswers, settings.personalIslandsBuilt, ::About),
            combine(importer.progress, worksheet, builder.progress, buildReview, talk, ::Activity),
            combine(settings.translationProvider, settings.pace, settings.levelsCelebrated, ::Triple)
        ) { coast, islands, about, activity, (provider, pace, celebrated) ->
            val language = coast?.language.orEmpty()
            val prefix = "$coastId:"
            val builtHere = about.built.filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix) }
            val topicsHere = about.starterTopicsAdded.filter { it.startsWith(prefix) }.mapTo(HashSet()) { it.removePrefix(prefix) }
            val route = routeFor(islands, topicsHere, builtHere, about.profile, about.answers)
            CoastUiState(
                coastId = coastId,
                coastName = coast?.displayName ?: coastName,
                nativeName = coast?.let { Languages.byName(it.language) }?.nativeName?.takeIf { it != coast?.language },
                islands = islands,
                today = clock.todayIn(timeZone),
                isLoading = false,
                coastLanguage = language,
                starterTopics = PhraseBank.THEMES
                    // A topic whose island was deleted from the coast can be added again.
                    .filterNot { theme -> starterTopicKey(coastId, theme.category) in about.starterTopicsAdded && islands.any { it.island.name == theme.category } }
                    .map { theme ->
                        val blanks = theme.sentences.flatMap(PhraseBank::placeholders).distinct()
                        StarterTopicOption(
                            theme = theme,
                            sentenceCount = importer.usableSentences(theme, language, PROFILE_PLACEHOLDERS.associateWith { "x" }).size,
                            profileBlanks = PROFILE_PLACEHOLDERS.filter { it in blanks }
                        )
                    },
                import = activity.import?.takeIf { it.coastId == coastId },
                translationProvider = provider,
                profile = about.profile.fillers(),
                nativeLanguage = about.nativeLanguage,
                canPrintWorksheets = printer.isAvailable,
                worksheet = activity.worksheet,
                personalIslands = PersonalIslands.ALL.mapNotNull { island ->
                    // An island the user deleted from the coast can be written again.
                    val written = about.built[personalIslandKey(coastId, island.id)]?.takeIf { islands.any { it.island.name == island.name } }
                    val state = when {
                        !island.isUnlocked(about.answers) -> if (written == null) PersonalIslandState.Locked else return@mapNotNull null
                        written == null -> PersonalIslandState.Ready
                        written != island.fingerprint(about.profile, about.answers) -> PersonalIslandState.Changed
                        else -> return@mapNotNull null
                    }
                    PersonalIslandOption(island, state, island.facts(about.profile, about.answers))
                },
                build = activity.build?.takeIf { it.coastId == coastId },
                buildReview = activity.review,
                route = route,
                levelReached = route.levels.lastOrNull { it.isComplete && levelKey(coastId, it.level) !in celebrated }?.level,
                islandFacts = (PersonalIslands.ALL + PersonalIslands.WRITTEN).associate { it.id to it.facts(about.profile, about.answers) },
                pace = pace,
                talk = activity.talk
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoastUiState(coastId = coastId, coastName = coastName))

    /** Adds the starter [topics] (by category) to this coast in the background, with the person's [fillers] (name, city, ...). */
    fun addStarterTopics(topics: List<String>, fillers: Map<String, String>) {
        importer.start(coastId, topics, fillers)
    }

    /** Clears a finished import from the screen. */
    fun dismissImport() = importer.dismiss()

    /** Has the AI provider write [orders] (islands about the user, theme islands, or islands a level up) on this coast, in the background. */
    fun buildIslands(orders: List<IslandOrder>) {
        builder.start(coastId, orders)
    }

    /** Stops the islands being written; what is written stays. */
    fun stopBuild() = builder.stop()

    /** Clears a finished run of [buildIslands] from the screen. */
    fun dismissBuild() = builder.dismiss()

    /** Records that the user saw they reached [level] (and every level under it) on this coast. */
    fun celebrated(level: Level) {
        viewModelScope.launch { Level.entries.filter { it <= level }.forEach { settings.markLevelCelebrated(coastId, it) } }
    }

    /** Removes the cards with [cardIds] that the user unticked when looking over what was written. */
    fun removeCards(cardIds: Set<Int>) {
        val review = uiState.value.buildReview
        val cards = (review.added + review.kept).filter { it.cardId in cardIds }
        viewModelScope.launch { cards.forEach { flashcards.deleteFlashcard(it) } }
    }

    /**
     * Has the AI provider join [island]'s cards into a short talk to rehearse aloud. The talk (or why there
     * is none) arrives in [CoastUiState.talk].
     */
    fun writeTalk(island: LanguageIsland) {
        talkJob?.cancel()
        speaker.stop()
        talk.value = TalkUiState(island)
        talkJob = viewModelScope.launch {
            val coast = flashcards.observeCoast(coastId).first() ?: return@launch
            val canSpeak = speaker.canSpeak(coast.language).first()
            talk.value = TalkUiState(island, canSpeak = canSpeak)
            val cards = flashcards.observeCards(island.islandId).first().sortedBy { it.cardId }.take(TALK_CARD_LIMIT)
            val result: Pair<IslandTalk?, String?> = when (val choice = settings.currentEngine()) {
                is EngineChoice.Missing -> null to choice.message
                is EngineChoice.Ready -> if (choice.engine == TranslationEngine.OnDevice) {
                    null to "A talk is written by an AI model. Pick an AI provider under Settings › Translation first."
                } else {
                    val prompt = buildTalkPrompt(
                        island.name, cards.map { it.nativeText to it.targetText }, settings.nativeLanguage.first(), coast.language, coast.region, settings.wording.first()
                    )
                    val completion = translator.complete(choice.engine, prompt)
                    val written = completion.text?.let(::parseTalk)
                    written to if (written == null) completion.errorMessage ?: "The AI provider's answer couldn't be read. Try again." else null
                }
            }
            talk.value = TalkUiState(island, talk = result.first, error = result.second, canSpeak = canSpeak)
        }
    }

    /** Reads the talk aloud in the coast's language. */
    fun speakTalk() {
        val text = talk.value?.talk?.targetText ?: return
        speaker.speak(text, uiState.value.coastLanguage)
    }

    /** Closes the talk. */
    fun dismissTalk() {
        talkJob?.cancel()
        speaker.stop()
        talk.value = null
    }

    /** What Settings knows about the user and about what this coast already has. */
    private data class About(
        val starterTopicsAdded: Set<String>,
        val profile: Profile,
        val nativeLanguage: String,
        val answers: Map<String, String>,
        val built: Map<String, String>
    )

    /** What is going on on the coast right now. */
    private data class Activity(
        val import: ImportProgress?,
        val worksheet: WorksheetUiState,
        val build: BuildProgress?,
        val review: BuildReview,
        val talk: TalkUiState?
    )

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

    /** Stops reading aloud when the screen is left. */
    override fun onCleared() {
        speaker.stop()
    }
}
