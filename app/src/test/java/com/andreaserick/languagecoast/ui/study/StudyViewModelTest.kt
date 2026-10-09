package com.andreaserick.languagecoast.ui.study

import androidx.lifecycle.SavedStateHandle
import com.andreaserick.languagecoast.data.DeletedContent
import com.andreaserick.languagecoast.data.Grade
import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.FakeSpeaker
import com.andreaserick.languagecoast.testing.TEST_CLOCK
import com.andreaserick.languagecoast.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class StudyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flashcards = FakeFlashcardRepository()
    private val settings = FakeSettingsRepository()
    private val speaker = FakeSpeaker()

    private fun TestScope.createViewModel(islandId: Int = 1): StudyViewModel {
        val viewModel = StudyViewModel(
            SavedStateHandle(mapOf("islandId" to islandId, "islandName" to "Island $islandId")),
            flashcards,
            settings,
            TEST_CLOCK,
            speaker
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

        viewModel.onGrade(Grade.Again)

        assertEquals(listOf("n2", "n3", "n1"), viewModel.state.sessionCards.map { it.nativeText })
        assertEquals("n2", viewModel.currentText)
    }

    @Test
    fun passingEveryCardCompletesSessionAndRecordsStreakOnce() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()

        viewModel.onGrade(Grade.Good)
        assertFalse(viewModel.state.isSessionComplete)
        viewModel.onGrade(Grade.Good)

        assertTrue(viewModel.state.isSessionComplete)
        assertEquals(1, settings.studySessionsRecorded)
    }

    @Test
    fun completingSessionMarksIslandStudiedAndReportsStats() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()

        viewModel.onGrade(Grade.Again)
        assertEquals(0f, viewModel.state.progress)
        viewModel.onGrade(Grade.Good)
        assertEquals(0.5f, viewModel.state.progress)
        viewModel.onGrade(Grade.Good)

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
    fun practiceAllBringsBackAllCards() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()
        viewModel.onGrade(Grade.Good)
        viewModel.onGrade(Grade.Good)

        viewModel.practiceAll()

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
    fun passingLastCardKeepsIndexInBounds() = runTest {
        flashcards.seed(islandId = 1, cardCount = 3)
        val viewModel = createViewModel()
        viewModel.next()
        viewModel.next()

        viewModel.onGrade(Grade.Good)

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

    @Test
    fun gradingSavesTheCardsNextReview() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()

        viewModel.onGrade(Grade.Good)

        val card = flashcards.cards.value.first()
        assertEquals(TEST_CLOCK.millis(), card.lastReviewed)
        // A new card graded "Good" comes back in 3 days, at the start of that day.
        assertEquals(startOfDay("2026-10-12"), card.due)
        assertEquals(null, flashcards.cards.value.last().due)
    }

    @Test
    fun againReschedulesTheCardAndKeepsItInTheSession() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()

        viewModel.onGrade(Grade.Again)

        assertEquals(startOfDay("2026-10-10"), flashcards.cards.value.first().due)
        assertEquals(listOf("n2", "n1"), viewModel.state.sessionCards.map { it.nativeText })
    }

    @Test
    fun gradeIntervalsArePreviewedForTheCurrentCard() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        val viewModel = createViewModel()

        assertEquals(
            mapOf(Grade.Again to 1, Grade.Hard to 1, Grade.Good to 3, Grade.Easy to 16),
            viewModel.state.gradeIntervals
        )
    }

    @Test
    fun sessionHasOnlyDueCardsWithOverdueOnesBeforeNewOnes() = runTest {
        flashcards.seed(islandId = 1, cardCount = 4)
        setDue("n2", startOfDay("2026-10-11")) // Not due yet.
        setDue("n3", startOfDay("2026-10-09")) // Due today.
        setDue("n4", startOfDay("2026-10-01")) // Overdue.

        val viewModel = createViewModel()

        assertEquals(listOf("n4", "n3", "n1"), viewModel.state.sessionCards.map { it.nativeText })
    }

    @Test
    fun islandWithNothingDueIsCaughtUpUntilPracticingAll() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        setDue("n1", startOfDay("2026-10-11"))
        setDue("n2", startOfDay("2026-10-14"))

        val viewModel = createViewModel()

        assertTrue(viewModel.state.isCaughtUp)
        assertFalse(viewModel.state.isSessionComplete)
        assertEquals(2, viewModel.state.nextDueInDays)

        viewModel.practiceAll()

        assertFalse(viewModel.state.isCaughtUp)
        assertEquals(2, viewModel.state.sessionCards.size)
    }

    @Test
    fun speakingReadsTheTranslationInTheCoastLanguage() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()

        assertTrue(viewModel.state.canSpeak)
        viewModel.speakAnswer()

        assertEquals(listOf("t1" to "Spanish"), speaker.spoken)
    }

    @Test
    fun noVoiceForTheLanguageMeansNothingIsRead() = runTest {
        speaker.available = emptySet()
        flashcards.seed(islandId = 1, cardCount = 1)
        val viewModel = createViewModel()

        assertFalse(viewModel.state.canSpeak)
        viewModel.speakAnswer()

        assertTrue(speaker.spoken.isEmpty())
    }

    @Test
    fun gradingStopsReadingTheOldAnswer() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()

        viewModel.onGrade(Grade.Good)

        assertEquals(1, speaker.stops)
    }

    @Test
    fun makingAnAlternativeMainSwapsItWithTheTranslation() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        flashcards.cards.update { all -> all.map { if (it.nativeText == "n1") it.copy(alternatives = listOf("a1", "b1")) else it } }
        val viewModel = createViewModel()

        viewModel.makeMainTranslation("b1")

        val card = viewModel.state.currentCard!!
        assertEquals("b1", card.targetText)
        assertEquals(listOf("a1", "t1"), card.alternatives)
        assertEquals("b1", flashcards.cards.value.first().targetText)
        // Still the same card in the same place in the session.
        assertEquals(listOf("n1", "n2"), viewModel.state.sessionCards.map { it.nativeText })
    }

    @Test
    fun alternativesCanBeReadAloud() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        val viewModel = createViewModel()

        viewModel.speak("a1")

        assertEquals(listOf("a1" to "Spanish"), speaker.spoken)
    }

    private fun setDue(nativeText: String, due: Long) {
        flashcards.cards.update { all -> all.map { if (it.nativeText == nativeText) it.copy(due = due) else it } }
    }

    private fun startOfDay(date: String): Long =
        LocalDate.parse(date).atStartOfDay(TEST_CLOCK.zone).toInstant().toEpochMilli()
}
