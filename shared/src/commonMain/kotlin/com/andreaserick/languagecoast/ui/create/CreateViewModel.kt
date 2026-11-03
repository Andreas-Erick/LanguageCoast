package com.andreaserick.languagecoast.ui.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.AddedCard
import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.data.CoastSummary
import com.andreaserick.languagecoast.data.DAILY_NEW_SENTENCES
import com.andreaserick.languagecoast.data.DEFAULT_CATEGORY
import com.andreaserick.languagecoast.data.DailyPlan
import com.andreaserick.languagecoast.data.EngineChoice
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.Language
import com.andreaserick.languagecoast.data.LanguageIsland
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
import com.andreaserick.languagecoast.data.fallbackCloudEngine
import com.andreaserick.languagecoast.data.isSameSentence
import com.andreaserick.languagecoast.data.islandEmoji
import com.andreaserick.languagecoast.data.normalizedWords
import com.andreaserick.languagecoast.data.splitSentences
import com.andreaserick.languagecoast.data.suggestIsland
import com.andreaserick.languagecoast.data.topicRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
 */
data class BatchState(
    val sentences: List<BatchSentence>,
    val saved: Int? = null,
    val failed: Int = 0,
    val fromEnglish: Boolean = false
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
    val profile: Map<String, String> = emptyMap()
) {
    /** True when the selected coast is in the user's native language, so there's nothing to translate. */
    val isSameLanguage: Boolean get() = selectedCoast?.language == nativeLanguage
    val isManualMode: Boolean get() = inputMode == InputMode.Manual

    /** The selected coast's counts, once known. */
    val focusSummary: CoastSummary? get() = coastSummaries.firstOrNull { it.coast.coastId == selectedCoast?.coastId }

    /** A session was completed today, so Today's job is done; another round is offered, not pushed. */
    val doneToday: Boolean get() = lastStudy != null && lastStudy == today

    /** Today's session on the selected coast, or null before the coast and the day are known. */
    val plan: DailyPlan? get() {
        val coast = selectedCoast ?: return null
        val day = today ?: return null
        return dailyPlan(focusSummary?.dueCount ?: 0, readySuggestions, lastStudy, day)
    }
}

/** Outcome of the last save, shown as a feedback card. */
sealed interface SaveResult {
    /**
     * A card was saved; [added] identifies it so the save can be undone.
     *
     * @property corrected For a sentence written in the coast's language: what the user wrote, if the AI corrected it.
     * @property note What was corrected, or how the alternatives differ.
     */
    data class Saved(
        val added: AddedCard,
        val category: String,
        val emoji: String,
        val manual: Boolean,
        val corrected: String? = null,
        val note: String? = null
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

    /** The cards of the selected coast, to warn about duplicates and suggest an island while typing. */
    private var coastCards: List<PlacedCard> = emptyList()

    /** Unreadable answers from the local model in a row; see [LOCAL_FAILURES_BEFORE_FALLBACK]. */
    private var localFailures = 0

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
            // Counted at the time the screen opens; reviewing changes the cards and with them the counts.
            combine(
                flashcards.observeCoastSummaries(studiedSince = 0, now = clock.now().toEpochMilliseconds()),
                settings.streakCount
            ) { coasts, streak -> coasts to streak }
                .collect { (coasts, streak) ->
                    uiState = uiState.copy(
                        streakCount = streak,
                        hasCards = coasts.any { it.cardCount > 0 },
                        dueCount = coasts.sumOf { it.dueCount },
                        dueCoastCount = coasts.count { it.dueCount > 0 },
                        coastSummaries = coasts
                    )
                }
        }
        viewModelScope.launch { settings.lastStudyDate.collect { uiState = uiState.copy(lastStudy = it) } }
        viewModelScope.launch { settings.profile.collect { uiState = uiState.copy(profile = it.fillers()) } }
        viewModelScope.launch { readySuggestions().collect { uiState = uiState.copy(readySuggestions = it) } }
        viewModelScope.launch {
            combine(selectedCoastId(), pool.preparing) { coastId, preparing -> coastId != null && coastId in preparing }
                .collect { uiState = uiState.copy(isPreparingSentences = it) }
        }
        viewModelScope.launch {
            // Keep a few sentences ready for the selected coast, so today's session always has something new.
            combine(selectedCoastId(), readySuggestions()) { coastId, ready -> coastId?.takeIf { ready < DAILY_NEW_SENTENCES } }
                .distinctUntilChanged()
                .collect { coastId -> if (coastId != null) pool.requestTopUp(coastId) }
        }
        viewModelScope.launch {
            // A different provider may manage where the last one failed.
            settings.translationProvider.distinctUntilChanged().drop(1).collect {
                selectedCoastId().first()?.let { coastId ->
                    pool.forgetFailure(coastId)
                    pool.requestTopUp(coastId)
                }
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
            combine(selectedCoastId(), flashcards.observeAllCards()) { coastId, cards -> cards.filter { it.coastId == coastId } }
                .collect { cards ->
                    coastCards = cards
                    uiState = uiState.copy(
                        duplicate = findDuplicate(uiState.nativeSentence, cards),
                        suggestedIsland = suggestion(uiState.nativeSentence, uiState.category),
                        coastSentences = cards.mapTo(HashSet()) { normalizedWords(it.card.nativeText).joinToString(" ") }
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

    /** Makes [coast] the one new cards are added to. */
    fun onCoastSelected(coast: Coast) {
        viewModelScope.launch { settings.setActiveCoastId(coast.coastId) }
    }

    /** Starts a coast for [language], selects it and asks why the user learns it. */
    fun addCoast(language: String) {
        viewModelScope.launch {
            val coastId = flashcards.addCoast(language)
            settings.setActiveCoastId(coastId)
            uiState = uiState.copy(goalPrompt = flashcards.observeCoast(coastId).first())
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
            duplicate = findDuplicate(value, coastCards),
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
        uiState = uiState.copy(inputMode = mode)
    }

    /** The island [sentence] resembles, unless a [category] is already typed (see [suggestIsland]). */
    private fun suggestion(sentence: String, category: String): String? =
        if (category.isNotBlank()) null else suggestIsland(sentence, coastCards)

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
                SaveResult.Error(e.message ?: "Something went wrong.")
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
                SaveResult.Error(e.message ?: "Something went wrong.")
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
        scheduleResultDismissal()
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
        val updated = saved.copy(added = saved.added.copy(card = card.copy(targetText = text, alternatives = alternatives)))
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
        uiState = uiState.copy(batch = batch.copy(saved = 0, failed = 0))
        viewModelScope.launch {
            var saved = 0
            var failed = 0
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
                    SaveResult.Error(e.message ?: "Something went wrong.")
                }
                if (result is SaveResult.Saved) saved += 1 else failed += 1
                val current = uiState.batch ?: return@launch
                uiState = uiState.copy(batch = current.copy(saved = saved + failed, failed = failed))
            }
        }
    }

    /** Closes the batch capture. */
    fun dismissBatch() {
        uiState = uiState.copy(batch = null)
    }

    /**
     * Saves a card for [state], translating it first unless in manual mode. Missing input, a missing API key
     * or a failed translation are returned as [SaveResult.Error] rather than thrown.
     * [engineOverride] translates with that engine instead of the provider picked in Settings.
     */
    private suspend fun createCard(state: CreateUiState, engineOverride: TranslationEngine?): SaveResult {
        val typed = state.nativeSentence.trim()
        val coast = state.selectedCoast ?: return SaveResult.Error("Start a coast first!")
        if (state.isSameLanguage) return SaveResult.Error(sameLanguageMessage(coast.language))

        if (state.inputMode == InputMode.Manual) {
            if (state.targetSentence.isBlank()) return SaveResult.Error("Translation cannot be empty in manual mode.")
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
        if (!translation.isSuccess) return SaveResult.Error(translation.errorMessage ?: "AI Translation Failed.")

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
            note = if (writtenInTarget) translation.note else null
        )
    }

    /** The feedback for a saved card, with the emoji of the island it went into. */
    private suspend fun saved(
        added: AddedCard,
        coastId: Int,
        category: String,
        manual: Boolean,
        corrected: String? = null,
        note: String? = null
    ): SaveResult.Saved {
        // An existing island keeps its own emoji; look it up so the feedback card matches the island.
        val island = added.createdIsland
            ?: flashcards.observeIslands(coastId).first().first { it.islandId == added.card.islandId }
        return SaveResult.Saved(added, category, islandEmoji(island), manual, corrected, note)
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

/** Explains why cards can't be added when a coast's language is the user's native language. */
fun sameLanguageMessage(language: String) =
    "$language is your native language, so there's nothing to translate. Pick another coast or change your native language in Settings."
