package com.andreaserick.languagecoast.ui.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudyUiState(
    val islandName: String = "",
    val isLoading: Boolean = true,
    /** Whether the island has any cards in the database at all. */
    val hasCards: Boolean = false,
    /** Cards still left in this session, in study order. */
    val sessionCards: List<Flashcard> = emptyList(),
    val currentIndex: Int = 0,
    val isTypingMode: Boolean = false,
    val isSessionComplete: Boolean = false,
    val nativeLanguage: String = SettingsDefaults.NATIVE_LANGUAGE,
    /** The language of the coast this island is on. */
    val targetLanguage: String = ""
) {
    val currentCard: Flashcard? get() = sessionCards.getOrNull(currentIndex)
    val canGoBack: Boolean get() = currentIndex > 0
    val canGoForward: Boolean get() = currentIndex < sessionCards.size - 1
}

/**
 * Runs a study session for one island. "Again" moves a card to the end of the session,
 * "Easy" removes it; the session is complete once no cards are left.
 */
@HiltViewModel
class StudyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    // Populated by Navigation from the StudyScreenRoute properties.
    private val islandId: Int = checkNotNull(savedStateHandle["islandId"])
    private val islandName: String = savedStateHandle["islandName"] ?: ""

    private data class Session(
        val allCards: List<Flashcard> = emptyList(),
        val cards: List<Flashcard> = emptyList(),
        val index: Int = 0,
        val isStarted: Boolean = false,
        val isLoading: Boolean = true,
        val isTypingMode: Boolean = false
    )

    private val session = MutableStateFlow(Session())

    val uiState: StateFlow<StudyUiState> = combine(
        session,
        settings.nativeLanguage,
        flashcards.observeCoastForIsland(islandId).map { it?.language.orEmpty() }
    ) { s, nativeLanguage, targetLanguage ->
        StudyUiState(
            islandName = islandName,
            isLoading = s.isLoading,
            hasCards = s.allCards.isNotEmpty(),
            sessionCards = s.cards,
            currentIndex = s.index,
            isTypingMode = s.isTypingMode,
            isSessionComplete = s.isStarted && s.allCards.isNotEmpty() && s.cards.isEmpty(),
            nativeLanguage = nativeLanguage,
            targetLanguage = targetLanguage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyUiState(islandName = islandName))

    init {
        viewModelScope.launch {
            flashcards.observeCards(islandId).collect(::onCardsChanged)
        }
    }

    private fun onCardsChanged(cards: List<Flashcard>) = updateSession { s ->
        if (!s.isStarted && cards.isNotEmpty()) {
            s.copy(allCards = cards, cards = cards, index = 0, isStarted = true, isLoading = false)
        } else {
            // Keep the session order, but drop cards that were deleted from the database.
            val ids = cards.mapTo(HashSet()) { it.cardId }
            s.copy(allCards = cards, cards = s.cards.filter { it.cardId in ids }, isLoading = false)
        }
    }

    fun onAgain() = updateSession { s ->
        val card = s.cards.getOrNull(s.index) ?: return@updateSession s
        s.copy(cards = s.cards - card + card)
    }

    fun onEasy() = updateSession { s ->
        val card = s.cards.getOrNull(s.index) ?: return@updateSession s
        s.copy(cards = s.cards - card)
    }

    fun next() = updateSession { it.copy(index = it.index + 1) }

    fun previous() = updateSession { it.copy(index = it.index - 1) }

    fun restart() = updateSession { it.copy(cards = it.allCards, index = 0) }

    fun setTypingMode(enabled: Boolean) = updateSession { it.copy(isTypingMode = enabled) }

    fun deleteCurrentCard() {
        val card = uiState.value.currentCard ?: return
        // observeCards re-emits without the card, which removes it from the session.
        viewModelScope.launch { flashcards.deleteFlashcard(card) }
    }

    private fun updateSession(transform: (Session) -> Session) {
        val before = session.value
        val after = transform(before).let { it.copy(index = it.index.coerceIn(0, maxOf(0, it.cards.lastIndex))) }
        session.value = after

        val justCompleted = before.cards.isNotEmpty() && after.cards.isEmpty() && after.allCards.isNotEmpty()
        if (justCompleted) {
            viewModelScope.launch { settings.recordStudySession() }
        }
    }
}
