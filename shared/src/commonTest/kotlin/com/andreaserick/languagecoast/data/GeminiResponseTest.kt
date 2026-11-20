package com.andreaserick.languagecoast.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeminiResponseTest {

    private fun response(vararg parts: String) =
        """{"candidates":[{"content":{"role":"model","parts":[${parts.joinToString(",")}]},"finishReason":"STOP"}]}"""

    private fun text(text: String) = "{\"text\":\"" + text.replace("\n", "\n") + "\"}"

    @Test
    fun requestBodyHasThePrompt() {
        assertEquals("""{"contents":[{"parts":[{"text":"Translate \"hi\""}]}]}""", geminiRequestBody("Translate \"hi\""))
    }

    @Test
    fun answerIsParsed() {
        val result = parseGeminiResponse(200, response(text("TRANSLATION: Hallo\nCATEGORY: Greetings\nEMOJI: 👋")))

        assertTrue(result.isSuccess)
        assertEquals("Hallo", result.translatedText)
        assertEquals("Greetings", result.finalCategory)
        assertEquals("👋", result.emoji)
    }

    @Test
    fun answerSplitAcrossPartsIsJoinedWithoutTheModelsThoughts() {
        val result = parseGeminiResponse(
            200,
            response("""{"text":"Thinking about greetings...","thought":true}""", text("TRANSLATION: Hallo\n"), text("CATEGORY: Greetings"))
        )

        assertEquals("Hallo", result.translatedText)
        assertEquals("Greetings", result.finalCategory)
    }

    @Test
    fun errorsAndEmptyAnswersFail() {
        val badKey = """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}"""
        assertFalse(parseGeminiResponse(400, badKey).isSuccess)
        assertFalse(parseGeminiResponse(200, """{"candidates":[]}""").isSuccess)
        assertFalse(parseGeminiResponse(200, "not json").isSuccess)
    }

    @Test
    fun streamedEventsIgnoreThoughtsEmitPartialLinesAndHandleMaxTokensAndTimeouts() {
        val partials = mutableListOf<String>()
        val stream = GeminiStream { partials += it }
        stream.read("""data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Planning 12 hard sentences...","thought":true}]}}]}""")
        stream.read("""data: {"candidates":[{"content":{"role":"model","parts":[{"text":"1 || Erster Satz || First sentence\n"}]}}]}""")
        stream.read("""data: {"candidates":[{"content":{"role":"model","parts":[{"text":"2 || Zweiter Satz || Second sentence"}]},"finishReason":"STOP"}]}""")

        val done = stream.completion()
        assertEquals("1 || Erster Satz || First sentence\n2 || Zweiter Satz || Second sentence", done.text)
        assertFalse(done.isTooLarge)
        assertTrue(partials.isNotEmpty())
        assertTrue(partials.first().startsWith("1 || Erster Satz"))

        // MAX_TOKENS after a complete first line keeps the whole line and drops the cut-off second line.
        val truncated = GeminiStream().apply {
            read("""data: {"candidates":[{"content":{"parts":[{"text":"1 || Ganz || Whole\n2 || Halb"}]},"finishReason":"MAX_TOKENS"}]}""")
        }.completion()
        assertEquals("1 || Ganz || Whole", truncated.text)

        // MAX_TOKENS spent entirely on thinking flags isTooLarge so WorksheetGenerator halves the batch.
        val exhausted = GeminiStream().apply {
            read("""data: {"candidates":[{"content":{"parts":[{"text":"Still thinking...","thought":true}]},"finishReason":"MAX_TOKENS"}]}""")
        }.completion()
        assertEquals(null, exhausted.text)
        assertTrue(exhausted.isTooLarge)

        // Gateway timeout (504 / DEADLINE_EXCEEDED) also flags isTooLarge.
        assertTrue(geminiAnswer(504, """{"error":{"code":504,"message":"Deadline exceeded","status":"DEADLINE_EXCEEDED"}}""").isTooLarge)
    }
}
