package com.andreaserick.languagecoast.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderCapacityTest {

    @Test
    fun everyClaudeFiveModelThinksFirst() {
        listOf("claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5", "claude-fable-5-2", "claude-opus-5").forEach {
            assertEquals(ProviderCapacity.Thinking, ProviderCapacity.of(TranslationProvider.Claude, it), it)
        }
    }

    @Test
    fun claudeModelsBeforeFiveAnswerLikeOtherCloudModels() {
        listOf("claude-haiku-4-5", "claude-haiku-4-5-20251001", "claude-sonnet-4-6", "claude-3-7-sonnet-20250219", "CLAUDE-OPUS-4-8").forEach {
            assertEquals(ProviderCapacity.Cloud, ProviderCapacity.of(TranslationProvider.Claude, it), it)
        }
    }

    @Test
    fun unknownClaudeModelIsSizedAsThinking() {
        // The cautious choice: smaller batches and longer estimates rather than a request that runs out of time.
        assertEquals(ProviderCapacity.Thinking, ProviderCapacity.of(TranslationProvider.Claude))
        assertEquals(ProviderCapacity.Thinking, ProviderCapacity.of(TranslationProvider.Claude, "claude-next"))
    }

    @Test
    fun versionMatchDoesNotTripOnLaterNumbers() {
        assertFalse(isClaudeBeforeFive("claude-haiku-45"))
        assertFalse(isClaudeBeforeFive("claude-sonnet-14-0"))
        assertTrue(isClaudeBeforeFive("claude-sonnet-4"))
    }

    @Test
    fun otherProvidersIgnoreTheModel() {
        assertEquals(ProviderCapacity.Cloud, ProviderCapacity.of(TranslationProvider.Gemini, "claude-haiku-4-5"))
        assertEquals(ProviderCapacity.OnPhone, ProviderCapacity.of(TranslationProvider.LocalAi, "anything"))
    }
}
