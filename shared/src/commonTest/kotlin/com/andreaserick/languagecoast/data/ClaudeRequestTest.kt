package com.andreaserick.languagecoast.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaudeRequestTest {

    @Test
    fun requestBodyCarriesTheRequiredMaxTokensAndAsksForAStream() {
        val body = claudeRequestBody("claude-sonnet-4-5", "Translate this.")

        // Anthropic's Messages API rejects a request without max_tokens; the field must never be left out.
        assertTrue("\"max_tokens\":$CLAUDE_MAX_TOKENS" in body, body)
        // And without "stream" the answer comes whole, which a model that thinks for minutes can't deliver in time.
        assertTrue("\"stream\":true" in body, body)
        assertTrue("\"model\":\"claude-sonnet-4-5\"" in body)
        assertTrue("\"role\":\"user\"" in body)
        assertTrue("Translate this." in body)
    }

    @Test
    fun maxTokensHasRoomForABatchOfSentencesAndForThinkingFirst() {
        // The Claude 5 models think before they answer, and that counts against max_tokens: a dozen hard
        // practice-sheet sentences took a top model about 8000 tokens.
        assertEquals(32_000, CLAUDE_MAX_TOKENS)
        // Every Claude model on offer can write at least this much (the smallest, Haiku 4.5, 64 000); more would be refused.
        assertTrue(CLAUDE_MAX_TOKENS <= 64_000)
    }

    @Test
    fun aStreamedAnswerHasMoreTimeThanAnyOtherRequestAndMayBeSilentWhileTheModelThinks() {
        // The client gives every request two minutes; an answer that is read as it comes gets a backstop instead.
        assertTrue(Patience.Batch.totalMillis >= 5 * 60_000L)
        assertTrue(Patience.Batch.silenceMillis in 2 * 60_000L..Patience.Batch.totalMillis)
        // One sentence's translation keeps the two minutes: the screen that waits for it has no way out.
        assertEquals(120_000L, Patience.Sentence.totalMillis)
        assertTrue(Patience.Sentence.silenceMillis in Patience.Sentence.totalMillis..130_000L)
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

    /** A streamed answer as [lines] of the event stream give it. */
    private fun streamed(vararg lines: String): Completion = ClaudeStream().apply { lines.forEach { read(it) } }.completion()

    private fun text(piece: String) = """data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"$piece"}}"""

    private fun stopped(reason: String) = """data: {"type":"message_delta","delta":{"stop_reason":"$reason","stop_sequence":null},"usage":{"output_tokens":12}}"""

    @Test
    fun aStreamedAnswerIsItsPiecesOfTextInOrder() {
        val answer = streamed(
            "event: message_start",
            """data: {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","content":[]}}""",
            "",
            "event: ping",
            """data: {"type":"ping"}""",
            // A model that thinks first sends a thinking block, empty to the reader, before the text.
            """data: {"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}""",
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"not for the sheet"}}""",
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"EqQB"}}""",
            """data: {"type":"content_block_stop","index":0}""",
            """data: {"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}""",
            text("1 || Wo ist "),
            text("der Bahnhof? || Where is the station?\\n"),
            "data: this is no event at all",
            ": a comment line",
            text("2 || Guten Morgen! || Good morning!"),
            """data: {"type":"content_block_stop","index":1}""",
            stopped("end_turn"),
            """data: {"type":"message_stop"}"""
        )

        assertEquals("1 || Wo ist der Bahnhof? || Where is the station?\n2 || Guten Morgen! || Good morning!", answer.text)
        assertFalse(answer.isTooLarge)
    }

    @Test
    fun anAnswerThatRanOutOfRoomKeepsItsWholeLinesAndSaysSoWhenThereAreNone() {
        // Cut off in the middle of the second line: a half translation must not get onto a sheet.
        val partly = streamed(text("1 || Wo ist der Bahnhof? || Where is the station?\\n2 || Guten Morgen! || Good mor"), stopped("max_tokens"))
        assertEquals("1 || Wo ist der Bahnhof? || Where is the station?", partly.text)

        // All the room went on thinking: nothing was written. Asking for less can go through; asking again can't.
        val nothing = streamed(stopped("max_tokens"))
        assertNull(nothing.text)
        assertTrue(nothing.isTooLarge)
        assertTrue("ran out of room" in nothing.errorMessage.orEmpty())
        // A single line that was cut off is no answer either.
        assertTrue(streamed(text("Guten Mor"), stopped("max_tokens")).isTooLarge)

        // A stream that ends without saying why broke off on the way: its last line may be half a line too.
        assertEquals("1 || Wo ist der Bahnhof? || Where is the station?", streamed(text("1 || Wo ist der Bahnhof? || Where is the station?\\n2 || Guten Mor")).text)
        val gone = streamed(text("1 || Wo ist"))
        assertEquals(null to false, gone.text to gone.isTooLarge)
        assertTrue("broke off" in gone.errorMessage.orEmpty())

        // The same when the answer came whole instead of as a stream.
        assertTrue(claudeAnswer(200, """{"content":[{"type":"thinking","thinking":""}],"stop_reason":"max_tokens"}""").isTooLarge)
        assertEquals("a", claudeAnswer(200, """{"content":[{"type":"text","text":"a\nb"}],"stop_reason":"max_tokens"}""").text)
    }

    @Test
    fun anAnswerTheModelDeclinedOrBrokeOffIsAFailureWithItsReason() {
        val declined = streamed(text("I can't"), stopped("refusal"))
        assertNull(declined.text)
        // Asking again in the same words would be declined again.
        assertEquals("Claude declined to answer this. Pick another model in Settings, or change what you asked for and try again.", declined.errorMessage)
        assertFalse(declined.isTooLarge)

        // Overloaded in mid-answer: worth sending again in a moment, whatever had arrived.
        val overloaded = streamed(text("1 || Wo"), """data: {"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""")
        assertNull(overloaded.text)
        assertEquals(CLAUDE_RETRY_SECONDS, overloaded.retryAfterSeconds)

        // Anthropic's own time limit, in mid-answer or as an error status: less at a time can go through.
        assertTrue(streamed("""data: {"type":"error","error":{"type":"timeout_error","message":"The request timed out while processing."}}""").isTooLarge)
        assertTrue(claudeAnswer(504, """{"type":"error","error":{"type":"timeout_error","message":"The request timed out while processing."}}""").isTooLarge)
        assertFalse(claudeAnswer(500, """{"type":"error","error":{"type":"api_error","message":"Internal server error"}}""").isTooLarge)

        // Any other error in mid-answer is a plain failure, and so is an answer with nothing in it.
        val broken = streamed("""data: {"type":"error","error":{"type":"api_error","message":"Internal server error"}}""")
        assertNull(broken.text)
        assertNull(broken.retryAfterSeconds)
        assertEquals("Claude returned an empty answer. Try again or pick another model.", streamed(stopped("end_turn")).errorMessage)
    }
}
