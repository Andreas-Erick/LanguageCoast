package com.andreaserick.languagecoast.ui

import androidx.lifecycle.SavedStateHandle
import com.andreaserick.languagecoast.data.BACKUP_FORMAT
import com.andreaserick.languagecoast.data.BACKUP_VERSION
import com.andreaserick.languagecoast.data.Backup
import com.andreaserick.languagecoast.data.BackupCoast
import com.andreaserick.languagecoast.data.BackupIsland
import com.andreaserick.languagecoast.data.BackupManager
import com.andreaserick.languagecoast.data.BackupSettings
import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.Grade
import com.andreaserick.languagecoast.data.ImportedCard
import com.andreaserick.languagecoast.data.LanguageIsland
import com.andreaserick.languagecoast.data.NoWorksheetPrinter
import com.andreaserick.languagecoast.data.PersonalIslandBuilder
import com.andreaserick.languagecoast.data.SentenceSuggester
import com.andreaserick.languagecoast.data.StarterImporter
import com.andreaserick.languagecoast.data.StarterTranslator
import com.andreaserick.languagecoast.data.SuggestedSentence
import com.andreaserick.languagecoast.data.SuggestionPool
import com.andreaserick.languagecoast.data.TranslationRequest
import com.andreaserick.languagecoast.data.TranslationResult
import com.andreaserick.languagecoast.data.Translator
import com.andreaserick.languagecoast.data.WorksheetGenerator
import com.andreaserick.languagecoast.data.encodeBackup
import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeReminderScheduler
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.FakeSpeaker
import com.andreaserick.languagecoast.testing.FakeTranslator
import com.andreaserick.languagecoast.testing.MainDispatcherRule
import com.andreaserick.languagecoast.testing.TEST_CLOCK
import com.andreaserick.languagecoast.testing.TEST_TIME_ZONE
import com.andreaserick.languagecoast.ui.coast.CoastViewModel
import com.andreaserick.languagecoast.ui.create.CreateViewModel
import com.andreaserick.languagecoast.ui.settings.BackupViewModel
import com.andreaserick.languagecoast.ui.settings.CardTransferViewModel
import com.andreaserick.languagecoast.ui.study.StudyMode
import com.andreaserick.languagecoast.ui.study.StudyViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

/**
 * Chaotic user behavior ("monkey testing"), spam-click re-entrancy guards, mid-flight cancellation,
 * and deadlock-prevention tests (Spec 2.8).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChaosViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun spamClickingConfirmRestoreExecutesOnlyOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        var restoreCalls = 0
        val delegate = FakeFlashcardRepository()
        val flashcards = object : FlashcardRepository by delegate {
            override suspend fun replaceAll(coasts: List<Coast>, islands: List<LanguageIsland>, cards: List<Flashcard>) {
                gate.await()
                restoreCalls++
                delegate.replaceAll(coasts, islands, cards)
            }
        }
        val settings = FakeSettingsRepository()
        val reminders = FakeReminderScheduler()
        val vm = BackupViewModel(BackupManager(flashcards, settings, TEST_CLOCK), flashcards, reminders, TEST_CLOCK, TEST_TIME_ZONE)
        val backup = Backup(
            format = BACKUP_FORMAT,
            version = BACKUP_VERSION,
            createdAt = TEST_CLOCK.now().toEpochMilliseconds(),
            coasts = listOf(BackupCoast(1, "German", creationDate = 1_760_000_000_000L)),
            islands = listOf(BackupIsland(1, 1, "Greetings", creationDate = 1_760_000_000_000L)),
            cards = emptyList(),
            settings = BackupSettings()
        )
        vm.onRestoreFileRead(encodeBackup(backup))

        var callbacks = 0
        repeat(10) { vm.confirmRestore { callbacks++ } }
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, restoreCalls)
        assertEquals(1, callbacks)
        assertFalse(vm.uiState.value.isRestoring)
    }

    @Test
    fun spamClickingConfirmImportAddsCardsOnlyOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        val delegate = FakeFlashcardRepository()
        delegate.seedCoast(1, "German")
        val flashcards = object : FlashcardRepository by delegate {
            override suspend fun addFlashcards(coastId: Int, cards: List<ImportedCard>, defaultIsland: String) =
                delegate.addFlashcards(coastId, cards, defaultIsland).also { gate.await() }
        }
        val settings = FakeSettingsRepository()
        val vm = CardTransferViewModel(flashcards, settings)
        val collector = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onTextCardsRead(listOf(ImportedCard("Hello", "Hallo", "Greetings")))

        var callbacks = 0
        repeat(10) { vm.confirmImport { callbacks++ } }
        gate.complete(Unit)
        advanceUntilIdle()
        collector.cancel()

        assertEquals(1, callbacks)
        assertEquals(1, delegate.cards.value.size)
        assertFalse(vm.uiState.value.isImporting)
    }

    @Test
    fun dismissingBatchMidFlightCancelsOldJobAndNeverCorruptsNewBatch() = runTest {
        val flashcards = FakeFlashcardRepository()
        flashcards.seedCoast(1, "Spanish")
        val settings = FakeSettingsRepository().apply { apiKey.value = "key" }
        val firstGate = CompletableDeferred<TranslationResult>()
        var callCount = 0
        val translator = object : Translator {
            override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult {
                callCount++
                return if (callCount == 1) firstGate.await()
                else TranslationResult("Hola nuevo", "Greetings", isSuccess = true)
            }
        }
        val pool = SuggestionPool(flashcards, settings, SentenceSuggester(translator, settings, StarterTranslator(translator)), TEST_CLOCK, TestScope())
        val vm = CreateViewModel(flashcards, settings, translator, TEST_CLOCK, TEST_TIME_ZONE, pool)

        // Start batch 1 and immediately dismiss while awaiting translator.
        vm.startBatch("Old sentence one.")
        vm.saveBatch()
        assertEquals(1, callCount)
        vm.dismissBatch()
        assertNull(vm.uiState.batch)

        // Start batch 2 and complete the old gate; old cancelled job must not write or touch batch 2.
        vm.startBatch("New sentence two.")
        firstGate.complete(TranslationResult("Viejo", "Old", isSuccess = true))
        advanceUntilIdle()

        assertTrue(flashcards.cards.value.isEmpty())
        assertNotNull(vm.uiState.batch)
        assertNull(vm.uiState.batch?.saved)
    }

    @Test
    fun keepSuggestionFailureNeverDeadlocksStudySession() = runTest {
        val delegate = FakeFlashcardRepository()
        delegate.seedCoast(1, "German")
        delegate.suggestions.value = listOf(
            SuggestedSentence(suggestionId = 1, coastId = 1, nativeText = "Hello", targetText = "Hallo", category = "Greetings")
        )
        val flashcards = object : FlashcardRepository by delegate {
            override suspend fun addFlashcard(
                coastId: Int, nativeText: String, targetText: String, category: String, emoji: String?,
                alternatives: List<String>, note: String?
            ) = throw IllegalStateException("Simulated disk error")
        }
        val settings = FakeSettingsRepository()
        val vm = StudyViewModel(
            SavedStateHandle(mapOf("scope" to "daily", "coastId" to 1, "islandName" to "German")),
            flashcards, settings, TEST_CLOCK, TEST_TIME_ZONE, FakeSpeaker(), FakeTranslator()
        )
        val collector = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.learning)
        vm.keepSuggestion()
        advanceUntilIdle()

        // Even though addFlashcard threw, pendingKeeps must have decremented to 0 so the session completes instead of hanging.
        assertTrue(vm.uiState.value.isSessionComplete)
        collector.cancel()
    }

    @Test
    fun seededMonkeyFuzzingOfStudyViewModelPreservesAllInvariants() = runTest {
        val seed = 133742L
        val rng = Random(seed)
        val flashcards = FakeFlashcardRepository()
        flashcards.seedCoast(1, "German")
        val island = LanguageIsland(islandId = 1, coastId = 1, name = "Travel")
        flashcards.islands.value = listOf(island)
        flashcards.cards.value = List(8) { i ->
            Flashcard(cardId = i + 1, islandId = 1, nativeText = "Sentence $i", targetText = "Satz $i")
        }
        val settings = FakeSettingsRepository()
        val vm = StudyViewModel(
            SavedStateHandle(mapOf("islandId" to 1, "islandName" to "Travel", "scope" to "island", "coastId" to 1)),
            flashcards, settings, TEST_CLOCK, TEST_TIME_ZONE, FakeSpeaker(), FakeTranslator()
        )
        val collector = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        repeat(150) { step ->
            val state = vm.uiState.value
            assertTrue("seed=$seed step=$step negative totalCards", state.totalCards >= 0)
            assertTrue("seed=$seed step=$step negative againCount", state.againCount >= 0)
            when (rng.nextInt(7)) {
                0 -> vm.onGrade(Grade.entries[rng.nextInt(Grade.entries.size)])
                1 -> vm.setMode(StudyMode.entries[rng.nextInt(StudyMode.entries.size)])
                2 -> vm.setReversed(rng.nextBoolean())
                3 -> vm.next()
                4 -> vm.previous()
                5 -> if (state.isSessionComplete) vm.practiceAll()
                6 -> vm.dismissExplanation()
            }
            advanceUntilIdle()
        }
        collector.cancel()
    }

    @Test
    fun writeTalkOnMissingCoastShowsErrorInsteadOfInfiniteLoading() = runTest {
        val flashcards = FakeFlashcardRepository()
        val settings = FakeSettingsRepository()
        val translator = FakeTranslator()
        val starterTranslator = StarterTranslator(translator)
        val starterImporter = StarterImporter(flashcards, settings, starterTranslator)
        val personalBuilder = PersonalIslandBuilder(flashcards, settings, translator, starterTranslator)
        val worksheetGen = WorksheetGenerator(translator, settings, flashcards, TEST_CLOCK, TEST_TIME_ZONE)
        val vm = CoastViewModel(
            SavedStateHandle(mapOf("coastId" to 999, "coastName" to "Missing")),
            flashcards, settings, TEST_CLOCK, TEST_TIME_ZONE, starterImporter, personalBuilder, worksheetGen, NoWorksheetPrinter(), translator, FakeSpeaker()
        )
        val collector = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.writeTalk(LanguageIsland(islandId = 1, coastId = 999, name = "Travel"))
        advanceUntilIdle()

        val talk = vm.uiState.value.talk
        assertNotNull(talk)
        assertFalse(talk!!.isWriting)
        assertNotNull(talk.error)
        collector.cancel()
    }
}
