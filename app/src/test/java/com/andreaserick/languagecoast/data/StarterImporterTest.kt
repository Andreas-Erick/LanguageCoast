package com.andreaserick.languagecoast.data

import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.FakeTranslator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** What a run says when the provider can't translate, and stopping one. The runs that go well are in the coast screen's tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class StarterImporterTest {

    private val flashcards = FakeFlashcardRepository()
    private val settings = FakeSettingsRepository()
    private val translator = FakeTranslator()
    private val importer = StarterImporter(flashcards, settings, StarterTranslator(translator), CoroutineScope(Job()))

    @Before
    fun setUp() {
        flashcards.seedCoast(coastId = 1, language = "German")
        settings.translationProvider.value = TranslationProvider.Gemini
        settings.apiKey.value = "key"
    }

    @Test
    fun aTopicThatCouldNotBeTranslatedEndsWithTheProvidersReason() = runTest {
        val refusal = "Gemini didn't accept the API key. Check your key in Settings."
        translator.completion = Completion.failure(refusal)

        importer.run(1, listOf("Greetings", "Getting By"), emptyMap())

        val progress = importer.progress.value!!
        assertTrue(progress.isFinished)
        // Not a finished run with 0 cards: the reason, so the user knows what to fix.
        assertEquals(refusal, progress.error)
        assertEquals(0, progress.added)
        assertTrue(flashcards.cards.value.isEmpty())
        assertTrue(settings.starterTopicsAdded.value.isEmpty())
        // Greetings is two batches. Neither was sent again sentence by sentence, and the second topic wasn't sent at all.
        assertEquals(2, translator.prompts.size)
        assertEquals(0, translator.requests)
        assertTrue(translator.prompts.all { "\"Greetings\"" in it })
    }

    @Test
    fun withoutAReasonFromTheProviderTheRunStillSaysWhatToCheck() = runTest {
        settings.translationProvider.value = TranslationProvider.OnDevice
        translator.result = TranslationResult.FAILURE

        importer.run(1, listOf("Greetings"), emptyMap())

        assertEquals(
            "Nothing could be translated. Check your connection and the provider in Settings, then try again.",
            importer.progress.value!!.error
        )
        // Two sentences in a row failed, so the rest of the topic wasn't tried one sentence after the other.
        assertEquals(StarterTranslator.GIVE_UP_AFTER, translator.requests)
    }

    @Test
    fun whatWasAddedBeforeTheProviderGaveUpStaysAndIsCounted() = runTest {
        val colours = PhraseBank.theme("Colours")!!
        translator.completionFor = { prompt ->
            if ("topic \"Colours\"" in prompt) Completion((1..StarterTranslator.BATCH_SIZE).joinToString("\n") { "$it || Farbe $it" })
            else Completion.failure("Gemini is overloaded right now. Try again in a moment.")
        }

        importer.run(1, listOf("Colours", "Greetings", "Getting By"), emptyMap())

        val progress = importer.progress.value!!
        assertTrue(progress.isFinished)
        assertEquals(
            "Gemini is overloaded right now. Try again in a moment. ${colours.sentences.size} cards were added; try again for the rest.",
            progress.error
        )
        assertEquals(colours.sentences.size, progress.added)
        assertEquals(colours.sentences.size, flashcards.cards.value.size)
        // The topic that came through counts as added; the one that failed can be added again.
        assertEquals(setOf("1:Colours"), settings.starterTopicsAdded.value)
        assertTrue(translator.prompts.none { "\"Getting By\"" in it })
    }

    @Test
    fun aKeyWithoutPermissionAddsNothingAndSaysSo() = runTest {
        settings.aiSharingAllowed.value = emptySet()

        importer.run(1, listOf("Greetings"), emptyMap())

        assertEquals(
            "Allow Language Coast to send text to Gemini first. You can do that under Settings.",
            importer.progress.value!!.error
        )
        assertTrue(translator.prompts.isEmpty())
        assertEquals(0, translator.requests)
    }

    @Test
    fun aRunCanBeStopped() = runTest {
        // A provider that never answers.
        val stuck = object : Translator {
            override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult = awaitCancellation()
            override suspend fun complete(engine: TranslationEngine, prompt: String): Completion = awaitCancellation()
        }
        val stoppable = StarterImporter(flashcards, settings, StarterTranslator(stuck), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        stoppable.start(1, listOf("Greetings"), emptyMap())
        assertTrue(stoppable.isRunning)
        assertFalse(stoppable.progress.value!!.isFinished)

        stoppable.stop()

        assertFalse(stoppable.isRunning)
        val progress = stoppable.progress.value!!
        assertTrue(progress.isFinished)
        assertTrue(progress.stopped)
        assertNull(progress.error)
        assertTrue(flashcards.cards.value.isEmpty())
        // A stopped run is cleared like a finished one.
        stoppable.dismiss()
        assertNull(stoppable.progress.value)
    }

    @Test
    fun aRunStoppedWhileItsCardsAreBeingSavedSaysHowManyItAdded() = runTest {
        translator.completion = Completion((1..StarterTranslator.BATCH_SIZE).joinToString("\n") { "$it || Farbe $it" })
        lateinit var stoppable: StarterImporter
        // Stop arrives while the topic's cards are being saved: two are in, the third is not.
        val stoppedAtTheThirdCard = object : FlashcardRepository by flashcards {
            override suspend fun addFlashcard(
                coastId: Int, nativeText: String, targetText: String, category: String, emoji: String?, alternatives: List<String>, note: String?
            ): AddedCard {
                if (flashcards.cards.value.size == 2) {
                    stoppable.stop()
                    yield()
                }
                return flashcards.addFlashcard(coastId, nativeText, targetText, category, emoji, alternatives, note)
            }
        }
        stoppable = StarterImporter(stoppedAtTheThirdCard, settings, StarterTranslator(translator), CoroutineScope(StandardTestDispatcher(testScheduler)))

        stoppable.start(1, listOf("Colours"), emptyMap())
        advanceUntilIdle()

        assertFalse(stoppable.isRunning)
        val progress = stoppable.progress.value!!
        assertTrue(progress.isFinished)
        assertTrue(progress.stopped)
        assertNull(progress.error)
        // Not "nothing was added": the two cards that are on the coast are counted.
        assertEquals(2, progress.added)
        assertEquals(2, flashcards.cards.value.size)
        // The topic wasn't finished, so it can be added again.
        assertTrue(settings.starterTopicsAdded.value.isEmpty())
    }
}
