package com.andreaserick.languagecoast.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClaudeRequestTest {

    @Test
    fun requestBodyCarriesTheRequiredMaxTokens() {
        val body = claudeRequestBody("claude-sonnet-4-5", "Translate this.")

        // Anthropic's Messages API rejects a request without max_tokens; the field must never be left out.
        assertTrue("\"max_tokens\":$CLAUDE_MAX_TOKENS" in body, body)
        assertTrue("\"model\":\"claude-sonnet-4-5\"" in body)
        assertTrue("\"role\":\"user\"" in body)
        assertTrue("Translate this." in body)
    }

    @Test
    fun maxTokensHasRoomForABatchOfTranslationsAndForThinkingFirst() {
        // Newer Claude models think before they answer, and that counts against max_tokens.
        assertEquals(16_000, CLAUDE_MAX_TOKENS)
        // Every Claude model on offer can write at least this much; a larger number would be refused.
        assertTrue(CLAUDE_MAX_TOKENS <= 32_000)
    }

    @Test
    fun aRateLimitedOrOverloadedAnswerSaysHowLongToWait() {
        val rateLimited = """{"type":"error","error":{"type":"rate_limit_error","message":"This request would exceed your rate limit."}}"""

        // The header's seconds, rounded up; a wait is never less than a second.
        assertEquals(23, claudeAnswer(429, rateLimited, retryAfter = "23").retryAfterSeconds)
        assertEquals(3, claudeAnswer(429, rateLimited, retryAfter = " 2.2 ").retryAfterSeconds)
        assertEquals(1, claudeAnswer(429, rateLimited, retryAfter = "0").retryAfterSeconds)
        // Without the header, or with one that isn't a number, a short wait of our own.
        assertEquals(CLAUDE_RETRY_SECONDS, claudeAnswer(429, rateLimited).retryAfterSeconds)
        assertEquals(CLAUDE_RETRY_SECONDS, claudeAnswer(529, "overloaded", retryAfter = "soon").retryAfterSeconds)
        assertTrue("rate-limited" in claudeAnswer(429, rateLimited, retryAfter = "23").errorMessage.orEmpty())

        // Waiting doesn't help with a wrong key or a model that is gone, and an answer needs no wait.
        assertEquals(null, claudeAnswer(401, "{}", retryAfter = "5").retryAfterSeconds)
        assertEquals(null, claudeAnswer(404, "{}").retryAfterSeconds)
        val answer = claudeAnswer(200, """{"content":[{"type":"text","text":"Hallo"}]}""")
        assertEquals("Hallo" to null, answer.text to answer.retryAfterSeconds)
    }
}
