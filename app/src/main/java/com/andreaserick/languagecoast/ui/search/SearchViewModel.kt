package com.andreaserick.languagecoast.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.CardEdit
import com.andreaserick.languagecoast.data.DeletedContent
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.LanguageIsland
import com.andreaserick.languagecoast.data.PlacedCard
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.matchesSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class SearchUiState(
    /** Whether something was typed to search for. */
    val hasQuery: Boolean = false,
    /** Cards matching the query (see [matchesSearch]), by coast, then island. */
    val results: List<PlacedCard> = emptyList(),
    /** Whether there are any cards to search at all. */
    val hasCards: Boolean = false,
    /** Every island, grouped by coast ID, which a card can be moved to within its coast. */
    val islandsByCoast: Map<Int, List<LanguageIsland>> = emptyMap(),
    val nativeLanguage: String = SettingsDefaults.NATIVE_LANGUAGE,
    /** Current time in epoch millis, for the due labels. */
    val now: Long = 0L,
    val isLoading: Boolean = true
)

/** Finds cards on every coast by their native text, translation or alternatives. */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val flashcards: FlashcardRepository,
    settings: SettingsRepository,
    private val clock: Clock
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        flashcards.observeAllCards(),
        flashcards.observeAllIslands(),
        settings.nativeLanguage
    ) { query, cards, islands, nativeLanguage ->
        SearchUiState(
            hasQuery = query.isNotBlank(),
            results = cards.filter { matchesSearch(it.card, query) },
            hasCards = cards.isNotEmpty(),
            islandsByCoast = islands.groupBy { it.coastId },
            nativeLanguage = nativeLanguage,
            now = clock.millis(),
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    /** Searches for [value]; the text field keeps its own state, so this only updates the results. */
    fun onQueryChange(value: String) {
        query.value = value
    }

    /** Saves [edit] to card [cardId]. */
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
