package com.andreaserick.languagecoast.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StarterTranslationTest {

    private val greetings = PhraseBank.theme("Greetings")!!

    @Test
    fun promptNumbersTheEntriesAndCarriesTheTopicsRules() {
        val prompt = buildStarterTranslationPrompt(PhraseBank.theme("Phone Settings")!!, "English", "German", null, listOf("Wi-Fi", "Turn off"))

        assertTrue("1. Wi-Fi" in prompt)
        assertTrue("2. Turn off" in prompt)
        assertTrue("system settings" in prompt)
        assertTrue("[number] || [the entry in German]" in prompt)
        assertFalse("the entry in English" in prompt)
    }

    @Test
    fun promptAsksForBothSidesWhenTheUserIsNotEnglishSpeaking() {
        val prompt = buildStarterTranslationPrompt(greetings, "Dutch", "German", null, listOf("Hello, how are you?"))

        assertTrue("[number] || [the entry in Dutch] || [the entry in German]" in prompt)
        assertTrue("front of the card" in prompt)
    }

    @Test
    fun promptsCarryTheRegionWhenOneIsGiven() {
        val translate = buildStarterTranslationPrompt(greetings, "English", "Spanish", "Mexico", listOf("Hello!"))
        val check = buildNaturalCheckPrompt(greetings, "English", "Spanish", "Mexico", listOf(ProposedSentence("Hello!", "¡Hola!", "Greetings")))
        val suggest = buildSuggestionPrompt("Spanish", "English", null, 3, emptyList(), emptyList(), 0, region = "Mexico")

        assertTrue("Spanish as spoken in Mexico" in translate)
        assertTrue("register of Mexico" in translate)
        assertTrue("native speaker of Spanish from Mexico" in check)
        assertTrue("1. Hello! => ¡Hola!" in check)
        assertTrue("use it in Mexico" in suggest)
        assertTrue("the places where it is spoken" in buildNaturalCheckPrompt(greetings, "English", "Spanish", null, emptyList()))
    }

    @Test
    fun naturalCheckParsesFixesAndIgnoresNone() {
        assertTrue(parseNaturalCheck("NONE", 3).isEmpty())
        val fixes = parseNaturalCheck("2 || ¿Qué onda? || In Mexico this is the casual greeting.\n3 || NONE || fine", 3)

        assertEquals(mapOf(2 to ("¿Qué onda?" to "In Mexico this is the casual greeting.")), fixes)
    }

    @Test
    fun everyTopicHasRulesAndTheDefaultAsksForPoliteAddress() {
        PhraseBank.THEMES.forEach { assertTrue(topicRules(it.category).isNotBlank(), it.category) }
        assertTrue("polite form" in topicRules("Restaurant"))
        assertTrue("digits" in topicRules("Numbers"))
    }

    @Test
    fun parsesNumberedLinesAndToleratesDecoration() {
        val parsed = parseStarterTranslations(
            """
            Here you go:
            1 || Hallo, wie geht's?
            2. || [Guten Morgen!]
            - 3) || **Guten Abend!**
            4 Guten Tag (no separator, dropped)
            5 || Schönen Tag noch!
            5 || repeated number, dropped
            7 || out of range, dropped
            """.trimIndent(),
            count = 6,
            bothSides = false
        )

        assertEquals(mapOf(1 to listOf("Hallo, wie geht's?"), 2 to listOf("Guten Morgen!"), 3 to listOf("Guten Abend!"), 5 to listOf("Schönen Tag noch!")), parsed)
    }

    @Test
    fun parsesBothSidesAndDropsLinesMissingOne() {
        val parsed = parseStarterTranslations("1 || Hallo, hoe gaat het? || Hallo, wie geht's?\n2 || Goedemorgen!", count = 2, bothSides = true)

        assertEquals(listOf("Hallo, hoe gaat het?", "Hallo, wie geht's?"), parsed[1])
        assertNull(parsed[2])
    }

    @Test
    fun parsingKeepsTheLastTwoSidesIfAModelAddsTheEnglishFirst() {
        val parsed = parseStarterTranslations("1 || Hello || Hallo || Hallo, wie geht's?", count = 1, bothSides = true)

        assertEquals(listOf("Hallo", "Hallo, wie geht's?"), parsed[1])
    }

    /** A [Translator] whose answers a test sets, and which counts what it was asked. */
    private class ScriptedTranslator(
        private val completion: (String) -> Completion = { Completion.failure("no answer") },
        private val translation: (TranslationRequest) -> TranslationResult = { TranslationResult.FAILURE }
    ) : Translator {
        val prompts = mutableListOf<String>()
        var requests = 0
            private set

        override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult {
            requests++
            return translation(request)
        }

        override suspend fun complete(engine: TranslationEngine, prompt: String): Completion {
            prompts += prompt
            return completion(prompt)
        }
    }

    private val gemini = TranslationEngine.Gemini("key", SettingsDefaults.GEMINI_MODEL)
    private val fortySentences = (1..40).map { "Sentence number $it of the test." }

    @Test
    fun aBatchTheProviderTurnsDownIsNotSentAgainSentenceBySentence() = runTest {
        val refusal = "Gemini didn't accept the API key. Check your key in Settings."
        val translator = ScriptedTranslator(completion = { Completion.failure(refusal) })
        val starter = StarterTranslator(translator)

        val translated = starter.translate(gemini, greetings, "English", "German", null, fortySentences)

        assertTrue(translated.isEmpty())
        // Not one of the forty went out on its own, and after two refused batches the third wasn't sent at all.
        assertEquals(0, translator.requests)
        assertEquals(StarterTranslator.GIVE_UP_AFTER, translator.prompts.size)
        assertEquals(refusal, starter.lastError)
    }

    @Test
    fun onlyTheLinesAnAnsweredBatchLeftOutAreAskedForOneByOne() = runTest {
        val translator = ScriptedTranslator(
            completion = { prompt -> if ("reviewing a learner's phrasebook" in prompt) Completion("NONE") else Completion("1 || Hallo!") },
            translation = { TranslationResult("einzeln übersetzt", "Greetings", isSuccess = true) }
        )
        val starter = StarterTranslator(translator)

        val translated = starter.translate(gemini, greetings, "English", "German", null, listOf("Hello!", "Good morning!", "Good night."))

        assertEquals(listOf("Hallo!", "einzeln übersetzt", "einzeln übersetzt"), translated.map { it.targetText })
        assertEquals(listOf("Hello!", "Good morning!", "Good night."), translated.map { it.nativeText })
        assertEquals(2, translator.requests)
        assertNull(starter.lastError)
    }

    @Test
    fun onTheDeviceTwoFailuresInARowEndTheCallAndGiveTheReason() = runTest {
        val reason = "The German language pack couldn't be downloaded. Check your internet connection and try again."
        val translator = ScriptedTranslator(translation = { TranslationResult.failure(reason) })
        val starter = StarterTranslator(translator)

        val translated = starter.translate(TranslationEngine.OnDevice, greetings, "English", "German", null, fortySentences)

        assertTrue(translated.isEmpty())
        // Each sentence would wait for the same missing language pack.
        assertEquals(StarterTranslator.GIVE_UP_AFTER, translator.requests)
        assertTrue(translator.prompts.isEmpty())
        assertEquals(reason, starter.lastError)
    }

    @Test
    fun aRateLimitIsWaitedOutAndTheBatchStillComesThrough() = runTest {
        var asked = 0
        val translator = ScriptedTranslator(
            completion = { prompt ->
                asked++
                when {
                    asked == 1 -> Completion.busy("Gemini rate limit or quota reached. Wait a moment or switch to another provider.", retryAfterSeconds = 20)
                    "reviewing a learner's phrasebook" in prompt -> Completion("NONE")
                    else -> Completion("1 || Hallo!\n2 || Guten Morgen!")
                }
            }
        )
        val starter = StarterTranslator(translator)

        val translated = starter.translate(gemini, greetings, "English", "German", null, listOf("Hello!", "Good morning!"))

        assertEquals(listOf("Hallo!", "Guten Morgen!"), translated.map { it.targetText })
        // The refused request, the same one again, and the natural-speech check.
        assertEquals(3, translator.prompts.size)
        assertEquals(translator.prompts[0], translator.prompts[1])
        assertNull(starter.lastError)
    }

    @Test
    fun aProviderThatStaysBusyIsAskedAFewTimesAndThenReported() = runTest {
        val busy = Completion.busy("Claude is busy or rate-limited. Wait a moment and try again.", retryAfterSeconds = 300)
        val translator = ScriptedTranslator(completion = { busy })

        val answer = translator.completeWaiting(TranslationEngine.Claude("key", SettingsDefaults.CLAUDE_MODEL), "prompt")

        assertEquals(busy, answer)
        assertEquals(1 + BUSY_MAX_WAITS, translator.prompts.size)
        // A failure that waiting can't fix is not waited on.
        val refused = ScriptedTranslator(completion = { Completion.failure("Claude didn't accept the API key. Check your Anthropic key in Settings.") })
        assertNull(refused.completeWaiting(TranslationEngine.Claude("key", SettingsDefaults.CLAUDE_MODEL), "prompt").text)
        assertEquals(1, refused.prompts.size)
    }
}
