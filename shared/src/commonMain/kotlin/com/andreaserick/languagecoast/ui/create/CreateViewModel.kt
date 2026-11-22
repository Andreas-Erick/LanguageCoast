package com.andreaserick.languagecoast.ui.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.AddedCard
import com.andreaserick.languagecoast.data.CardEdit
import com.andreaserick.languagecoast.data.DeletedContent
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.data.CoastSummary
import com.andreaserick.languagecoast.data.DEFAULT_CATEGORY
import com.andreaserick.languagecoast.data.DailyPlan
import com.andreaserick.languagecoast.data.EngineChoice
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.Language
import com.andreaserick.languagecoast.data.LanguageIsland
import com.andreaserick.languagecoast.data.Pace
import com.andreaserick.languagecoast.data.Route
import com.andreaserick.languagecoast.data.RouteLevel
import com.andreaserick.languagecoast.data.observeRoute
import com.andreaserick.languagecoast.data.PlacedCard
import com.andreaserick.languagecoast.data.RecentCard
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.SuggestionPool
import com.andreaserick.languagecoast.data.TranslationEngine
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.TranslationRequest
import com.andreaserick.languagecoast.data.Translator
import com.andreaserick.languagecoast.data.availableCoastLanguages
import com.andreaserick.languagecoast.data.currentEngine
import com.andreaserick.languagecoast.data.dailyPlan
import com.andreaserick.languagecoast.data.engineReady
import com.andreaserick.languagecoast.data.fallbackCloudEngine
import com.andreaserick.languagecoast.data.isSameSentence
import com.andreaserick.languagecoast.data.islandEmoji
import com.andreaserick.languagecoast.data.logError
import com.andreaserick.languagecoast.data.normalizedWords
import com.andreaserick.languagecoast.data.providerAwaitingPermission
import com.andreaserick.languagecoast.data.splitSentences
import com.andreaserick.languagecoast.data.suggestIsland
import com.andreaserick.languagecoast.data.topicRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Category used for manual cards when the user leaves the category blank. */
const val DEFAULT_MANUAL_CATEGORY = DEFAULT_CATEGORY

/** How long the save feedback card stays on screen; long enough to read the translation and undo it. */
const val RESULT_VISIBLE_MILLIS = 8_000L

/** How many recently added cards the screen lists. */
const val RECENT_CARDS = 3

/** The language the starter sentences ([com.andreaserick.languagecoast.data.PhraseBank]) are written in. */
private const val BANK_LANGUAGE = "English"

/** After this many unreadable answers in a row from the local model, a cloud provider is offered instead. */
const val LOCAL_FAILURES_BEFORE_FALLBACK = 2

private const val TAG = "CreateViewModel"

/** Shown when saving failed for a reason the user can do nothing about; the detail goes to the log. */
private const val SAVE_FAILED = "Something went wrong. Try again."

/** Words shorter than this (articles, prepositions) say nothing about which island a sentence belongs on. */
private const val ISLAND_WORD_LENGTH = 4

/** Where a new card's translation comes from. */
enum class InputMode {
    /** The user writes in their language; the AI translates. */
    Translate,
    /** The user writes in the coast's language; the AI fills in their language and corrects slips. */
    WriteInTarget,
    /** The user types both sides. */
    Manual
}

/**
 * A sentence offered by a batch capture (pasted text, a photo or the starter sentences), ticked to become a card.
 *
 * @property category The island to file it on, if the source knows (the starter sentences' topic), with its [emoji].
 */
data class BatchSentence(val text: String, val checked: Boolean = true, val category: String? = null, val emoji: String? = null)

/**
 * A batch capture in progress: the sentences found, and once saving, how far along it is.
 *
 * @property saved How many cards were saved so far; null until saving starts.
 * @property fromEnglish The sentences are the starter sentences, written in English: when the user's language
 *     is another one, each is translated into it first so the card's front is in the user's language.
 * @property lastError Why sentences couldn't be saved (the first reason met), to show with the result; null if none failed.
 */
data class BatchState(
    val sentences: List<BatchSentence>,
    val saved: Int? = null,
    val failed: Int = 0,
    val fromEnglish: Boolean = false,
    val lastError: String? = null
) {
    val isSaving: Boolean get() = saved != null && saved < sentences.count { it.checked }
    val isDone: Boolean get() = saved != null && !isSaving
}

data class CreateUiState(
    val nativeSentence: String = "",
    val targetSentence: String = "", // Only used in manual mode
    val category: String = "",
    val inputMode: InputMode = InputMode.Translate,
    val isSaving: Boolean = false,
    /** Seconds since the save started, for the "thinking…" label of slow local models. */
    val elapsedSeconds: Int = 0,
    val result: SaveResult? = null,
    val isLoadingCoasts: Boolean = true,
    val coasts: List<Coast> = emptyList(),
    /** The coast new cards are added to; null until the user has started a coast. */
    val selectedCoast: Coast? = null,
    /** Existing islands on [selectedCoast], offered as quick-pick category chips. */
    val coastIslands: List<LanguageIsland> = emptyList(),
    /** Languages a new coast can be started for. */
    val availableLanguages: List<Language> = emptyList(),
    val nativeLanguage: String = SettingsDefaults.NATIVE_LANGUAGE,
    val translationProvider: TranslationProvider = TranslationProvider.OnDevice,
    val streakCount: Int = 0,
    /** Whether any coast has cards at all, i.e. whether there is anything to review. */
    val hasCards: Boolean = false,
    /** Cards due across all coasts (see [isDue]). */
    val dueCount: Int = 0,
    /** How many coasts have due cards. */
    val dueCoastCount: Int = 0,
    /** The cards most recently added to the selected coast, newest first. */
    val recentCards: List<RecentCard> = emptyList(),
    /** A card on the selected coast with the same text as [nativeSentence] (see [findDuplicate]), if there is one. */
    val duplicate: PlacedCard? = null,
    /** The island whose cards the typed sentence resembles (see [suggestIsland]), while no category is typed. */
    val suggestedIsland: String? = null,
    /** A cloud provider to retry with after the local model's answers couldn't be read; null if none is set up. */
    val fallbackProvider: TranslationProvider? = null,
    /** A batch capture being reviewed or saved. */
    val batch: BatchState? = null,
    /** Every coast with its card and due counts, for the daily session of the selected one. */
    val coastSummaries: List<CoastSummary> = emptyList(),
    /** Sentences prepared for the selected coast and not yet offered. */
    val readySuggestions: Int = 0,
    /** Whether sentences for the selected coast are being prepared right now. */
    val isPreparingSentences: Boolean = false,
    /** The day of the last study session (for the welcome-back rule) and today. */
    val lastStudy: LocalDate? = null,
    val today: LocalDate? = null,
    /** A coast just started, whose goal the screen asks for; null once answered or dismissed. */
    val goalPrompt: Coast? = null,
    /** The selected coast's sentences, normalized ([normalizedWords] joined by spaces), to mark starter sentences it has. */
    val coastSentences: Set<String> = emptySet(),
    /** An emoji for a new island named [category], when the source of the sentence knows one and the AI gives none. */
    val categoryEmoji: String? = null,
    /** What the sentence being saved is, for the model (see [TranslationRequest.context]). */
    val translationContext: String? = null,
    /** The user's blanks from Settings, by placeholder, which the starter sheet fills in. */
    val profile: Map<String, String> = emptyMap(),
    /** How much new a day's session brings. */
    val pace: Pace = Pace.Steady,
    /** The level the selected coast is on; null until known. */
    val level: RouteLevel? = null,
    /** Whether to invite the user to tell the app about themselves: they haven't yet, and haven't waved it away. */
    val showAboutYouInvite: Boolean = false,
    /** Whether the provider picked in Settings can translate right now: it has its key, and the user's permission. */
    val engineReady: Boolean = true,
    /** The picked cloud provider while it has a key but may not be sent text yet; null otherwise. */
    val awaitingPermission: TranslationProvider? = null,
    /** Why new sentences for the selected coast couldn't be prepared, when the last attempt failed. */
    val suggestionError: String? = null,
    /** Whether the user hid the "Getting started" card. Hidden until known, so it never flashes. */
    val gettingStartedDismissed: Boolean = true,
    /** Whether this phone ever made a backup. */
    val hasBackedUp: Boolean = false
) {
    /** True when the selected coast is in the user's native language, so there's nothing to translate. */
    val isSameLanguage: Boolean get() = selectedCoast?.language == nativeLanguage
    val isManualMode: Boolean get() = inputMode == InputMode.Manual

    /** The selected coast's counts, once known. */
    val focusSummary: CoastSummary? get() = coastSummaries.firstOrNull { it.coast.coastId == selectedCoast?.coastId }

    /**
     * The "Getting started" card's steps, or null when it isn't shown: hidden by the user, or every step done.
     * Where translation works from the start (on-device translation) its step is simply ticked already, so
     * the card opens at "1 of 3" and the first step shows that something is already done.
     */
    val gettingStarted: GettingStarted? get() {
        if (gettingStartedDismissed || selectedCoast == null) return null
        val steps = GettingStarted(translationReady = engineReady, studied = lastStudy != null, backedUp = hasBackedUp)
        return steps.takeUnless { it.isDone }
    }

    /** A session was completed today, so Today's job is done; another round is offered, not pushed. */
    val doneToday: Boolean get() = lastStudy != null && lastStudy == today

    /** Today's session on the selected coast, or null before the coast, its counts and the day are known. */
    val plan: DailyPlan? get() {
        val day = today ?: return null
        // Not before the counts are in: zeros would show an empty coast for a moment.
        val summary = focusSummary ?: return null
        return dailyPlan(summary.reviewCount, summary.newCount, readySuggestions, lastStudy, day, pace)
    }
}

/**
 * The first three things a new user does, for the calm checklist on Today: translation working, a first
 * session finished and a first backup. Each ticks itself off from what really happened.
 */
data class GettingStarted(val translationReady: Boolean, val studied: Boolean, val backedUp: Boolean) {
    val doneCount: Int get() = listOf(translationReady, studied, backedUp).count { it }
    val isDone: Boolean get() = doneCount == 3
}

/** Outcome of the last save, shown as a feedback card. */
sealed interface SaveResult {
    /**
     * A card was saved; [added] identifies it so the save can be undone.
     *
     * @property corrected For a sentence written in the coast's language: what the user wrote, if the AI corrected it.
     * @property note What was corrected, or how the alternatives differ.
     * @property onDevice The translation came from the on-device translator, which is credited under the card.
     */
    data class Saved(
        val added: AddedCard,
        val category: String,
        val emoji: String,
        val manual: Boolean,
        val corrected: String? = null,
        val note: String? = null,
        val onDevice: Boolean = false
    ) : SaveResult {
        val nativeText: String get() = added.card.nativeText
        val targetText: String get() = added.card.targetText
    }
    data class Error(val message: String) : SaveResult
}

@OptIn(ExperimentalCoroutinesApi::class)
class CreateViewModel(
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository,
    private val translator: Translator,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val pool: SuggestionPool
) : ViewModel() {

    // Compose state rather than StateFlow so text fields update synchronously while typing.
    var uiState by mutableStateOf(CreateUiState(today = clock.todayIn(timeZone)))
        private set

    private var dismissJob: Job? = null
    private var tickerJob: Job? = null
    private var batchJob: Job? = null
    private var isAddingCoast: Boolean = false

    /** Off-main dispatcher for indexing large coasts; overridable in tests. */
    var background: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default

    /** The cards of the selected coast, to warn about duplicates and suggest an island while typing. */
    private var coastIndex = CoastIndex(emptyList())

    /** Unreadable answers from the local model in a row; see [LOCAL_FAILURES_BEFORE_FALLBACK]. */
    private var localFailures = 0

    /** The moment due cards are counted at; moved on whenever the screen comes back (see [onResume]). */
    private val nowTick = MutableStateFlow(clock.now().toEpochMilliseconds())

    /** Whether the user picked an input mode themselves. Until then the form follows what the provider can do. */
    private var modePicked = false

    init {
        viewModelScope.launch {
            combine(
                flashcards.observeCoasts(),
                settings.activeCoastId,
                settings.nativeLanguage,
                settings.translationProvider
            ) { coasts, activeCoastId, nativeLanguage, provider ->
                 CoastSelectionSnapshot(coasts, activeCoastId, nativeLanguage, provider)
            }.collect { snapshot ->
                uiState = uiState.copy(
                    isLoadingCoasts = false,
                    coasts = snapshot.coasts,
                    // Fall back to the first coast if none was picked yet or the active one was deleted.
                    selectedCoast = snapshot.coasts.firstOrNull { it.coastId == snapshot.activeCoastId } ?: snapshot.coasts.firstOrNull(),
                    availableLanguages = availableCoastLanguages(snapshot.coasts, snapshot.nativeLanguage),
                    nativeLanguage = snapshot.nativeLanguage,
                    translationProvider = snapshot.provider
                )
            }
        }
        viewModelScope.launch {
            // Counted at the time the screen opens or comes back; reviewing changes the cards and with them the counts.
            combine(
                nowTick.flatMapLatest { now -> flashcards.observeCoastSummaries(studiedSince = 0, now = now) },
                settings.streakCount
            ) { coasts, streak -> coasts to streak }
                .collect { (coasts, streak) ->
                    uiState = uiState.copy(
                        streakCount = streak,
                        hasCards = coasts.any { it.cardCount > 0 },
                        dueCount = coasts.sumOf { it.reviewCount },
                        dueCoastCount = coasts.count { it.reviewCount > 0 },
                        coastSummaries = coasts
                    )
                }
        }
        viewModelScope.launch { settings.lastStudyDate.collect { uiState = uiState.copy(lastStudy = it) } }
        viewModelScope.launch { settings.profile.collect { uiState = uiState.copy(profile = it.fillers()) } }
        viewModelScope.launch { readySuggestions().collect { uiState = uiState.copy(readySuggestions = it) } }
        viewModelScope.launch { settings.pace.collect { uiState = uiState.copy(pace = it) } }
        viewModelScope.launch {
            combine(selectedCoastId(), nowTick) { coastId, now -> coastId to now }
                .flatMapLatest { (coastId, now) ->
                    if (coastId == null) flowOf(null)
                    else observeRoute(flashcards, settings, coastId, now).map<Route, RouteLevel?> { it.current }
                }
                .collect { uiState = uiState.copy(level = it) }
        }
        viewModelScope.launch {
            combine(settings.personalAnswers, settings.aboutYouInviteDismissed) { answers, dismissed -> answers.isEmpty() && !dismissed }
                .collect { uiState = uiState.copy(showAboutYouInvite = it) }
        }
        viewModelScope.launch {
            combine(selectedCoastId(), pool.preparing) { coastId, preparing -> coastId != null && coastId in preparing }
                .collect { uiState = uiState.copy(isPreparingSentences = it) }
        }
        viewModelScope.launch {
            combine(selectedCoastId(), pool.failures) { coastId, failures -> coastId?.let { failures[it] } }
                .collect { uiState = uiState.copy(suggestionError = it) }
        }
        viewModelScope.launch {
            // Keep a few sentences ready for the selected coast, so today's session always has something new.
            combine(selectedCoastId(), readySuggestions(), settings.pace) { coastId, ready, pace -> coastId?.takeIf { ready < pace.newPerDay } }
                .distinctUntilChanged()
                .collect { coastId -> if (coastId != null) pool.requestTopUp(coastId) }
        }
        viewModelScope.launch {
            settings.engineReady().collect { ready ->
                // With no provider that can translate, the form starts on typing both sides, until the user picks a mode.
                // A translation typed by hand keeps it there too: switching would hide it and save the AI's instead.
                val keep = modePicked || uiState.targetSentence.isNotBlank()
                val mode = if (keep) uiState.inputMode else if (ready) InputMode.Translate else InputMode.Manual
                uiState = uiState.copy(engineReady = ready, inputMode = mode)
            }
        }
        viewModelScope.launch { settings.providerAwaitingPermission().collect { uiState = uiState.copy(awaitingPermission = it) } }
        viewModelScope.launch { settings.gettingStartedDismissed.collect { uiState = uiState.copy(gettingStartedDismissed = it) } }
        viewModelScope.launch { settings.hasBackedUp.collect { uiState = uiState.copy(hasBackedUp = it) } }
        viewModelScope.launch {
            // A different provider may manage where the last one failed, and so may the same one once it has its key or permission.
            combine(
                settings.translationProvider,
                settings.engineReady(),
                // A key that replaces one the provider refused leaves "ready" as it was, so the key counts too.
                settings.translationProvider.flatMapLatest { settings.key(it) }
            ) { provider, ready, key -> Triple(provider, ready, key) }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    // Every coast's stored reason is out of date now, not only the selected one's.
                    pool.forgetFailures()
                    selectedCoastId().first()?.let { coastId -> pool.requestTopUp(coastId) }
                }
        }
        viewModelScope.launch {
            selectedCoastId()
                .flatMapLatest { coastId -> if (coastId == null) flowOf(emptyList()) else flashcards.observeRecentCards(coastId, RECENT_CARDS) }
                .collect { uiState = uiState.copy(recentCards = it) }
        }
        viewModelScope.launch {
            selectedCoastId()
                .flatMapLatest { coastId -> if (coastId == null) flowOf(emptyList()) else flashcards.observeIslands(coastId) }
                .collect { uiState = uiState.copy(coastIslands = it) }
        }
        viewModelScope.launch {
            selectedCoastId()
                .flatMapLatest { coastId -> if (coastId == null) flowOf(emptyList()) else flashcards.getPlacedCardsForCoast(coastId) }
                .collect { cards ->
                    // Large coasts are indexed off the main thread so imported decks never jank typing.
                    val index = if (cards.size >= 200) {
                        kotlinx.coroutines.withContext(background) { CoastIndex(cards) }
                    } else {
                        CoastIndex(cards)
                    }
                    coastIndex = index
                    uiState = uiState.copy(
                        duplicate = index.duplicateOf(uiState.nativeSentence),
                        suggestedIsland = suggestion(uiState.nativeSentence, uiState.category),
                        coastSentences = index.sentences
                    )
                }
        }
        viewModelScope.launch { settings.refreshStreak() }
        viewModelScope.launch {
            // A local model takes seconds to load, so load it while the user is still typing.
            settings.translationProvider.distinctUntilChanged().collect { provider ->
                if (provider == TranslationProvider.LocalAi) {
                    (settings.currentEngine(provider) as? EngineChoice.Ready)?.let { translator.prepare(it.engine) }
                }
            }
        }
    }

    private data class CoastSelectionSnapshot(
        val coasts: List<Coast>,
        val activeCoastId: Int?,
        val nativeLanguage: String,
        val provider: TranslationProvider
    )

    /** The coast new cards go to, as shown in the coast picker. */
    private fun selectedCoastId(): Flow<Int?> =
        combine(flashcards.observeCoasts(), settings.activeCoastId) { coasts, activeCoastId ->
            (coasts.firstOrNull { it.coastId == activeCoastId } ?: coasts.firstOrNull())?.coastId
        }.distinctUntilChanged()

    /** How many prepared sentences the selected coast has waiting. */
    private fun readySuggestions(): Flow<Int> =
        selectedCoastId()
            .flatMapLatest { coastId -> if (coastId == null) flowOf(emptyList()) else flashcards.observeSuggestions(coastId) }
            .map { it.size }
            .distinctUntilChanged()

    /**
     * Brings the day, the due counts and the streak up to date when the screen comes back. The app can stay
     * open in the background over midnight, and cards fall due as a day begins.
     */
    fun onResume() {
        nowTick.value = clock.now().toEpochMilliseconds()
        val today = clock.todayIn(timeZone)
        if (today != uiState.today) uiState = uiState.copy(today = today)
        viewModelScope.launch { settings.refreshStreak() }
        // A top-up that failed is tried again once it is allowed to be (the pool decides); one that isn't needed does nothing.
        val coastId = uiState.selectedCoast?.coastId
        if (coastId != null && uiState.readySuggestions < uiState.pace.newPerDay) pool.requestTopUp(coastId)
    }

    /** Has new sentences for the selected coast prepared again, after an attempt that failed. */
    fun retrySuggestions() {
        val coastId = uiState.selectedCoast?.coastId ?: return
        pool.forgetFailure(coastId)
        pool.requestTopUp(coastId)
    }

    /** Stores how much new a day's session brings. */
    fun setPace(pace: Pace) {
        viewModelScope.launch { settings.setPace(pace) }
    }

    /** Waves away the invitation to tell the app about oneself. */
    fun dismissAboutYouInvite() {
        viewModelScope.launch { settings.dismissAboutYouInvite() }
    }

    /** Hides the "Getting started" card for good. */
    fun dismissGettingStarted() {
        viewModelScope.launch { settings.dismissGettingStarted() }
    }

    /** Saves [edit] to card [cardId], from the list of recently added cards. */
    fun editCard(cardId: Int, edit: CardEdit) {
        viewModelScope.launch { flashcards.editCard(cardId, edit) }
    }

    /** Deletes [card] right away; [onDeleted] receives it so the delete can be undone. */
    fun deleteCard(card: Flashcard, onDeleted: (DeletedContent) -> Unit) {
        viewModelScope.launch { onDeleted(flashcards.deleteFlashcard(card)) }
    }

    /** Puts back a deleted card. Not tied to this ViewModel's scope, so undo works after leaving the screen. */
    suspend fun restore(content: DeletedContent) = flashcards.restore(content)

    /** Makes [coast] the one new cards are added to. */
    fun onCoastSelected(coast: Coast) {
        viewModelScope.launch { settings.setActiveCoastId(coast.coastId) }
    }

    /** Starts a coast for [language], selects it and asks why the user learns it. */
    fun addCoast(language: String) {
        if (isAddingCoast) return
        isAddingCoast = true
        viewModelScope.launch {
            try {
                val coastId = flashcards.addCoast(language)
                settings.setActiveCoastId(coastId)
                uiState = uiState.copy(goalPrompt = flashcards.observeCoast(coastId).first())
            } finally {
                isAddingCoast = false
            }
        }
    }

    /** Asks for the goal of [coast] (the selected one, from the Today card). */
    fun editGoal(coast: Coast) {
        uiState = uiState.copy(goalPrompt = coast)
    }

    /** Closes the goal question without an answer. */
    fun dismissGoalPrompt() {
        uiState = uiState.copy(goalPrompt = null)
    }

    /**
     * Stores why the user learns [coast]'s language and where they will use it (blank clears either). A
     * change throws away the sentences prepared for the old goal and has new ones made.
     */
    fun setGoal(coast: Coast, goal: String, region: String = coast.region.orEmpty()) {
        uiState = uiState.copy(goalPrompt = null)
        val trimmed = goal.trim().ifBlank { null }
        val trimmedRegion = region.trim().ifBlank { null }
        if (trimmed == coast.goal && trimmedRegion == coast.region) return
        viewModelScope.launch {
            flashcards.setCoastGoal(coast.coastId, trimmed, trimmedRegion)
            flashcards.clearPendingSuggestions(coast.coastId)
            pool.forgetFailure(coast.coastId)
            pool.requestTopUp(coast.coastId)
        }
    }

    /** Sets the language the user speaks (the first step of onboarding). */
    fun setNativeLanguage(language: String) {
        viewModelScope.launch { settings.setNativeLanguage(language) }
    }

    /** Updates the sentence typed in the native language (or, in [InputMode.WriteInTarget], the coast's). */
    fun onNativeSentenceChange(value: String) {
        uiState = uiState.copy(
            nativeSentence = value,
            duplicate = coastIndex.duplicateOf(value),
            suggestedIsland = suggestion(value, uiState.category)
        )
    }

    /** Updates the translation typed in manual mode. */
    fun onTargetSentenceChange(value: String) {
        uiState = uiState.copy(targetSentence = value)
    }

    /** Updates the category (island) typed for the card. */
    fun onCategoryChange(value: String) {
        uiState = uiState.copy(category = value, suggestedIsland = suggestion(uiState.nativeSentence, value))
    }

    /** Switches between translating automatically and typing the translation by hand. */
    fun onManualModeChange(manual: Boolean) = onInputModeChange(if (manual) InputMode.Manual else InputMode.Translate)

    /** Switches where the translation comes from. */
    fun onInputModeChange(mode: InputMode) {
        modePicked = true
        uiState = uiState.copy(inputMode = mode)
    }

    /** The island [sentence] resembles, unless a [category] is already typed (see [suggestIsland]). */
    private fun suggestion(sentence: String, category: String): String? =
        if (category.isNotBlank()) null else coastIndex.islandFor(sentence)

    /**
     * Translates the typed sentence (unless in manual mode) and saves it as a card, then shows the result.
     * The form is cleared only if the card was saved. Does nothing while a save is running.
     */
    fun save() {
        val state = uiState
        if (state.nativeSentence.isBlank() || state.isSaving) return
        dismissJob?.cancel()
        uiState = state.copy(isSaving = true, result = null, fallbackProvider = null)
        startTicker()

        viewModelScope.launch {
            val result = try {
                createCard(state, engineOverride = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError(TAG, "Couldn't save the card", e)
                SaveResult.Error(SAVE_FAILED)
            }
            finishSave(result)
        }
    }

    /** Translates the typed sentence again with [fallbackProvider][CreateUiState.fallbackProvider] instead of the local model. */
    fun retryWithFallback() {
        val state = uiState
        val provider = state.fallbackProvider ?: return
        if (state.nativeSentence.isBlank() || state.isSaving) return
        dismissJob?.cancel()
        uiState = state.copy(isSaving = true, result = null)
        startTicker()
        viewModelScope.launch {
            val result = try {
                when (val choice = settings.currentEngine(provider)) {
                    is EngineChoice.Ready -> createCard(state, engineOverride = choice.engine)
                    is EngineChoice.Missing -> SaveResult.Error(choice.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError(TAG, "Couldn't save the card with the other provider", e)
                SaveResult.Error(SAVE_FAILED)
            }
            finishSave(result)
        }
    }

    /** Shows [result], clearing the form if a card was saved, and offers a cloud retry after repeated local failures. */
    private suspend fun finishSave(result: SaveResult) {
        stopTicker()
        val fallback = if (result is SaveResult.Error && uiState.translationProvider == TranslationProvider.LocalAi && result.isUnreadableAnswer) {
            localFailures += 1
            if (localFailures >= LOCAL_FAILURES_BEFORE_FALLBACK) settings.fallbackCloudEngine()?.first else null
        } else {
            if (result is SaveResult.Saved) localFailures = 0
            null
        }
        uiState = if (result is SaveResult.Saved) {
            uiState.copy(nativeSentence = "", targetSentence = "", category = "", suggestedIsland = null, duplicate = null, isSaving = false, result = result)
        } else {
            uiState.copy(isSaving = false, result = result, fallbackProvider = fallback)
        }
        // Only auto-dismiss success confirmations; keep errors visible until the user retries or edits.
        if (result is SaveResult.Saved) scheduleResultDismissal() else dismissJob?.cancel()
    }

    /** Cancels auto-dismissal of the saved-card preview (e.g. while the user edits its translation inline). */
    fun pauseResultDismissal() {
        dismissJob?.cancel()
    }

    /** Restarts auto-dismissal of the saved-card preview once inline editing is cancelled. */
    fun resumeResultDismissal() {
        if (uiState.result is SaveResult.Saved) scheduleResultDismissal()
    }

    /** Whether a failure means the model answered, but not in the expected format. */
    private val SaveResult.Error.isUnreadableAnswer: Boolean
        get() = message.contains("couldn't be read", ignoreCase = true) || message.contains("empty answer", ignoreCase = true)

    /** Removes the card that was just saved (and its island, if that was created for it). */
    fun undoLastSave() {
        val saved = uiState.result as? SaveResult.Saved ?: return
        dismissJob?.cancel()
        uiState = uiState.copy(result = null)
        viewModelScope.launch { flashcards.undoAdd(saved.added) }
    }

    /** Replaces the translation of the card that was just saved with [targetText], as typed in the preview. */
    fun updateSavedTranslation(targetText: String) {
        val saved = uiState.result as? SaveResult.Saved ?: return
        val text = targetText.trim()
        if (text.isEmpty() || text == saved.targetText) return
        val card = saved.added.card
        // Keep the preview up, and keep the old translation among the alternatives in case it was right after all.
        val alternatives = (card.alternatives + card.targetText).filterNot { it == text }.distinct().take(2)
        // The text is the user's own now, so the on-device translator is no longer credited under it.
        val updated = saved.copy(added = saved.added.copy(card = card.copy(targetText = text, alternatives = alternatives)), onDevice = false)
        uiState = uiState.copy(result = updated)
        scheduleResultDismissal()
        viewModelScope.launch { flashcards.updateTranslation(card.cardId, text, alternatives) }
    }

    /** Hides the feedback card after [RESULT_VISIBLE_MILLIS], restarting the timer on every save. */
    private fun scheduleResultDismissal() {
        dismissJob?.cancel()
        dismissJob = viewModelScope.launch {
            delay(RESULT_VISIBLE_MILLIS)
            uiState = uiState.copy(result = null)
        }
    }

    /** Counts the seconds a save has been running. */
    private fun startTicker() {
        tickerJob?.cancel()
        uiState = uiState.copy(elapsedSeconds = 0)
        tickerJob = viewModelScope.launch {
            var seconds = 0
            while (true) {
                delay(1_000)
                seconds += 1
                uiState = uiState.copy(elapsedSeconds = seconds)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    // ---------- Batch capture ----------

    /** Starts a batch capture from pasted or scanned [text], splitting it into sentences to tick. */
    fun startBatch(text: String) {
        batchJob?.cancel()
        batchJob = null
        val sentences = splitSentences(text)
        uiState = uiState.copy(batch = BatchState(sentences.map { BatchSentence(it) }))
    }

    /** Ticks or unticks the sentence at [index] of the batch. */
    fun toggleBatchSentence(index: Int) {
        val batch = uiState.batch ?: return
        if (batch.saved != null) return
        val sentences = batch.sentences.mapIndexed { i, s -> if (i == index) s.copy(checked = !s.checked) else s }
        uiState = uiState.copy(batch = batch.copy(sentences = sentences))
    }

    /**
     * Saves the starter sentences the user picked as cards, one after the other, each on its topic's island
     * unless the AI knows better. Shown and reported like a batch capture.
     */
    fun addStarterSentences(sentences: List<StarterSentence>) {
        if (sentences.isEmpty() || uiState.batch?.isSaving == true) return
        uiState = uiState.copy(batch = BatchState(sentences.map { BatchSentence(it.text, category = it.category, emoji = it.emoji) }, fromEnglish = true))
        saveBatch()
    }

    /** [text] (English, from the starter sentences) in the user's language; null if it couldn't be translated. */
    private suspend fun englishToNative(text: String): String? {
        val native = settings.nativeLanguage.first()
        if (native == BANK_LANGUAGE) return text
        val engine = (settings.currentEngine() as? EngineChoice.Ready)?.engine ?: return null
        val result = translator.translateAndCategorize(
            TranslationRequest(engine, text, nativeLanguage = BANK_LANGUAGE, targetLanguage = native, userCategory = "", existingCategories = emptyList())
        )
        return result.translatedText.takeIf { result.isSuccess }
    }

    /** Saves a card for every ticked sentence of the batch, one after the other, reporting progress. */
    fun saveBatch() {
        val batch = uiState.batch ?: return
        if (batch.saved != null) return
        val picked = batch.sentences.filter { it.checked }
        if (picked.isEmpty()) return
        uiState = uiState.copy(batch = batch.copy(saved = 0, failed = 0, lastError = null))
        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            // Without a provider that can translate, every sentence would fail for the same reason: say it once and stop.
            if (batch.fromEnglish || uiState.inputMode != InputMode.Manual) {
                val choice = try {
                    settings.currentEngine()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null // The sentences below then fail one by one, each with its own message.
                }
                if (choice is EngineChoice.Missing) {
                    val current = uiState.batch ?: return@launch
                    uiState = uiState.copy(batch = current.copy(saved = picked.size, failed = picked.size, lastError = choice.message))
                    return@launch
                }
            }
            var saved = 0
            var failed = 0
            var firstError: String? = null
            for (sentence in picked) {
                val result = try {
                    if (batch.fromEnglish) {
                        val native = englishToNative(sentence.text)
                        if (native == null) SaveResult.Error("Couldn't translate into ${uiState.nativeLanguage}.")
                        else createCard(
                            uiState.copy(
                                nativeSentence = native,
                                inputMode = InputMode.Translate,
                                category = sentence.category.orEmpty(),
                                categoryEmoji = sentence.emoji,
                                translationContext = sentence.category?.let { "A phrasebook entry from the topic \"$it\" for a learner; ${topicRules(it)}" }
                            ),
                            engineOverride = null
                        )
                    } else {
                        createCard(uiState.copy(nativeSentence = sentence.text), engineOverride = null)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logError(TAG, "Couldn't save a sentence of the batch", e)
                    SaveResult.Error(SAVE_FAILED)
                }
                if (result is SaveResult.Saved) {
                    saved += 1
                } else {
                    failed += 1
                    if (firstError == null) firstError = (result as? SaveResult.Error)?.message
                }
                val current = uiState.batch ?: return@launch
                uiState = uiState.copy(batch = current.copy(saved = saved + failed, failed = failed, lastError = firstError))
            }
        }
    }

    /** Closes the batch capture and cancels any in-flight batch save coroutine. */
    fun dismissBatch() {
        batchJob?.cancel()
        batchJob = null
        uiState = uiState.copy(batch = null)
    }

    /**
     * Saves a card for [state], translating it first unless in manual mode. Missing input, a missing API key
     * or a failed translation are returned as [SaveResult.Error] rather than thrown.
     * [engineOverride] translates with that engine instead of the provider picked in Settings.
     */
    private suspend fun createCard(state: CreateUiState, engineOverride: TranslationEngine?): SaveResult {
        val typed = state.nativeSentence.trim()
        val coast = state.selectedCoast ?: return SaveResult.Error("Start a coast first.")
        if (state.isSameLanguage) return SaveResult.Error(sameLanguageMessage(coast.language))

        if (state.inputMode == InputMode.Manual) {
            if (state.targetSentence.isBlank()) return SaveResult.Error("Type the ${coast.language} translation as well. In Manual you write both sides.")
            val category = state.category.trim().ifBlank { DEFAULT_MANUAL_CATEGORY }
            val added = flashcards.addFlashcard(coast.coastId, typed, state.targetSentence.trim(), category)
            return saved(added, coast.coastId, category, manual = true)
        }

        val engine = engineOverride ?: when (val choice = settings.currentEngine()) {
            is EngineChoice.Ready -> choice.engine
            is EngineChoice.Missing -> return SaveResult.Error(choice.message)
        }
        val writtenInTarget = state.inputMode == InputMode.WriteInTarget
        if (writtenInTarget && engine == TranslationEngine.OnDevice) {
            return SaveResult.Error("On-device translation can't correct what you write. Pick an AI provider in Settings, or let the AI translate.")
        }

        val translation = translator.translateAndCategorize(
            TranslationRequest(
                engine = engine,
                nativeSentence = typed,
                nativeLanguage = settings.nativeLanguage.first(),
                targetLanguage = coast.language,
                userCategory = state.category.trim(),
                existingCategories = flashcards.observeIslands(coast.coastId).first().map { it.name },
                writtenInTarget = writtenInTarget,
                context = state.translationContext,
                targetRegion = coast.region
            )
        )
        if (!translation.isSuccess) return SaveResult.Error(translation.errorMessage ?: "The translation didn't work. Try again.")

        // Written in the coast's language, the typed text is the card's translation (corrected if need be)
        // and the model's answer is the native side.
        val added = flashcards.addFlashcard(
            coastId = coast.coastId,
            nativeText = if (writtenInTarget) translation.translatedText else typed,
            targetText = if (writtenInTarget) translation.corrected ?: typed else translation.translatedText,
            category = translation.finalCategory,
            emoji = translation.emoji ?: state.categoryEmoji?.takeIf { translation.finalCategory.equals(state.category.trim(), ignoreCase = true) },
            alternatives = if (writtenInTarget) emptyList() else translation.alternatives,
            note = if (writtenInTarget) null else translation.note
        )
        return saved(
            added = added,
            coastId = coast.coastId,
            category = translation.finalCategory,
            manual = false,
            corrected = if (writtenInTarget && translation.corrected != null) typed else null,
            note = if (writtenInTarget) translation.note else null,
            onDevice = engine == TranslationEngine.OnDevice
        )
    }

    /** The feedback for a saved card, with the emoji of the island it went into. */
    private suspend fun saved(
        added: AddedCard,
        coastId: Int,
        category: String,
        manual: Boolean,
        corrected: String? = null,
        note: String? = null,
        onDevice: Boolean = false
    ): SaveResult.Saved {
        // An existing island keeps its own emoji; look it up so the feedback card matches the island.
        val island = added.createdIsland
            ?: flashcards.observeIslands(coastId).first().first { it.islandId == added.card.islandId }
        return SaveResult.Saved(added, category, islandEmoji(island), manual, corrected, note, onDevice)
    }
}

/**
 * The first of [cards] whose native text is the same sentence as [text] (see [isSameSentence]),
 * or null if there is none or [text] has no words yet.
 */
fun findDuplicate(text: String, cards: List<PlacedCard>): PlacedCard? {
    if (normalizedWords(text).isEmpty()) return null
    return cards.firstOrNull { isSameSentence(it.card.nativeText, text) }
}

/**
 * The cards of one coast, read once, so that a keystroke in the form costs a lookup and not a pass over
 * every card. [duplicateOf] answers what [findDuplicate] would, and [islandFor] what [suggestIsland] would.
 */
private class CoastIndex(cards: List<PlacedCard>) {
    /** The first card for each sentence, by its words ([normalizedWords] joined by spaces). */
    private val bySentence = HashMap<String, PlacedCard>()

    /** The islands in the order their first card comes, which settles a tie the way [suggestIsland] does. */
    private val islands = LinkedHashSet<String>()

    /** For every word that counts (see [ISLAND_WORD_LENGTH]): how often each island's cards use it. */
    private val wordUse = HashMap<String, HashMap<String, Int>>()

    init {
        for (placed in cards) {
            val words = normalizedWords(placed.card.nativeText)
            val sentence = words.joinToString(" ")
            if (sentence !in bySentence) bySentence[sentence] = placed
            islands += placed.islandName
            for (word in words) {
                if (word.length < ISLAND_WORD_LENGTH) continue
                val use = wordUse.getOrPut(word) { HashMap() }
                use[placed.islandName] = (use[placed.islandName] ?: 0) + 1
            }
        }
    }

    /** The coast's sentences, each as its words joined by spaces. */
    val sentences: Set<String> get() = bySentence.keys

    /** The first card whose native text is the same sentence as [text], or null if there is none or [text] has no words. */
    fun duplicateOf(text: String): PlacedCard? {
        val words = normalizedWords(text)
        return if (words.isEmpty()) null else bySentence[words.joinToString(" ")]
    }

    /** The island whose cards share the most words with [text]; null if nothing overlaps or [text] is a single short word. */
    fun islandFor(text: String): String? {
        val all = normalizedWords(text)
        val words = all.filterTo(HashSet()) { it.length >= ISLAND_WORD_LENGTH }
        if (words.isEmpty() || (words.size < 2 && all.size < 2)) return null
        val scores = HashMap<String, Int>()
        for (word in words) {
            wordUse[word]?.forEach { (island, count) -> scores[island] = (scores[island] ?: 0) + count }
        }
        return islands.filter { it in scores }.maxByOrNull { scores.getValue(it) }
    }
}

/** Explains why cards can't be added when a coast's language is the user's native language. */
fun sameLanguageMessage(language: String) =
    "$language is your native language, so there's nothing to translate. Pick another coast or change your native language in Settings."
