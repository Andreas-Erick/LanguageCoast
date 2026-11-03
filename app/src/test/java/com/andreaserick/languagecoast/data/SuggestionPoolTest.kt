package com.andreaserick.languagecoast.data

import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.FakeTranslator
import com.andreaserick.languagecoast.testing.TEST_CLOCK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SuggestionPoolTest {

    private val flashcards = FakeFlashcardRepository()
    private val settings = FakeSettingsRepository()
    private val translator = FakeTranslator()
    private val pool = SuggestionPool(flashcards, settings, SentenceSuggester(translator, settings, StarterTranslator(translator)), TEST_CLOCK, CoroutineScope(Job()))

    @Before
    fun setUp() {
        flashcards.coasts.value = listOf(Coast(coastId = 1, language = "German", goal = "A trip to Berlin"))
        settings.translationProvider.value = TranslationProvider.Gemini
        settings.apiKey.value = "key"
    }

    /** The coast has had its basics from the bank, so the model is asked. */
    private fun pastTheBasics() {
        flashcards.coasts.value = flashcards.coasts.value.map { it.copy(bankOffered = BASICS_FROM_BANK) }
    }

    @Test
    fun theBasicsComeFromTheBankTranslatedByTheModel() = runTest {
        translator.completion = Completion("1 || Hallo, wie geht's?\n2 || Guten Morgen!\n3 || Guten Abend!")

        pool.topUp(1)

        val stored = flashcards.suggestions.value
        assertTrue(stored.isNotEmpty())
        assertTrue(translator.prompts.all { "phrasebook" in it })
        assertTrue(stored.all { PhraseBank.theme(it.category) != null })
        assertEquals(stored.size, flashcards.coasts.value.single().bankOffered)
    }

    @Test
    fun topUpAsksTheModelAndStoresWhatIsNew() = runTest {
        pastTheBasics()
        flashcards.seed(islandId = 1, cardCount = 1, coastId = 1)
        flashcards.cards.value = flashcards.cards.value.map { it.copy(nativeText = "Where is the station?", targetText = "Wo ist der Bahnhof?") }
        translator.completion = Completion(
            "Where is the station? || Wo ist der Bahnhof? || Travel || ✈️\n" +
                "Two beers, please. || Zwei Bier, bitte. || Restaurant || 🍺\n" +
                "I love this city. || Ich liebe diese Stadt. || Feelings || 💛"
        )

        pool.topUp(1)

        val stored = flashcards.suggestions.value
        assertEquals(listOf("Two beers, please.", "I love this city."), stored.map { it.nativeText })
        assertTrue(stored.all { it.coastId == 1 && !it.skipped })
        assertEquals("🍺", stored.first().emoji)
        val prompt = translator.prompts.single()
        assertTrue("A trip to Berlin" in prompt)
        assertTrue("Where is the station?" in prompt)
    }

    @Test
    fun topUpDoesNothingWhileEnoughSentencesAreReady() = runTest {
        pastTheBasics()
        flashcards.addSuggestions((1..DAILY_NEW_SENTENCES).map { SuggestedSentence(coastId = 1, nativeText = "n$it", targetText = "t$it", category = "C") })

        pool.topUp(1)

        assertTrue(translator.prompts.isEmpty())
    }

    @Test
    fun aFailedTopUpIsNotRetriedRightAway() = runTest {
        settings.translationProvider.value = TranslationProvider.OnDevice
        translator.result = TranslationResult.FAILURE

        pool.topUp(1)
        val attempts = translator.requests
        pool.topUp(1)

        assertTrue(attempts > 0)
        assertEquals(attempts, translator.requests)
        assertTrue(flashcards.suggestions.value.isEmpty())

        pool.forgetFailure(1)
        pool.topUp(1)
        assertTrue(translator.requests > attempts)
    }

    @Test
    fun withoutAModelThePhraseBankIsTranslatedOnTheDevice() = runTest {
        settings.translationProvider.value = TranslationProvider.OnDevice

        pool.topUp(1)

        val stored = flashcards.suggestions.value
        assertEquals(1, stored.size) // Every bank sentence gets the same canned translation, and repeats are dropped.
        assertEquals("Hola", stored.single().targetText)
        assertEquals("Airport", stored.single().category) // "A trip" points at the airport topic first.
        assertEquals(TranslationEngine.OnDevice, translator.lastRequest?.engine)
        assertTrue(translator.prompts.isEmpty())
        assertEquals(1, flashcards.coasts.value.single().bankOffered)
    }
}
