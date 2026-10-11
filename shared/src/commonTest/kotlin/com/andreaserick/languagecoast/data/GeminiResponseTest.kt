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
}
