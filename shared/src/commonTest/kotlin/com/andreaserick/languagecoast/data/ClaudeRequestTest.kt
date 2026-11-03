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
    fun maxTokensHasRoomForABatchOfTranslations() {
        assertTrue(CLAUDE_MAX_TOKENS >= 1500)
        assertEquals(4096, CLAUDE_MAX_TOKENS)
    }
}
