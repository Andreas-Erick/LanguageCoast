package com.andreaserick.languagecoast.ui.study

import androidx.lifecycle.SavedStateHandle
import com.andreaserick.languagecoast.data.DeletedContent
import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.TEST_CLOCK
import com.andreaserick.languagecoast.testing.MainDispatcherRule
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
class StudyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flashcards = FakeFlashcardRepository()
    private val settings = FakeSettingsRepository()

    private fun TestScope.createViewModel(islandId: Int = 1): StudyViewModel {
        val viewModel = StudyViewModel(
            SavedStateHandle(mapOf("islandId" to islandId, "islandName" to "Island $islandId")),
            flashcards,
            settings,
            TEST_CLOCK
        )
        // uiState is only computed while collected (WhileSubscribed).
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private val StudyViewModel.state get() = uiState.value
    private val StudyViewModel.currentText get() = state.currentCard?.nativeText

    @Test
    fun sessionStartsWithAllCardsOfTheIsland() = runTest {
        flashcards.seed(islandId = 1, cardCount = 3)
        flashcards.seed(islandId = 2, cardCount = 5)

        val viewModel = createViewModel(islandId = 1)

        assertEquals("Island 1", viewModel.state.islandName)
        assertEquals(3, viewModel.state.sessionCards.size)
        assertEquals("n1", viewModel.currentText)
        assertFalse(viewModel.state.isLoading)
    }

    @Test
    fun emptyIslandShowsNoCards() = runTest {
        val viewModel = createViewModel()

        assertFalse(viewModel.state.hasCards)
        assertFalse(viewModel.state.isSessionComplete)
    }

    @Test
    fun againMovesCurrentCardToTheEnd() = runTest {
        flashcards.seed(islandId = 1, cardCount = 3)
        val viewModel = createViewModel()

        viewModel.onAgain()

        assertEquals(listOf("n2", "n3", "n1"), viewModel.state.sessionCards.map { it.nativeText })
        assertEquals("n2", viewModel.currentText)
    }

    @Test
    fun easyOnEveryCardCompletesSessionAndRecordsStreakOnce() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()

        viewModel.onEasy()
        assertFalse(viewModel.state.isSessionComplete)
        viewModel.onEasy()

        assertTrue(viewModel.state.isSessionComplete)
        assertEquals(1, settings.studySessionsRecorded)
    }

    @Test
    fun completingSessionMarksIslandStudiedAndReportsStats() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()

        viewModel.onAgain()
        assertEquals(0f, viewModel.state.progress)
        viewModel.onEasy()
        assertEquals(0.5f, viewModel.state.progress)
        viewModel.onEasy()

        assertTrue(viewModel.state.isSessionComplete)
        assertEquals(2, viewModel.state.totalCards)
        assertEquals(1, viewModel.state.againCount)
        assertEquals(TEST_CLOCK.millis(), flashcards.islands.value.single().lastStudied)
    }

    @Test
    fun deletedCardCanBeRestored() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()
        var deleted: DeletedContent? = null

        viewModel.deleteCurrentCard { deleted = it }
        assertEquals(listOf("n2"), viewModel.state.sessionCards.map { it.nativeText })

        viewModel.restore(deleted!!)
        assertEquals(listOf("n1", "n2"), flashcards.cards.value.map { it.nativeText })
    }

    @Test
    fun restartBringsBackAllCards() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()
        viewModel.onEasy()
        viewModel.onEasy()

        viewModel.restart()

        assertFalse(viewModel.state.isSessionComplete)
        assertEquals(2, viewModel.state.sessionCards.size)
        assertEquals(0, viewModel.state.currentIndex)
    }

    @Test
    fun nextAndPreviousStayWithinBounds() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()

        viewModel.previous()
        assertEquals(0, viewModel.state.currentIndex)
        assertFalse(viewModel.state.canGoBack)

        viewModel.next()
        viewModel.next()
        assertEquals(1, viewModel.state.currentIndex)
        assertFalse(viewModel.state.canGoForward)
    }

    @Test
    fun easyOnLastCardKeepsIndexInBounds() = runTest {
        flashcards.seed(islandId = 1, cardCount = 3)
        val viewModel = createViewModel()
        viewModel.next()
        viewModel.next()

        viewModel.onEasy()

        assertEquals(1, viewModel.state.currentIndex)
        assertEquals("n2", viewModel.currentText)
    }

    @Test
    fun deletingCurrentCardRemovesItFromDatabaseAndSession() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()

        viewModel.deleteCurrentCard {}

        assertEquals(listOf("n2"), flashcards.cards.value.map { it.nativeText })
        assertEquals(listOf("n2"), viewModel.state.sessionCards.map { it.nativeText })
    }

    @Test
    fun deletingLastCardOfIslandShowsEmptyStateWithoutRecordingStreak() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        val viewModel = createViewModel()

        viewModel.deleteCurrentCard {}

        assertFalse(viewModel.state.hasCards)
        assertFalse(viewModel.state.isSessionComplete)
        assertEquals(0, settings.studySessionsRecorded)
    }

    @Test
    fun targetLanguageComesFromTheIslandsCoast() = runTest {
        settings.nativeLanguage.value = "German"
        flashcards.seedCoast(coastId = 1, language = "Spanish")
        flashcards.seed(islandId = 1, cardCount = 1, coastId = 1)
        flashcards.seedCoast(coastId = 2, language = "Icelandic")
        flashcards.seed(islandId = 2, cardCount = 1, coastId = 2)

        val viewModel = createViewModel(islandId = 2)

        assertEquals("German", viewModel.state.nativeLanguage)
        assertEquals("Icelandic", viewModel.state.targetLanguage)
    }
}
