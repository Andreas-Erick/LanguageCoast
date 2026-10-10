package com.andreaserick.languagecoast.ui.search

import com.andreaserick.languagecoast.data.CardEdit
import com.andreaserick.languagecoast.data.DeletedContent
import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.MainDispatcherRule
import com.andreaserick.languagecoast.testing.TEST_CLOCK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flashcards = FakeFlashcardRepository()
    private val settings = FakeSettingsRepository()

    private fun TestScope.createViewModel(): SearchViewModel {
        val viewModel = SearchViewModel(flashcards, settings, TEST_CLOCK)
        // uiState is only computed while collected (WhileSubscribed).
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private val SearchViewModel.state get() = uiState.value

    private suspend fun seedTwoCoasts() {
        flashcards.seedCoast(coastId = 1, language = "Spanish")
        flashcards.seedCoast(coastId = 2, language = "German")
        flashcards.addFlashcard(coastId = 1, nativeText = "Good morning", targetText = "Buenos días", category = "Greetings")
        flashcards.addFlashcard(coastId = 2, nativeText = "Good night", targetText = "Gute Nacht", category = "Greetings")
        flashcards.addFlashcard(coastId = 2, nativeText = "The station", targetText = "Der Bahnhof", category = "Travel")
    }

    @Test
    fun nothingIsListedUntilSomethingIsTyped() = runTest {
        seedTwoCoasts()
        val viewModel = createViewModel()

        assertFalse(viewModel.state.hasQuery)
        assertTrue(viewModel.state.hasCards)
        assertTrue(viewModel.state.results.isEmpty())
    }

    @Test
    fun findsCardsOnEveryCoastWithWhereTheyAre() = runTest {
        seedTwoCoasts()
        val viewModel = createViewModel()

        viewModel.onQueryChange("good")

        assertEquals(listOf("Buenos días", "Gute Nacht"), viewModel.state.results.map { it.card.targetText })
        assertEquals(listOf("Spanish", "German"), viewModel.state.results.map { it.language })
        assertEquals(listOf("Greetings", "Greetings"), viewModel.state.results.map { it.islandName })
    }

    @Test
    fun editsShowUpInTheResults() = runTest {
        seedTwoCoasts()
        val viewModel = createViewModel()
        viewModel.onQueryChange("station")
        val placed = viewModel.state.results.single()
        val travel = viewModel.state.islandsByCoast.getValue(2).first { it.name == "Travel" }

        viewModel.editCard(placed.card.cardId, CardEdit("The train station", "Der Bahnhof", emptyList(), null, travel.islandId))

        assertEquals("The train station", viewModel.state.results.single().card.nativeText)
    }

    @Test
    fun deletedCardCanBeRestored() = runTest {
        seedTwoCoasts()
        val viewModel = createViewModel()
        viewModel.onQueryChange("night")
        var deleted: DeletedContent? = null

        viewModel.deleteCard(viewModel.state.results.single().card) { deleted = it }
        assertTrue(viewModel.state.results.isEmpty())

        viewModel.restore(deleted!!)
        assertEquals("Gute Nacht", viewModel.state.results.single().card.targetText)
    }
}
