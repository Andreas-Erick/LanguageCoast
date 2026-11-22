package com.andreaserick.languagecoast.data

import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.FakeTranslator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AskAiTest {

    private val settings = FakeSettingsRepository().apply { apiKey.value = "key" }
    private val translator = FakeTranslator()
    private val ai = AskAi(translator, settings)

    @Test
    fun anAnswerIsReadWithTheFeaturesParser() = runTest {
        translator.completion = Completion("VERDICT: fine")

        val reply = ai.ask("Check this.") { it.removePrefix("VERDICT: ").takeIf { v -> v != it } }

        assertEquals("fine", reply.value)
        assertNull(reply.error)
        assertEquals(listOf("Check this."), translator.prompts)
    }

    @Test
    fun aProviderThatIsNotSetUpOrOnlyTranslatesIsNotAsked() = runTest {
        settings.apiKey.value = ""
        assertEquals(missingKeyMessage(TranslationProvider.Gemini), ai.ask("Hi") { it }.error)

        settings.translationProvider.value = TranslationProvider.OnDevice
        assertEquals(ON_DEVICE_CANNOT_ANSWER, ai.ask("Hi") { it }.error)
        assertTrue(ai.connect() is AiConnection.Unavailable)

        assertTrue(translator.prompts.isEmpty())
    }

    @Test
    fun noAnswerAndAnUnreadableAnswerAreToldApart() = runTest {
        translator.completion = Completion(" \n``` ")
        assertEquals(noAnswer(TranslationProvider.Gemini), ai.ask("Hi") { it }.error)

        translator.completion = Completion("Sure! Here you go.")
        assertEquals(unreadable(TranslationProvider.Gemini), ai.ask("Hi") { null }.error)

        translator.completion = Completion.failure("No internet connection.")
        assertEquals("No internet connection.", ai.ask("Hi") { it }.error)
    }

    @Test
    fun aRequestThatWasTooMuchSaysSo() = runTest {
        translator.completion = Completion.tooLarge("Claude ran out of room.")

        val reply = ai.ask("Write fifty sentences.") { it }

        assertTrue(reply.isTooLarge)
        assertEquals("Claude ran out of room.", reply.error)
    }

    @Test
    fun aBusyProviderIsWaitedOutOnlyWhenAsked() = runTest {
        val answers = ArrayDeque(listOf(Completion.busy("Busy.", 20), Completion.busy("Busy.", 300), Completion("Hallo")))
        translator.completionFor = { answers.removeFirst() }
        val connection = ai.connect() as AiConnection.Ready

        val reply = connection.ask("Hi", waitWhileBusy = true) { it }

        assertEquals("Hallo", reply.value)
        // The wait the provider names, a minute at most.
        assertEquals(80_000L, testScheduler.currentTime)

        translator.completionFor = null
        translator.completion = Completion.busy("Busy.", 20)
        assertEquals("Busy.", connection.ask("Hi") { it }.error)
        assertFalse(translator.prompts.size > 4)
    }

    @Test
    fun aProviderThatStaysBusyIsGivenUpAfterFourWaits() = runTest {
        translator.completion = Completion.busy("Busy.", 10)

        val reply = (ai.connect() as AiConnection.Ready).ask("Hi", waitWhileBusy = true) { it }

        assertEquals("Busy.", reply.error)
        assertEquals(1 + BUSY_MAX_WAITS, translator.prompts.size)
    }
}
