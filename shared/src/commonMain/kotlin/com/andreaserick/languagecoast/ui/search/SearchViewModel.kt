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
import com.andreaserick.languagecoast.data.normalizedWords
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/** How long typing has to pause before the cards are searched, so a word typed in one go is searched for once. */
const val SEARCH_PAUSE_MILLIS = 200L

data class SearchUiState(
    /** Whether something was typed to search for. */
    val hasQuery: Boolean = false,
    /** Cards matching the query (as [matchesSearch] decides), by coast, then island. */
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

/**
 * Finds cards on every coast by their native text, translation or alternatives.
 *
 * A collection can hold thousands of cards, so the search is kept off the keyboard's back: what a card is
 * found by is worked out once per change of the cards (not per keystroke), the cards are only looked
 * through once typing pauses ([SEARCH_PAUSE_MILLIS]), and both happen off the screen's thread.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val flashcards: FlashcardRepository,
    settings: SettingsRepository,
    private val clock: Clock,
    private val timeZone: TimeZone
) : ViewModel() {

    /**
     * Where the cards are sorted through: off the screen's thread, which thousands of cards would hold up
     * while typing. It is read each time there is work, so a test can set its own before it collects [uiState].
     */
    var background: CoroutineDispatcher = Dispatchers.Default

    private val query = MutableStateFlow("")

    /** What was typed, once typing pauses. An emptied field counts at once, so the results clear without a wait. */
    private val settledQuery: Flow<String> = query.debounce { typed -> if (typed.isBlank()) 0L else SEARCH_PAUSE_MILLIS }

    /** Every card with the words it is found by. */
    private val searchable: Flow<List<SearchableCard>> = flashcards.observeAllCards().mapLatest { cards ->
        withContext(background) { cards.map { SearchableCard(it, searchWords(it.card)) } }
    }

    private val found: Flow<Found> = combine(settledQuery, searchable, ::Pair).mapLatest { (typed, cards) ->
        val words = normalizedWords(typed)
        Found(
            hasQuery = typed.isNotBlank(),
            hasCards = cards.isNotEmpty(),
            // A query without any words (only punctuation, say) matches nothing.
            results = if (words.isEmpty()) emptyList() else withContext(background) { cards.filter { it.matches(words) }.map { it.placed } }
        )
    }

    val uiState: StateFlow<SearchUiState> = combine(
        found,
        flashcards.observeAllIslands(),
        settings.nativeLanguage
    ) { search, islands, nativeLanguage ->
        SearchUiState(
            hasQuery = search.hasQuery,
            results = search.results,
            hasCards = search.hasCards,
            islandsByCoast = islands.groupBy { it.coastId },
            nativeLanguage = nativeLanguage,
            now = clock.now().toEpochMilliseconds(),
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

    /** What a search for the settled query gave. */
    private class Found(val hasQuery: Boolean, val hasCards: Boolean, val results: List<PlacedCard>)
}

/**
 * A card with the words of its native text, translation and alternatives, lowercase and joined by spaces.
 * Looking for a word of the query in [words] finds what [matchesSearch] finds: a query word has no space
 * in it, so it can only match inside one of the card's words.
 */
private class SearchableCard(val placed: PlacedCard, val words: String) {
    /** Whether every one of [queryWords] is in the card's texts; a word may be part of one ("bahn" finds "Bahnhof"). */
    fun matches(queryWords: List<String>): Boolean = queryWords.all { it in words }
}

/** The words [card] is found by, as [SearchableCard.words] wants them. */
private fun searchWords(card: Flashcard): String =
    (listOf(card.nativeText, card.targetText) + card.alternatives).flatMap(::normalizedWords).joinToString(" ")
