package com.andreaserick.languagecoast.ui.island

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.CardEdit
import com.andreaserick.languagecoast.data.DeletedContent
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.LanguageIsland
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.isDue
import com.andreaserick.languagecoast.data.islandEmoji
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class IslandUiState(
    val islandName: String = "",
    val emoji: String = "",
    /** The island's cards, soonest due first (see [nextDueOrder]). */
    val cards: List<Flashcard> = emptyList(),
    val dueCount: Int = 0,
    /** The islands on this island's coast, which a card can be moved to. */
    val coastIslands: List<LanguageIsland> = emptyList(),
    val nativeLanguage: String = SettingsDefaults.NATIVE_LANGUAGE,
    /** The language of the coast this island is on. */
    val targetLanguage: String = "",
    /** Current time in epoch millis, for the due labels. */
    val now: Long = 0L,
    val isLoading: Boolean = true
)

/** Lists the cards of one island, where they can be edited, moved to another island or deleted. */
@HiltViewModel
class IslandViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val flashcards: FlashcardRepository,
    settings: SettingsRepository,
    private val clock: Clock
) : ViewModel() {

    // Populated by Navigation from the IslandScreenRoute properties.
    private val islandId: Int = checkNotNull(savedStateHandle["islandId"])
    private val islandName: String = savedStateHandle["islandName"] ?: ""

    private val coast = flashcards.observeCoastForIsland(islandId)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val coastIslands = coast.flatMapLatest { coast ->
        coast?.let { flashcards.observeIslands(it.coastId) } ?: flowOf(emptyList())
    }

    val uiState: StateFlow<IslandUiState> = combine(
        flashcards.observeIsland(islandId),
        flashcards.observeCards(islandId),
        coast,
        coastIslands,
        settings.nativeLanguage
    ) { island, cards, coast, islands, nativeLanguage ->
        val now = clock.millis()
        IslandUiState(
            islandName = island?.name ?: islandName,
            emoji = island?.let(::islandEmoji).orEmpty(),
            cards = nextDueOrder(cards, now),
            dueCount = cards.count { isDue(it, now) },
            coastIslands = islands,
            nativeLanguage = nativeLanguage,
            targetLanguage = coast?.language.orEmpty(),
            now = now,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IslandUiState(islandName = islandName))

    /** Saves [edit] to card [cardId]; a card moved to another island drops out of the list. */
    fun editCard(cardId: Int, edit: CardEdit) {
        viewModelScope.launch { flashcards.editCard(cardId, edit) }
    }

    /** Deletes [card] right away; [onDeleted] receives it so the delete can be undone. */
    fun deleteCard(card: Flashcard, onDeleted: (DeletedContent) -> Unit) {
        viewModelScope.launch { onDeleted(flashcards.deleteFlashcard(card)) }
    }

    /** Puts back a deleted card. Not tied to this ViewModel's scope, so undo works after leaving the screen. */
    suspend fun restore(content: DeletedContent) = flashcards.restore(content)
}

/** [cards] by when they are next due, with new cards counted as due at [now]; ties in the order they were added. */
internal fun nextDueOrder(cards: List<Flashcard>, now: Long): List<Flashcard> =
    cards.sortedWith(compareBy<Flashcard> { it.due ?: now }.thenBy { it.cardId })
