package com.andreaserick.languagecoast.data

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

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
        assertEquals("Your OpenRouter account is out of credits. Add credits at openrouter.ai, or pick another provider in Settings.", noCredits.errorMessage)
    }

    // OpenRouter's own error text is English, technical and often long: it goes to the log, and the user is told what to do.
    @Test
    fun otherErrorsSayWhatToDoWithoutOpenRoutersOwnText() {
        val result = parseOpenRouterResponse(400, """{"error":{"code":400,"message":"not-a-model is not a valid model ID"}}""")

        assertEquals("OpenRouter couldn't answer. Try again, or pick another model in Settings.", result.errorMessage)
        assertEquals(
            "The selected OpenRouter model wasn't found. Pick another model in Settings.",
            parseOpenRouterResponse(404, """{"error":{"code":404,"message":"No endpoints found for x/y"}}""").errorMessage
        )
    }

    @Test
    fun errorInsideASuccessfulResponseIsReported() {
        val result = parseOpenRouterResponse(200, """{"error":{"code":502,"message":"Provider returned error"}}""")

        assertFalse(result.isSuccess)
        assertEquals("OpenRouter is having trouble right now. Try again in a moment.", result.errorMessage)
    }

    @Test
    fun noMessageShowsAStatusCodeOrTheProvidersOwnText() {
        val answers = listOf(
            openRouterAnswer(400, """{"error":{"code":400,"message":"Request failed with status 400: bad JSON"}}"""),
            openRouterAnswer(403, """{"error":{"code":403,"message":"Your input was flagged"}}"""),
            openRouterAnswer(500, "<html>500 Internal Server Error</html>"),
            cloudChatAnswer("Groq", 400, """{"error":{"message":"'messages' : minimum number of items is 1","type":"invalid_request_error"}}"""),
            cloudChatAnswer("OpenAI", 403, """{"error":{"message":"Country, region, or territory not supported"}}"""),
            cloudChatAnswer("DeepSeek", 503, "upstream connect error"),
            claudeAnswer(400, """{"type":"error","error":{"type":"invalid_request_error","message":"max_tokens: Field required"}}"""),
            claudeAnswer(500, "{}"),
            geminiAnswer(400, """{"error":{"code":400,"message":"Invalid JSON payload received.","status":"INVALID_ARGUMENT"}}"""),
            geminiAnswer(503, """{"error":{"code":503,"message":"The model is overloaded.","status":"UNAVAILABLE"}}""")
        )

        answers.forEach { answer ->
            val message = answer.errorMessage.orEmpty()
            assertTrue(message.isNotEmpty())
            assertFalse(Regex("\\d{3}").containsMatchIn(message), message)
            assertFalse("HTTP" in message, message)
            assertFalse("max_tokens" in message || "JSON" in message || "flagged" in message || "territory" in message, message)
            // Every one says what to do next.
            assertTrue("Try again" in message || "Settings" in message, message)
        }
    }

    @Test
    fun anAccountWithoutCreditIsToldApartFromOtherRefusals() {
        val lowBalance = """{"type":"error","error":{"type":"invalid_request_error","message":"Your credit balance is too low to access the Anthropic API."}}"""
        assertEquals(
            "Your Claude account is out of credit or over its quota. Check your plan with Claude, or pick another provider in Settings.",
            claudeAnswer(400, lowBalance).errorMessage
        )
        assertTrue("out of credit" in cloudChatAnswer("DeepSeek", 402, """{"error":{"message":"Insufficient Balance"}}""").errorMessage.orEmpty())
        // OpenAI answers 429 for an empty account too, which no wait fixes.
        val noQuota = cloudChatAnswer("OpenAI", 429, """{"error":{"message":"You exceeded your current quota.","type":"insufficient_quota"}}""")
        assertTrue("out of credit" in noQuota.errorMessage.orEmpty())
        assertEquals(null, noQuota.retryAfterSeconds)
    }

    @Test
    fun aRateLimitSaysHowLongToWaitForEveryProvider() {
        val limited = """{"error":{"code":429,"message":"Rate limit exceeded"}}"""

        // The header's seconds, rounded up; without one, a short wait of our own.
        assertEquals(7, openRouterAnswer(429, limited, retryAfter = "6.2").retryAfterSeconds)
        assertEquals(BUSY_RETRY_SECONDS, openRouterAnswer(429, limited).retryAfterSeconds)
        // OpenRouter also reports a rate limit inside an answer that arrived fine.
        assertEquals(BUSY_RETRY_SECONDS, openRouterAnswer(200, limited).retryAfterSeconds)
        assertEquals(30, cloudChatAnswer("Groq", 429, """{"error":{"message":"Rate limit reached","type":"tokens"}}""", retryAfter = "30").retryAfterSeconds)
        assertEquals(BUSY_RETRY_SECONDS, cloudChatAnswer("OpenAI", 429, "").retryAfterSeconds)
        assertEquals(BUSY_RETRY_SECONDS, geminiAnswer(429, """{"error":{"code":429,"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED"}}""").retryAfterSeconds)
        assertEquals(1, geminiAnswer(429, "{}", retryAfter = "0").retryAfterSeconds)
        // The message stays one the user can act on if the wait didn't help.
        assertEquals("OpenRouter is busy. Wait a moment and try again.", openRouterAnswer(429, limited).errorMessage)

        // Waiting doesn't help with a wrong key, and an answer needs no wait.
        assertEquals(null, openRouterAnswer(401, """{"error":{"code":401,"message":"No auth"}}""", retryAfter = "5").retryAfterSeconds)
        assertEquals(null, cloudChatAnswer("OpenAI", 401, "{}", retryAfter = "5").retryAfterSeconds)
        assertEquals(null, geminiAnswer(404, "{}").retryAfterSeconds)
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
