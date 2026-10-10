package com.andreaserick.languagecoast.ui.island

import androidx.lifecycle.SavedStateHandle
import com.andreaserick.languagecoast.data.CardEdit
import com.andreaserick.languagecoast.data.DeletedContent
import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.MainDispatcherRule
import com.andreaserick.languagecoast.testing.TEST_CLOCK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IslandViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flashcards = FakeFlashcardRepository()
    private val settings = FakeSettingsRepository()
    private val now = TEST_CLOCK.millis()

    private fun TestScope.createViewModel(islandId: Int = 1): IslandViewModel {
        val viewModel = IslandViewModel(
            SavedStateHandle(mapOf("islandId" to islandId, "islandName" to "Island $islandId")),
            flashcards,
            settings,
            TEST_CLOCK
        )
        // uiState is only computed while collected (WhileSubscribed).
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private val IslandViewModel.state get() = uiState.value

    @Test
    fun listsTheIslandsCardsSoonestDueFirst() = runTest {
        flashcards.seed(islandId = 1, cardCount = 4)
        flashcards.seed(islandId = 2, cardCount = 2)
        setDue("n1", now + DAY)
        setDue("n2", now - DAY)
        val viewModel = createViewModel()

        // Overdue, then new cards (due now) in the order they were added, then upcoming ones.
        assertEquals(listOf("n2", "n3", "n4", "n1"), viewModel.state.cards.map { it.nativeText })
        assertEquals(3, viewModel.state.dueCount)
        assertEquals("Spanish", viewModel.state.targetLanguage)
        assertEquals(listOf(1, 2), viewModel.state.coastIslands.map { it.islandId })
        assertFalse(viewModel.state.isLoading)
    }

    @Test
    fun editingKeepsTheReviewProgress() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        setDue("n1", now + DAY)
        val viewModel = createViewModel()
        val card = viewModel.state.cards.single()

        viewModel.editCard(card.cardId, CardEdit("hello", "hola", listOf("buenas"), note = null, islandId = 1))

        val edited = viewModel.state.cards.single()
        assertEquals("hello", edited.nativeText)
        assertEquals("hola", edited.targetText)
        assertEquals(listOf("buenas"), edited.alternatives)
        assertEquals(now + DAY, edited.due)
    }

    @Test
    fun movingACardToAnotherIslandTakesItOffTheList() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        flashcards.seed(islandId = 2, cardCount = 1)
        val viewModel = createViewModel()
        val card = viewModel.state.cards.first()

        viewModel.editCard(card.cardId, CardEdit(card.nativeText, card.targetText, emptyList(), null, islandId = 2))

        assertEquals(listOf("n2"), viewModel.state.cards.map { it.nativeText })
        assertEquals(2, flashcards.cards.value.count { it.islandId == 2 })
    }

    @Test
    fun deletedCardCanBeRestored() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()
        var deleted: DeletedContent? = null

        viewModel.deleteCard(viewModel.state.cards.first()) { deleted = it }
        assertEquals(listOf("n2"), viewModel.state.cards.map { it.nativeText })

        viewModel.restore(deleted!!)
        assertEquals(listOf("n1", "n2"), viewModel.state.cards.map { it.nativeText })
    }

    private fun setDue(nativeText: String, due: Long) {
        flashcards.cards.update { all -> all.map { if (it.nativeText == nativeText) it.copy(due = due) else it } }
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
