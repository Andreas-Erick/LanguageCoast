package com.andreaserick.languagecoast.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterResponseTest {

    private fun completion(content: String) =
        """{"id":"gen-1","choices":[{"message":{"role":"assistant","content":${quote(content)}}}]}"""

    private fun quote(text: String) = "\"" + text.replace("\n", "\\n") + "\""

    @Test
    fun requestBodyHasTheModelAndPrompt() {
        assertEquals(
            """{"model":"openrouter/auto","messages":[{"role":"user","content":"Translate \"hi\""}]}""",
            openRouterRequestBody("openrouter/auto", "Translate \"hi\"")
        )
    }

    @Test
    fun answerIsParsedLikeGeminis() {
        val result = parseOpenRouterResponse(200, completion("TRANSLATION: Hallo\nCATEGORY: Greetings\nEMOJI: 👋"))

        assertTrue(result.isSuccess)
        assertEquals("Hallo", result.translatedText)
        assertEquals("Greetings", result.finalCategory)
        assertEquals("👋", result.emoji)
    }

    @Test
    fun badKeyAndMissingCreditsGetTheirOwnMessages() {
        val badKey = parseOpenRouterResponse(401, """{"error":{"code":401,"message":"No auth credentials found"}}""")
        val noCredits = parseOpenRouterResponse(402, """{"error":{"code":402,"message":"Insufficient credits"}}""")

        assertFalse(badKey.isSuccess)
        assertEquals("OpenRouter didn't accept the API key. Check it in Settings.", badKey.errorMessage)
        assertEquals("Your OpenRouter account is out of credits.", noCredits.errorMessage)
    }

    @Test
    fun otherErrorsShowOpenRoutersMessage() {
        val result = parseOpenRouterResponse(400, """{"error":{"code":400,"message":"not-a-model is not a valid model ID"}}""")

        assertEquals("OpenRouter: not-a-model is not a valid model ID", result.errorMessage)
    }

    @Test
    fun errorInsideASuccessfulResponseIsReported() {
        val result = parseOpenRouterResponse(200, """{"error":{"code":502,"message":"Provider returned error"}}""")

        assertFalse(result.isSuccess)
        assertEquals("OpenRouter: Provider returned error", result.errorMessage)
    }

    @Test
    fun unreadableAnswersFailWithAMessage() {
        assertEquals(
            "The model's answer couldn't be read. Try again or pick another model.",
            parseOpenRouterResponse(200, completion("Sure! Hallo means hello.")).errorMessage
        )
        assertEquals(
            "OpenRouter sent an empty answer. Try again or pick another model.",
            parseOpenRouterResponse(200, "not json").errorMessage
        )
    }
}
