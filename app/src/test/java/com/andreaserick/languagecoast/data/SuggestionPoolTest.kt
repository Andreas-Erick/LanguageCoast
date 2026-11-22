package com.andreaserick.languagecoast.data

import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.FakeTranslator
import com.andreaserick.languagecoast.testing.TEST_CLOCK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SuggestionPoolTest {

    private val flashcards = FakeFlashcardRepository()
    private val settings = FakeSettingsRepository()
    private val translator = FakeTranslator()
    // Without the shipped first sentences, so each test sees only what it is about; those have tests of their own below.
    private val pool = SuggestionPool(flashcards, settings, SentenceSuggester(translator, settings, StarterTranslator(translator)), TEST_CLOCK, CoroutineScope(Job())) { _, _ -> emptyList() }

    /** A pool that gives a brand-new coast the shipped first sentences, as the app's does. */
    private val shippingPool = SuggestionPool(flashcards, settings, SentenceSuggester(translator, settings, StarterTranslator(translator)), TEST_CLOCK, CoroutineScope(Job()))

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
    fun aTopUpThatBringsNothingSaysWhyAndCanBeTriedAgain() = runTest {
        val refusal = "Gemini didn't accept the API key. Check your key in Settings."
        translator.completion = Completion.failure(refusal)

        pool.topUp(1)

        assertEquals(mapOf(1 to refusal), pool.failures.value)
        assertTrue(flashcards.suggestions.value.isEmpty())
        // One batch of the bank and one request for suggestions: a provider that refuses isn't asked sentence by sentence.
        assertEquals(2, translator.prompts.size)
        assertEquals(0, translator.requests)

        // Trying again forgets the reason, and a provider that answers now fills the pool.
        translator.completion = Completion("1 || Hallo, wie geht's?\n2 || Guten Morgen!\n3 || Guten Abend!")
        pool.forgetFailure(1)
        assertTrue(pool.failures.value.isEmpty())
        pool.topUp(1)

        assertTrue(flashcards.suggestions.value.isNotEmpty())
        assertTrue(pool.failures.value.isEmpty())
    }

    @Test
    fun aNewProviderKeyOrPermissionForgetsEveryCoastsFailure() = runTest {
        settings.translationProvider.value = TranslationProvider.OnDevice
        translator.result = TranslationResult.FAILURE
        pool.topUp(1)
        val attempts = translator.requests
        assertTrue(pool.failures.value.isNotEmpty())

        // Not only the coast on screen: every stored reason is about the provider as it was.
        pool.forgetFailures()

        assertTrue(pool.failures.value.isEmpty())
        pool.topUp(1)
        assertTrue(translator.requests > attempts)
    }

    @Test
    fun aFailureWithoutAReasonStillSaysSomethingTheUserCanActOn() = runTest {
        settings.translationProvider.value = TranslationProvider.OnDevice
        translator.result = TranslationResult.FAILURE

        pool.topUp(1)

        assertEquals(mapOf(1 to "No sentences could be prepared. Check your connection and try again."), pool.failures.value)
    }

    @Test
    fun withoutPermissionNothingIsSentToTheCloudProvider() = runTest {
        // The key is saved, but the user hasn't allowed sending text to Gemini.
        settings.aiSharingAllowed.value = emptySet()

        pool.topUp(1)

        assertTrue(translator.prompts.isEmpty())
        // The phone's translator doesn't stand in either: the user is being asked, and the provider they picked gets the first turn.
        assertNull(translator.lastRequest)
        assertTrue(flashcards.suggestions.value.isEmpty())
        assertEquals(mapOf(1 to sharingNotAllowedMessage(TranslationProvider.Gemini)), pool.failures.value)

        // Once they allow it, Today forgets the failure and asks again, and the provider fills the pool.
        settings.allowAiSharing(TranslationProvider.Gemini)
        translator.completion = Completion("1 || Hallo, wie geht's?\n2 || Guten Morgen!\n3 || Guten Abend!")
        pool.forgetFailure(1)
        pool.topUp(1)

        assertTrue(translator.prompts.isNotEmpty())
        assertTrue(flashcards.suggestions.value.isNotEmpty())
        assertTrue(pool.failures.value.isEmpty())
    }

    @Test
    fun withoutAKeyTheBasicsAreStillTranslatedOnThePhone() = runTest {
        // No key at all, so there is nothing to ask permission for: the phone's translator stands in, as before.
        settings.apiKey.value = ""

        pool.topUp(1)

        assertTrue(translator.prompts.isEmpty())
        assertEquals(TranslationEngine.OnDevice, translator.lastRequest?.engine)
        assertTrue(flashcards.suggestions.value.isNotEmpty())
    }

    @Test
    fun aTopUpThatNeverComesBackIsGivenUpSoOtherCoastsAreNotKeptWaiting() = runTest {
        // A language pack that won't download, or a request that stalls.
        val stuck = object : Translator {
            override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult = awaitCancellation()
        }
        val stuckPool = SuggestionPool(flashcards, settings, SentenceSuggester(stuck, settings, StarterTranslator(stuck)), TEST_CLOCK, CoroutineScope(Job())) { _, _ -> emptyList() }
        settings.translationProvider.value = TranslationProvider.OnDevice

        stuckPool.topUp(1)

        assertEquals(mapOf(1 to "Preparing sentences took too long. Check your connection and try again."), stuckPool.failures.value)
        assertTrue(stuckPool.preparing.value.isEmpty())
        // The pool is free for the next coast: this returns instead of waiting for the first.
        flashcards.seedCoast(coastId = 2, language = "Spanish")
        flashcards.addSuggestions((1..DAILY_NEW_SENTENCES).map { SuggestedSentence(coastId = 2, nativeText = "n$it", targetText = "t$it", category = "C") })
        stuckPool.topUp(2)
        assertEquals(setOf(1), stuckPool.failures.value.keys)
    }

    @Test
    fun aTopUpThatTookTooLongIsNotStartedAgainRightAway() = runTest {
        // The attempt takes as long as the wait before a retry, so the wait has to count from when it gave up.
        var time = Instant.parse("2026-10-09T12:00:00Z")
        val clock = object : Clock {
            override fun now() = time
        }
        var attempts = 0
        val stuck = object : Translator {
            override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult {
                attempts++
                time += 10.minutes
                awaitCancellation()
            }
        }
        val stuckPool = SuggestionPool(flashcards, settings, SentenceSuggester(stuck, settings, StarterTranslator(stuck)), clock, CoroutineScope(Job())) { _, _ -> emptyList() }
        settings.translationProvider.value = TranslationProvider.OnDevice

        stuckPool.topUp(1)
        // Today comes back to the front right after.
        stuckPool.topUp(1)

        assertEquals(1, attempts)
        assertEquals(setOf(1), stuckPool.failures.value.keys)

        // Ten minutes after it gave up, it is tried again.
        time += 10.minutes
        stuckPool.topUp(1)
        assertEquals(2, attempts)
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

    @Test
    fun aNewCoastGetsTheShippedSentencesAtOnceEvenWhenTranslationFails() = runTest {
        settings.apiKey.value = ""
        translator.result = TranslationResult.FAILURE

        shippingPool.topUp(1)

        val stored = flashcards.suggestions.value
        assertEquals(StarterBundle.SENTENCES, stored.map { it.nativeText })
        assertEquals(listOf("Hallo, wie geht's?", "Vielen Dank.", "Ja, bitte."), stored.map { it.targetText })
        assertEquals(StarterBundle.SENTENCES.size, flashcards.coasts.value.single().bankOffered)
    }

    @Test
    fun theShippedSentencesAreOnlyForACoastWithNothingYet() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1, coastId = 1)
        settings.apiKey.value = ""
        translator.result = TranslationResult.FAILURE

        shippingPool.topUp(1)

        assertTrue(flashcards.suggestions.value.none { it.nativeText in StarterBundle.SENTENCES })
    }

    @Test
    fun aLanguageTheBundleDoesNotHaveWaitsForTranslation() = runTest {
        settings.nativeLanguage.value = "Albanian"
        settings.apiKey.value = ""
        translator.result = TranslationResult.FAILURE

        shippingPool.topUp(1)

        assertTrue(flashcards.suggestions.value.isEmpty())
    }
}
