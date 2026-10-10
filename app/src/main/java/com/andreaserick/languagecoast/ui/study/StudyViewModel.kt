package com.andreaserick.languagecoast.ui.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.CardEdit
import com.andreaserick.languagecoast.data.DeletedContent
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.Grade
import com.andreaserick.languagecoast.data.LanguageIsland
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.isDue
import com.andreaserick.languagecoast.data.reviewOutcomes
import com.andreaserick.languagecoast.speech.Speaker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class StudyUiState(
    val islandName: String = "",
    val isLoading: Boolean = true,
    /** Whether the island has any cards in the database at all. */
    val hasCards: Boolean = false,
    /** Cards still left in this session, in study order. */
    val sessionCards: List<Flashcard> = emptyList(),
    /** Number of cards the session started with. */
    val totalCards: Int = 0,
    val currentIndex: Int = 0,
    val isTypingMode: Boolean = false,
    val isSessionComplete: Boolean = false,
    /** The island has cards, but none were due when the session started. */
    val isCaughtUp: Boolean = false,
    /** Days until the island's next card is due, or null if none is scheduled. */
    val nextDueInDays: Int? = null,
    /** Days until the current card would be due again after each grade. */
    val gradeIntervals: Map<Grade, Int> = emptyMap(),
    /** How many times "Again" was pressed in this session. */
    val againCount: Int = 0,
    val streakCount: Int = 0,
    val nativeLanguage: String = SettingsDefaults.NATIVE_LANGUAGE,
    /** The language of the coast this island is on. */
    val targetLanguage: String = "",
    /** Whether the phone has a voice to read [targetLanguage] aloud. */
    val canSpeak: Boolean = false,
    /** The islands on this island's coast, which a card can be moved to when editing it. */
    val coastIslands: List<LanguageIsland> = emptyList(),
    /** Cards are shown in [targetLanguage] and answered in [nativeLanguage], training recognition instead of recall. */
    val isReversed: Boolean = false
) {
    val currentCard: Flashcard? get() = sessionCards.getOrNull(currentIndex)
    val canGoBack: Boolean get() = currentIndex > 0
    val canGoForward: Boolean get() = currentIndex < sessionCards.size - 1

    /** Share of the session's cards already passed (graded anything but "Again"), from 0 to 1. */
    val progress: Float get() = if (totalCards == 0) 0f else (totalCards - sessionCards.size).toFloat() / totalCards
}

/**
 * Runs a study session for one island's due cards (see [isDue]). Every grade reschedules the card
 * with [reviewOutcomes]; "Again" also moves it to the end of the session, any other grade removes it.
 * The session is complete once no cards are left.
 */
@HiltViewModel
class StudyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val speaker: Speaker
) : ViewModel() {

    // Populated by Navigation from the StudyScreenRoute properties.
    private val islandId: Int = checkNotNull(savedStateHandle["islandId"])
    private val islandName: String = savedStateHandle["islandName"] ?: ""

    private data class Session(
        val allCards: List<Flashcard> = emptyList(),
        val cards: List<Flashcard> = emptyList(),
        val total: Int = 0,
        val index: Int = 0,
        val againCount: Int = 0,
        val isStarted: Boolean = false,
        val isLoading: Boolean = true,
        val isTypingMode: Boolean = false
    )

    private val session = MutableStateFlow(Session())

    /** What the session needs to know about the coast this island is on. */
    private data class CoastInfo(val language: String, val canSpeak: Boolean, val islands: List<LanguageIsland>)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val coastInfo = flashcards.observeCoastForIsland(islandId).flatMapLatest { coast ->
        val language = coast?.language.orEmpty()
        val islands = coast?.let { flashcards.observeIslands(it.coastId) } ?: flowOf(emptyList())
        combine(speaker.canSpeak(language), islands) { canSpeak, islands -> CoastInfo(language, canSpeak, islands) }
    }

    val uiState: StateFlow<StudyUiState> = combine(
        session,
        settings.nativeLanguage,
        coastInfo,
        settings.streakCount,
        settings.studyReversed
    ) { s, nativeLanguage, coast, streak, reversed ->
        val now = clock.instant()
        StudyUiState(
            islandName = islandName,
            isLoading = s.isLoading,
            hasCards = s.allCards.isNotEmpty(),
            sessionCards = s.cards,
            totalCards = s.total,
            currentIndex = s.index,
            isTypingMode = s.isTypingMode,
            isSessionComplete = s.isStarted && s.total > 0 && s.cards.isEmpty(),
            isCaughtUp = s.isStarted && s.allCards.isNotEmpty() && s.total == 0,
            nextDueInDays = daysUntilNextDue(s.allCards, now),
            gradeIntervals = s.cards.getOrNull(s.index)
                ?.let { card -> reviewOutcomes(card, now, clock.zone).mapValues { it.value.intervalDays } }
                .orEmpty(),
            againCount = s.againCount,
            streakCount = streak,
            nativeLanguage = nativeLanguage,
            targetLanguage = coast.language,
            canSpeak = coast.canSpeak,
            coastIslands = coast.islands,
            isReversed = reversed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyUiState(islandName = islandName))

    init {
        viewModelScope.launch {
            flashcards.observeCards(islandId).collect(::onCardsChanged)
        }
    }

    /** Starts the session with the due cards once they first load; later updates only refresh or drop cards already in it. */
    private fun onCardsChanged(cards: List<Flashcard>) = updateSession { s ->
        if (!s.isStarted && cards.isNotEmpty()) {
            val due = dueInStudyOrder(cards, clock.millis())
            s.copy(allCards = cards, cards = due, total = due.size, index = 0, isStarted = true, isLoading = false)
        } else {
            // Keep the session order, but use the saved cards and drop those deleted from the database.
            val byId = cards.associateBy { it.cardId }
            val kept = s.cards.mapNotNull { byId[it.cardId] }
            s.copy(allCards = cards, cards = kept, total = s.total - (s.cards.size - kept.size), isLoading = false)
        }
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
        // observeCards re-emits the updated card, which replaces it in the session.
        viewModelScope.launch { flashcards.updateTranslation(card.cardId, alternative, alternatives) }
    }

    /**
     * Saves [edit] to card [cardId]. A card moved to another island leaves the session;
     * otherwise observeCards re-emits it with the changes in place.
     */
    fun editCard(cardId: Int, edit: CardEdit) {
        viewModelScope.launch { flashcards.editCard(cardId, edit) }
    }

    /** Grades the current card, saves its new schedule and moves on. */
    fun onGrade(grade: Grade) {
        val card = session.value.let { it.cards.getOrNull(it.index) } ?: return
        // Don't keep reading the old answer over the next card.
        speaker.stop()
        val reviewed = reviewOutcomes(card, clock.instant(), clock.zone).getValue(grade).card
        updateSession { s ->
            val rest = s.cards.filterNot { it.cardId == card.cardId }
            if (grade == Grade.Again) s.copy(cards = rest + reviewed, againCount = s.againCount + 1) else s.copy(cards = rest)
        }
        viewModelScope.launch { flashcards.saveReview(reviewed) }
    }

    /** Moves to the next card without grading the current one. */
    fun next() = updateSession { it.copy(index = it.index + 1) }

    /** Moves back to the previous card. */
    fun previous() = updateSession { it.copy(index = it.index - 1) }

    /** Starts a session with every card of the island, due or not. Grades still reschedule the cards. */
    fun practiceAll() = updateSession { it.copy(cards = it.allCards, total = it.allCards.size, index = 0, againCount = 0) }

    /**
     * Switches between studying from the native language (recall) and from the coast's language (recognition).
     * Both directions share each card's schedule. Remembered for later sessions.
     */
    fun setReversed(reversed: Boolean) {
        viewModelScope.launch { settings.setStudyReversed(reversed) }
    }

    /** Switches between revealing the answer and typing it. */
    fun setTypingMode(enabled: Boolean) = updateSession { it.copy(isTypingMode = enabled) }

    /** Deletes the current card; [onDeleted] receives it so the delete can be undone. */
    fun deleteCurrentCard(onDeleted: (DeletedContent) -> Unit) {
        val card = uiState.value.currentCard ?: return
        // observeCards re-emits without the card, which removes it from the session.
        viewModelScope.launch { onDeleted(flashcards.deleteFlashcard(card)) }
    }

    /** Puts back a deleted card. Not tied to this ViewModel's scope, so undo works after leaving the screen. */
    suspend fun restore(content: DeletedContent) = flashcards.restore(content)

    /**
     * Applies [transform] to the session, keeping the index in range.
     * When the last card of a session is graded, records the study session and marks the island studied.
     */
    private fun updateSession(transform: (Session) -> Session) {
        val before = session.value
        val after = transform(before).let { it.copy(index = it.index.coerceIn(0, maxOf(0, it.cards.lastIndex))) }
        session.value = after

        val justCompleted = before.cards.isNotEmpty() && after.cards.isEmpty() && after.total > 0
        if (justCompleted) {
            viewModelScope.launch {
                settings.recordStudySession()
                flashcards.markIslandStudied(islandId, clock.millis())
            }
        }
    }

    /** Stops reading aloud when the screen is left. */
    override fun onCleared() {
        speaker.stop()
    }

    /** Days from [now] until the earliest card that isn't due yet becomes due, or null if there is none. */
    private fun daysUntilNextDue(cards: List<Flashcard>, now: Instant): Int? {
        val nextDue = cards.mapNotNull { it.due }.filter { it > now.toEpochMilli() }.minOrNull() ?: return null
        val today = now.atZone(clock.zone).toLocalDate()
        return ChronoUnit.DAYS.between(today, Instant.ofEpochMilli(nextDue).atZone(clock.zone).toLocalDate()).toInt()
    }
}

/** The due cards of [cards] at [now]: reviewed cards first, most overdue first, then new cards in the order they were added. */
internal fun dueInStudyOrder(cards: List<Flashcard>, now: Long): List<Flashcard> {
    val (new, reviewed) = cards.filter { isDue(it, now) }.partition { it.due == null }
    return reviewed.sortedBy { it.due } + new.sortedBy { it.cardId }
}
