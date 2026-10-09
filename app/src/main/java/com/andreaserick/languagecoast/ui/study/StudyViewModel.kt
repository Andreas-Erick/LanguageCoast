package com.andreaserick.languagecoast.ui.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.DeletedContent
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.Grade
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
import kotlinx.coroutines.flow.map
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
    val canSpeak: Boolean = false
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

    private val targetLanguage = flashcards.observeCoastForIsland(islandId).map { it?.language.orEmpty() }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StudyUiState> = combine(
        session,
        settings.nativeLanguage,
        targetLanguage,
        settings.streakCount,
        targetLanguage.flatMapLatest { speaker.canSpeak(it) }
    ) { s, nativeLanguage, targetLanguage, streak, canSpeak ->
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
            targetLanguage = targetLanguage,
            canSpeak = canSpeak
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyUiState(islandName = islandName))

    init {
        viewModelScope.launch {
            flashcards.observeCards(islandId).collect(::onCardsChanged)
        }
    }

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
        val state = uiState.value
        val card = state.currentCard ?: return
        if (state.canSpeak) speaker.speak(card.targetText, state.targetLanguage)
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

    fun next() = updateSession { it.copy(index = it.index + 1) }

    fun previous() = updateSession { it.copy(index = it.index - 1) }

    /** Starts a session with every card of the island, due or not. Grades still reschedule the cards. */
    fun practiceAll() = updateSession { it.copy(cards = it.allCards, total = it.allCards.size, index = 0, againCount = 0) }

    fun setTypingMode(enabled: Boolean) = updateSession { it.copy(isTypingMode = enabled) }

    /** Deletes the current card; [onDeleted] receives it so the delete can be undone. */
    fun deleteCurrentCard(onDeleted: (DeletedContent) -> Unit) {
        val card = uiState.value.currentCard ?: return
        // observeCards re-emits without the card, which removes it from the session.
        viewModelScope.launch { onDeleted(flashcards.deleteFlashcard(card)) }
    }

    /** Puts back a deleted card. Not tied to this ViewModel's scope, so undo works after leaving the screen. */
    suspend fun restore(content: DeletedContent) = flashcards.restore(content)

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

    override fun onCleared() {
        speaker.stop()
    }

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
