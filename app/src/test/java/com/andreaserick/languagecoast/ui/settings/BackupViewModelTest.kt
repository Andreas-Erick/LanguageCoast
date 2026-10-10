package com.andreaserick.languagecoast.ui.settings

import com.andreaserick.languagecoast.data.Backup
import com.andreaserick.languagecoast.data.BackupManager
import com.andreaserick.languagecoast.data.Grade
import com.andreaserick.languagecoast.data.reviewOutcomes
import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeReminderScheduler
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** The phone the backup is made on, and the new phone it is restored on. */
    private val oldCards = FakeFlashcardRepository()
    private val oldSettings = FakeSettingsRepository()
    private val newCards = FakeFlashcardRepository()
    private val newSettings = FakeSettingsRepository()
    private val reminders = FakeReminderScheduler()

    private fun TestScope.createViewModel(cards: FakeFlashcardRepository, settings: FakeSettingsRepository): BackupViewModel {
        val viewModel = BackupViewModel(BackupManager(cards, settings, TEST_CLOCK), cards, reminders, TEST_CLOCK)
        // uiState is only computed while collected (WhileSubscribed).
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    /** Two coasts, a reviewed card, a streak and some settings on the old phone. */
    private suspend fun seedOldPhone() {
        oldCards.seed(islandId = 1, cardCount = 2, coastId = 1)
        oldCards.seedCoast(coastId = 2, language = "German")
        oldCards.addFlashcard(coastId = 2, nativeText = "Hello", targetText = "Hallo", category = "Greetings", alternatives = listOf("Servus"))
        val reviewed = reviewOutcomes(oldCards.cards.value.first(), TEST_CLOCK.instant(), TEST_CLOCK.zone).getValue(Grade.Good).card
        oldCards.saveReview(reviewed)
        oldCards.markIslandStudied(1, TEST_CLOCK.millis())
        oldSettings.nativeLanguage.value = "Dutch"
        oldSettings.activeCoastId.value = 2
        oldSettings.streakCount.value = 6
        oldSettings.studyDays.value = setOf(LocalDate.parse("2026-10-08"), LocalDate.parse("2026-10-09"))
        oldSettings.studyReversed.value = true
        oldSettings.apiKey.value = "secret"
    }

    @Test
    fun backupAndRestoreMovesEverythingToANewPhone() = runTest {
        seedOldPhone()
        val file = createViewModel(oldCards, oldSettings).buildBackup()
        assertEquals(3, file.cardCount)
        newSettings.apiKey.value = "new-phone-key"
        val viewModel = createViewModel(newCards, newSettings)

        viewModel.onRestoreFileRead(file.text)
        assertEquals(3, viewModel.uiState.value.restoreSummary?.cardCount)
        viewModel.confirmRestore {}

        assertEquals(oldCards.coasts.value, newCards.coasts.value)
        assertEquals(oldCards.islands.value.sortedBy { it.islandId }, newCards.islands.value.sortedBy { it.islandId })
        // Review progress, alternatives and IDs all come along.
        assertEquals(oldCards.cards.value.sortedBy { it.cardId }, newCards.cards.value.sortedBy { it.cardId })
        assertEquals("Dutch", newSettings.nativeLanguage.value)
        assertEquals(2, newSettings.activeCoastId.value)
        assertEquals(6, newSettings.streakCount.value)
        assertEquals(oldSettings.studyDays.value, newSettings.studyDays.value)
        assertTrue(newSettings.studyReversed.value)
        // API keys aren't in the backup.
        assertEquals("new-phone-key", newSettings.apiKey.value)
        assertEquals(1, reminders.reschedules)
        assertNull(viewModel.uiState.value.restorePreview)
    }

    @Test
    fun aRestoreCanBeUndone() = runTest {
        seedOldPhone()
        val file = createViewModel(oldCards, oldSettings).buildBackup()
        newCards.seed(islandId = 10, cardCount = 1, coastId = 5)
        newSettings.streakCount.value = 2
        val before = newCards.cards.value
        val viewModel = createViewModel(newCards, newSettings)
        var previous: Backup? = null

        viewModel.onRestoreFileRead(file.text)
        viewModel.confirmRestore { previous = it }
        assertEquals(3, newCards.cards.value.size)

        viewModel.undoRestore(previous!!)
        assertEquals(before, newCards.cards.value)
        assertEquals(2, newSettings.streakCount.value)
    }

    @Test
    fun aFileThatIsNoBackupChangesNothing() = runTest {
        newCards.seed(islandId = 1, cardCount = 1)
        val viewModel = createViewModel(newCards, newSettings)

        viewModel.onRestoreFileRead("Front,Back\nHello,Hola")

        assertNull(viewModel.uiState.value.restorePreview)
        assertNotNull(viewModel.uiState.value.message)
        assertEquals(1, newCards.cards.value.size)
    }

    @Test
    fun cancellingTheRestoreChangesNothing() = runTest {
        seedOldPhone()
        val file = createViewModel(oldCards, oldSettings).buildBackup()
        newCards.cards.update { emptyList() }
        val viewModel = createViewModel(newCards, newSettings)

        viewModel.onRestoreFileRead(file.text)
        viewModel.cancelRestore()

        assertNull(viewModel.uiState.value.restorePreview)
        assertTrue(newCards.cards.value.isEmpty())
        assertEquals(0, reminders.reschedules)
    }

    @Test
    fun theFileNameHasTodaysDate() = runTest {
        assertEquals("language-coast-backup-2026-10-09.json", createViewModel(newCards, newSettings).backupFileName())
    }
}
