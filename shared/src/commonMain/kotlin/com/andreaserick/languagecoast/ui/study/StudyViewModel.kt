package com.andreaserick.languagecoast.ui.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.CardEdit
import com.andreaserick.languagecoast.data.Cloze
import com.andreaserick.languagecoast.data.DailyPlan
import com.andreaserick.languagecoast.data.DeletedContent
import com.andreaserick.languagecoast.data.EngineChoice
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.Grade
import com.andreaserick.languagecoast.data.LanguageIsland
import com.andreaserick.languagecoast.data.PlacedCard
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.SuggestedSentence
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.Translator
import com.andreaserick.languagecoast.data.buildExplainPrompt
import com.andreaserick.languagecoast.data.clozeOf
import com.andreaserick.languagecoast.data.currentEngine
import com.andreaserick.languagecoast.data.dailyPlan
import com.andreaserick.languagecoast.data.interleaveByCoast
import com.andreaserick.languagecoast.data.isDue
import com.andreaserick.languagecoast.data.isLeaky
import com.andreaserick.languagecoast.data.reviewOutcomes
import com.andreaserick.languagecoast.navigation.StudyScreenRoute
import com.andreaserick.languagecoast.speech.Speaker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant

/** What a study session covers; see [StudyScreenRoute.scope]. */
enum class StudyScope(val key: String) {
    Island(StudyScreenRoute.SCOPE_ISLAND),
    Coast(StudyScreenRoute.SCOPE_COAST),
    /** Today's due cards on every coast, with the coasts taking turns. */
    Today(StudyScreenRoute.SCOPE_TODAY),
    /** The cards the user keeps forgetting (see [isLeaky]), due or not. */
    Leaky(StudyScreenRoute.SCOPE_LEAKY),
    /** One coast's daily session: a few due cards, then a few new sentences to keep or skip (see [dailyPlan]). */
    Daily(StudyScreenRoute.SCOPE_DAILY);

    companion object {
        fun fromKey(key: String?): StudyScope = entries.firstOrNull { it.key == key } ?: Island
    }
}

/** How a card is answered: flipped, typed in full, or typed into a gap in the translation. */
enum class StudyMode { Flip, Type, Cloze }

/** An explanation of a word on the current card, asked from the AI provider. */
data class WordExplanation(
    val word: String,
    val sentence: String,
    val providerLabel: String,
    val text: String? = null,
    val error: String? = null
) {
    val isLoading: Boolean get() = text == null && error == null
}

data class StudyUiState(
    /** The island's name, or the session's title for the other scopes ("Today's review"). */
    val islandName: String = "",
    val scope: StudyScope = StudyScope.Island,
    val isLoading: Boolean = true,
    /** Whether the session's scope has any cards in the database at all. */
    val hasCards: Boolean = false,
    /** Cards still left in this session, in study order. */
    val sessionCards: List<PlacedCard> = emptyList(),
    /** Number of cards the session started with. */
    val totalCards: Int = 0,
    val currentIndex: Int = 0,
    val mode: StudyMode = StudyMode.Flip,
    val isSessionComplete: Boolean = false,
    /** The scope has cards, but none were due (or leaky) when the session started. */
    val isCaughtUp: Boolean = false,
    /** Days until the scope's next card is due, or null if none is scheduled. */
    val nextDueInDays: Int? = null,
    /** Days until the current card would be due again after each grade. */
    val gradeIntervals: Map<Grade, Int> = emptyMap(),
    /** How many times "Again" was pressed in this session. */
    val againCount: Int = 0,
    val streakCount: Int = 0,
    val nativeLanguage: String = SettingsDefaults.NATIVE_LANGUAGE,
    /** The language of the scope's coast, for sessions on one coast; blank across coasts. */
    val scopeLanguage: String = "",
    /** Languages the phone has a voice for. */
    val spokenLanguages: Set<String> = emptySet(),
    /** Every island, so a card can be moved to another island on its coast when editing it. */
    val allIslands: List<LanguageIsland> = emptyList(),
    /** Cards are shown in the coast's language and answered in [nativeLanguage], training recognition instead of recall. */
    val isReversed: Boolean = false,
    /** An explanation being shown (or fetched) for a word on the current card. */
    val explanation: WordExplanation? = null,
    /** In a daily session: the new sentence being offered, once the due cards are done; null while reviewing. */
    val learning: SuggestedSentence? = null,
    /** New sentences the daily session started with, and how many are still to be offered. */
    val learnTotal: Int = 0,
    val learnLeft: Int = 0,
    /** New sentences the user kept in this session (they became cards). */
    val keptCount: Int = 0,
    /** The daily session was shrunk because the user was away for days. */
    val isWelcomeBack: Boolean = false
) {
    val currentPlaced: PlacedCard? get() = sessionCards.getOrNull(currentIndex)
    val currentCard: Flashcard? get() = currentPlaced?.card
    val canGoBack: Boolean get() = currentIndex > 0
    val canGoForward: Boolean get() = currentIndex < sessionCards.size - 1
    val isTypingMode: Boolean get() = mode == StudyMode.Type

    /** The language of the current card's coast (or of the scope's coast before the first card). */
    val targetLanguage: String get() = currentPlaced?.language ?: scopeLanguage

    /** Whether the phone has a voice to read [targetLanguage] aloud. */
    val canSpeak: Boolean get() = targetLanguage in spokenLanguages

    /** The islands on the current card's coast, which the card can be moved to when editing it. */
    val coastIslands: List<LanguageIsland> get() = currentPlaced?.let { placed -> allIslands.filter { it.coastId == placed.coastId } }.orEmpty()

    /** The current card's translation with its key word blanked, in [StudyMode.Cloze]; null when it is a single word. */
    val cloze: Cloze? get() = if (mode == StudyMode.Cloze) currentCard?.let { clozeOf(it.targetText) } else null

    /** Share of the session already done: cards passed (graded anything but "Again") and new sentences decided, from 0 to 1. */
    val progress: Float get() {
        val steps = totalCards + learnTotal
        return if (steps == 0) 0f else ((totalCards - sessionCards.size) + (learnTotal - learnLeft)).toFloat() / steps
    }
}

/**
 * Runs a study session over the cards in its [StudyScope]: an island's or a coast's due cards, today's
 * due cards across every coast, the leaky words, or a coast's daily session. Every grade reschedules the
 * card with [reviewOutcomes]; "Again" also moves it to the end of the session, any other grade removes it.
 * A daily session reviews a capped number of due cards, then offers its new sentences one by one; the kept
 * ones become cards and are practiced once before the session is complete.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudyViewModel(
    savedStateHandle: SavedStateHandle,
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val speaker: Speaker,
    private val translator: Translator
) : ViewModel() {

    // Populated by Navigation from the StudyScreenRoute properties.
    private val scope: StudyScope = StudyScope.fromKey(savedStateHandle["scope"])
    private val islandId: Int = savedStateHandle["islandId"] ?: 0
    private val coastId: Int = savedStateHandle["coastId"] ?: 0
    private val title: String = savedStateHandle["islandName"] ?: ""

    private data class Session(
        /** Every card in the scope, whether in the session or not. */
        val allCards: List<PlacedCard> = emptyList(),
        val cards: List<PlacedCard> = emptyList(),
        val total: Int = 0,
        val index: Int = 0,
        val againCount: Int = 0,
        val isStarted: Boolean = false,
        val isLoading: Boolean = true,
        val mode: StudyMode = StudyMode.Flip,
        /** The islands the session's cards came from, marked studied when it is completed. */
        val islandIds: Set<Int> = emptySet(),
        /** New sentences still to offer (daily sessions). */
        val learnQueue: List<SuggestedSentence> = emptyList(),
        val learnTotal: Int = 0,
        /** Sentences kept so far, practiced once the last one is decided. */
        val kept: List<PlacedCard> = emptyList(),
        val keptCount: Int = 0,
        /** Kept sentences still being saved as cards; the practice round waits for them. */
        val pendingKeeps: Int = 0,
        val plan: DailyPlan? = null
    ) {
        /** Nothing left to review, offer or practice (and there was something to begin with). */
        val isComplete: Boolean
            get() = isStarted && cards.isEmpty() && learnQueue.isEmpty() && kept.isEmpty() && pendingKeeps == 0 && (total > 0 || learnTotal > 0)

        /** The scope has cards, but the session had nothing to do from the start. */
        val isCaughtUp: Boolean
            get() = isStarted && allCards.isNotEmpty() && total == 0 && learnTotal == 0

        /** The new sentence to offer: once the due cards are done, the next in the queue. */
        val learning: SuggestedSentence? get() = if (cards.isEmpty()) learnQueue.firstOrNull() else null
    }

    /** The coast's language for a daily session, which may have no cards yet to read it from. */
    private var dailyLanguage: String = ""

    private val session = MutableStateFlow(Session())
    private val explanation = MutableStateFlow<WordExplanation?>(null)
    private var explainJob: Job? = null

    /** The languages of the session's coasts that have a voice. */
    private val spokenLanguages: Flow<Set<String>> = session
        .map { s -> (s.allCards.map { it.language } + listOfNotNull(dailyLanguage.takeIf { it.isNotEmpty() })).distinct() }
        .distinctUntilChanged()
        .flatMapLatest { languages ->
            if (languages.isEmpty()) flowOf(emptySet())
            else combine(languages.map { language -> speaker.canSpeak(language).map { language to it } }) { pairs ->
                pairs.filter { it.second }.mapTo(HashSet()) { it.first }
            }
        }

    val uiState: StateFlow<StudyUiState> = combine(
        combine(session, explanation) { s, e -> s to e },
        settings.nativeLanguage,
        combine(spokenLanguages, flashcards.observeAllIslands()) { spoken, islands -> spoken to islands },
        settings.streakCount,
        settings.studyReversed
    ) { (s, explanation), nativeLanguage, (spoken, islands), streak, reversed ->
        val now = clock.now()
        StudyUiState(
            islandName = title,
            scope = scope,
            isLoading = s.isLoading,
            hasCards = s.allCards.isNotEmpty() || s.learnTotal > 0 || s.keptCount > 0,
            sessionCards = s.cards,
            totalCards = s.total,
            currentIndex = s.index,
            mode = s.mode,
            isSessionComplete = s.isComplete,
            isCaughtUp = s.isCaughtUp,
            nextDueInDays = daysUntilNextDue(s.allCards.map { it.card }, now),
            gradeIntervals = s.cards.getOrNull(s.index)
                ?.let { placed -> reviewOutcomes(placed.card, now, timeZone).mapValues { it.value.intervalDays } }
                .orEmpty(),
            againCount = s.againCount,
            streakCount = streak,
            nativeLanguage = nativeLanguage,
            scopeLanguage = when (scope) {
                StudyScope.Island, StudyScope.Coast -> s.allCards.firstOrNull()?.language.orEmpty()
                StudyScope.Daily -> dailyLanguage
                else -> ""
            },
            spokenLanguages = spoken,
            allIslands = islands,
            isReversed = reversed,
            explanation = explanation,
            learning = s.learning,
            learnTotal = s.learnTotal,
            learnLeft = s.learnQueue.size,
            keptCount = s.keptCount,
            isWelcomeBack = s.plan?.isWelcomeBack == true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyUiState(islandName = title, scope = scope))

    /** What a daily session is sized by, read once before it starts. */
    private var dailySuggestions: List<SuggestedSentence> = emptyList()
    private var lastStudy: LocalDate? = null

    init {
        viewModelScope.launch {
            if (scope == StudyScope.Daily) {
                dailyLanguage = flashcards.observeCoast(coastId).first()?.language.orEmpty()
                dailySuggestions = flashcards.observeSuggestions(coastId).first()
                lastStudy = settings.lastStudyDate.first()
            }
            flashcards.observeAllCards().map { cards -> cards.filter(::inScope) }.collect(::onCardsChanged)
        }
    }

    private fun inScope(placed: PlacedCard): Boolean = when (scope) {
        StudyScope.Island -> placed.card.islandId == islandId
        StudyScope.Coast, StudyScope.Daily -> placed.coastId == coastId
        StudyScope.Today, StudyScope.Leaky -> true
    }

    /**
     * Starts the session once the cards first load; later updates only refresh or drop cards already in it.
     * A daily session also starts on a coast without cards, since it may still have new sentences to offer.
     */
    private fun onCardsChanged(cards: List<PlacedCard>) = updateSession { s ->
        if (!s.isStarted && (cards.isNotEmpty() || scope == StudyScope.Daily)) {
            val due = openingCards(cards)
            val plan = if (scope == StudyScope.Daily) dailyPlan(due.size, dailySuggestions.size, lastStudy, clock.todayIn(timeZone)) else null
            val opening = if (plan != null) due.take(plan.reviewCount) else due
            val learnQueue = if (plan != null) dailySuggestions.take(plan.newCount) else emptyList()
            s.copy(
                allCards = cards,
                cards = opening,
                total = opening.size,
                index = 0,
                isStarted = true,
                isLoading = false,
                islandIds = opening.mapTo(HashSet()) { it.card.islandId },
                learnQueue = learnQueue,
                learnTotal = learnQueue.size,
                plan = plan
            )
        } else {
            // Keep the session order, but use the saved cards and drop those deleted from the database.
            val byId = cards.associateBy { it.card.cardId }
            val kept = s.cards.mapNotNull { byId[it.card.cardId] }
            s.copy(allCards = cards, cards = kept, total = s.total - (s.cards.size - kept.size), isLoading = false)
        }
    }

    /** The cards a session over [cards] opens with: the due ones, or for [StudyScope.Leaky] the leaky ones. */
    private fun openingCards(cards: List<PlacedCard>): List<PlacedCard> = when (scope) {
        StudyScope.Leaky -> leakyInStudyOrder(cards)
        StudyScope.Today -> interleaveByCoast(duePlacedInStudyOrder(cards, clock.now().toEpochMilliseconds()))
        else -> duePlacedInStudyOrder(cards, clock.now().toEpochMilliseconds())
    }

    /** Reads the current card's translation aloud. */
    fun speakAnswer() {
        uiState.value.currentCard?.let { speak(it.targetText) }
    }

    /** Reads [text] (e.g. one of the current card's alternatives) aloud in the coast's language. */
    fun speak(text: String) {
        val state = uiState.value
        if (state.canSpeak) speaker.speak(text, state.targetLanguage)
    }

    /**
     * Makes [alternative] the current card's translation. The previous translation takes its place
     * among the alternatives, so the user can switch back.
     */
    fun makeMainTranslation(alternative: String) {
        val card = uiState.value.currentCard ?: return
        if (alternative !in card.alternatives) return
        val alternatives = card.alternatives.map { if (it == alternative) card.targetText else it }
        // observeAllCards re-emits the updated card, which replaces it in the session.
        viewModelScope.launch { flashcards.updateTranslation(card.cardId, alternative, alternatives) }
    }

    /**
     * Saves [edit] to card [cardId]. A card moved to another island leaves an island session;
     * otherwise observeAllCards re-emits it with the changes in place.
     */
    fun editCard(cardId: Int, edit: CardEdit) {
        viewModelScope.launch { flashcards.editCard(cardId, edit) }
    }

    /** Grades the current card, saves its new schedule and moves on. */
    fun onGrade(grade: Grade) {
        val placed = session.value.let { it.cards.getOrNull(it.index) } ?: return
        // Don't keep reading the old answer over the next card.
        speaker.stop()
        dismissExplanation()
        val reviewed = reviewOutcomes(placed.card, clock.now(), timeZone).getValue(grade).card
        updateSession { s ->
            val rest = s.cards.filterNot { it.card.cardId == placed.card.cardId }
            if (grade == Grade.Again) s.copy(cards = rest + placed.copy(card = reviewed), againCount = s.againCount + 1) else s.copy(cards = rest)
        }
        viewModelScope.launch { flashcards.saveReview(reviewed) }
    }

    /**
     * Keeps the new sentence being offered: it becomes a card on the session's coast (and leaves the pool),
     * to be practiced once the last sentence is decided.
     */
    fun keepSuggestion() {
        val suggestion = session.value.learning ?: return
        speaker.stop()
        // Take it off the queue right away, so a second tap can't keep it twice.
        updateSession { s -> s.copy(learnQueue = s.learnQueue.drop(1), keptCount = s.keptCount + 1, pendingKeeps = s.pendingKeeps + 1) }
        viewModelScope.launch {
            val added = flashcards.addFlashcard(coastId, suggestion.nativeText, suggestion.targetText, suggestion.category, suggestion.emoji)
            flashcards.removeSuggestion(suggestion.suggestionId)
            val island = added.createdIsland ?: flashcards.observeIslands(coastId).first().first { it.islandId == added.card.islandId }
            val placed = PlacedCard(added.card, island.name, island.emoji, coastId, dailyLanguage)
            updateSession { s -> s.copy(kept = s.kept + placed, islandIds = s.islandIds + island.islandId, pendingKeeps = s.pendingKeeps - 1) }
        }
    }

    /** Passes on the new sentence being offered; it isn't suggested again. */
    fun skipSuggestion() {
        val suggestion = session.value.learning ?: return
        speaker.stop()
        updateSession { s -> s.copy(learnQueue = s.learnQueue.drop(1)) }
        viewModelScope.launch { flashcards.skipSuggestion(suggestion.suggestionId) }
    }

    /** Moves to the next card without grading the current one. */
    fun next() = updateSession { it.copy(index = it.index + 1) }

    /** Moves back to the previous card. */
    fun previous() = updateSession { it.copy(index = it.index - 1) }

    /**
     * Starts a session with every card of the scope, due or not (for [StudyScope.Leaky], every leaky card).
     * Grades still reschedule the cards.
     */
    fun practiceAll() = updateSession { s ->
        val all = if (scope == StudyScope.Leaky) leakyInStudyOrder(s.allCards).ifEmpty { s.allCards } else interleaveByCoast(s.allCards)
        s.copy(cards = all, total = all.size, index = 0, againCount = 0, islandIds = all.mapTo(HashSet()) { it.card.islandId })
    }

    /**
     * Switches between studying from the native language (recall) and from the coast's language (recognition).
     * Both directions share each card's schedule. Remembered for later sessions.
     */
    fun setReversed(reversed: Boolean) {
        viewModelScope.launch { settings.setStudyReversed(reversed) }
    }

    /** Switches between revealing the answer and typing it. */
    fun setTypingMode(enabled: Boolean) = setMode(if (enabled) StudyMode.Type else StudyMode.Flip)

    /** Switches how cards are answered. */
    fun setMode(mode: StudyMode) = updateSession { it.copy(mode = mode) }

    /**
     * Asks the AI provider picked in Settings to explain [word] as used on the current card. The answer
     * (or why there is none) arrives in [StudyUiState.explanation].
     */
    fun explainWord(word: String) {
        val state = uiState.value
        val card = state.currentCard ?: return
        explainJob?.cancel()
        explainJob = viewModelScope.launch {
            val provider = settings.translationProvider.first()
            explanation.value = WordExplanation(word, card.targetText, provider.label)
            val answer = when (val choice = settings.currentEngine(provider)) {
                is EngineChoice.Missing -> null to choice.message
                is EngineChoice.Ready -> {
                    val completion = translator.complete(
                        choice.engine,
                        buildExplainPrompt(word, card.targetText, state.targetLanguage, state.nativeLanguage)
                    )
                    completion.text to completion.errorMessage
                }
            }
            explanation.value = explanation.value?.takeIf { it.word == word }?.copy(text = answer.first, error = answer.second)
        }
    }

    /** Closes the word explanation. */
    fun dismissExplanation() {
        explainJob?.cancel()
        explanation.value = null
    }

    /** Deletes the current card; [onDeleted] receives it so the delete can be undone. */
    fun deleteCurrentCard(onDeleted: (DeletedContent) -> Unit) {
        val card = uiState.value.currentCard ?: return
        // observeAllCards re-emits without the card, which removes it from the session.
        viewModelScope.launch { onDeleted(flashcards.deleteFlashcard(card)) }
    }

    /** Puts back a deleted card. Not tied to this ViewModel's scope, so undo works after leaving the screen. */
    suspend fun restore(content: DeletedContent) = flashcards.restore(content)

    /**
     * Applies [transform] to the session, keeping the index in range.
     * When the last card of a session is graded, records the study session and marks its islands studied.
     */
    private fun updateSession(transform: (Session) -> Session) {
        val before = session.value
        val after = transform(before)
            .let { s ->
                // Once every new sentence is decided (and saved), the kept ones are practiced.
                if (s.cards.isEmpty() && s.learnQueue.isEmpty() && s.pendingKeeps == 0 && s.kept.isNotEmpty()) {
                    s.copy(cards = s.kept, total = s.total + s.kept.size, kept = emptyList(), index = 0)
                } else s
            }
            .let { it.copy(index = it.index.coerceIn(0, maxOf(0, it.cards.lastIndex))) }
        session.value = after

        // Skipping every offered sentence ends the session too, but isn't a day studied.
        val justCompleted = !before.isComplete && after.isComplete && (after.total > 0 || after.keptCount > 0)
        if (justCompleted) {
            viewModelScope.launch {
                settings.recordStudySession()
                val now = clock.now().toEpochMilliseconds()
                after.islandIds.forEach { flashcards.markIslandStudied(it, now) }
            }
        }
    }

    /** Stops reading aloud when the screen is left. */
    override fun onCleared() {
        speaker.stop()
    }

    /** Days from [now] until the earliest card that isn't due yet becomes due, or null if there is none. */
    private fun daysUntilNextDue(cards: List<Flashcard>, now: Instant): Int? {
        val nextDue = cards.mapNotNull { it.due }.filter { it > now.toEpochMilliseconds() }.minOrNull() ?: return null
        val today = now.toLocalDateTime(timeZone).date
        return today.daysUntil(Instant.fromEpochMilliseconds(nextDue).toLocalDateTime(timeZone).date)
    }
}

/** The due cards of [cards] at [now]: reviewed cards first, most overdue first, then new cards in the order they were added. */
internal fun dueInStudyOrder(cards: List<Flashcard>, now: Long): List<Flashcard> {
    val (new, reviewed) = cards.filter { isDue(it, now) }.partition { it.due == null }
    return reviewed.sortedBy { it.due } + new.sortedBy { it.cardId }
}

/** [dueInStudyOrder] for cards that know their island and coast. */
internal fun duePlacedInStudyOrder(cards: List<PlacedCard>, now: Long): List<PlacedCard> {
    val byId = cards.associateBy { it.card.cardId }
    return dueInStudyOrder(cards.map { it.card }, now).mapNotNull { byId[it.cardId] }
}

/** The leaky cards of [cards] (see [isLeaky]), the most forgotten first, with the coasts taking turns. */
internal fun leakyInStudyOrder(cards: List<PlacedCard>): List<PlacedCard> =
    interleaveByCoast(cards.filter { isLeaky(it.card) }.sortedWith(compareByDescending<PlacedCard> { it.card.lapses }.thenByDescending { it.card.difficulty ?: 0.0 }))
