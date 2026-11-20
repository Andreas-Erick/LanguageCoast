package com.andreaserick.languagecoast.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [claudeCompletion] against a server on this machine that answers the way Anthropic's does: over a real
 * connection, with the networking the app uses on Android and the time limits of the app's own client.
 * What a unit test of the parsing can't show is here: that an answer is read while it is still being
 * written, and what a time limit does to one.
 */
class ClaudeStreamingTest {

    private lateinit var server: HttpServer
    private lateinit var client: HttpClient
    private var answer: (HttpExchange) -> Unit = {}
    private var requestBody = ""
    private var requestKey: String? = null
    private val endpoint get() = "http://127.0.0.1:${server.address.port}/v1/messages"

    @BeforeTest
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/v1/messages") { exchange ->
            requestBody = exchange.requestBody.readBytes().decodeToString()
            requestKey = exchange.requestHeaders.getFirst("x-api-key")
            // A client that has given up closes the connection; writing to it then fails, which is fine.
            runCatching { answer(exchange) }
            runCatching { exchange.close() }
        }
        server.createContext("/v1beta/models/") { exchange ->
            requestBody = exchange.requestBody.readBytes().decodeToString()
            requestKey = exchange.requestHeaders.getFirst("x-goog-api-key")
            runCatching { answer(exchange) }
            runCatching { exchange.close() }
        }
        server.start()
        // The limits sharedModule gives every request: two minutes, which a streamed answer must not be held to.
        client = HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 120_000
                socketTimeoutMillis = 125_000
            }
        }
    }

    @AfterTest
    fun tearDown() {
        client.close()
        server.stop(0)
    }

    private fun json(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun textDelta(text: String) = """{"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"${json(text)}"}}"""

    private val ping = """{"type":"ping"}"""

    private fun stopped(reason: String) = """{"type":"message_delta","delta":{"stop_reason":"$reason","stop_sequence":null},"usage":{"output_tokens":7000}}"""

    /** Answers with a stream of [events], each sent after the pause before it (milliseconds). */
    private fun HttpExchange.stream(vararg events: Pair<Long, String>) {
        responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8")
        sendResponseHeaders(200, 0)
        for ((pause, data) in events) {
            Thread.sleep(pause)
            val name = Regex("\"type\":\"([a-z_]+)\"").find(data)?.groupValues?.get(1) ?: "message"
            responseBody.write("event: $name\ndata: $data\n\n".toByteArray())
            responseBody.flush()
        }
    }

    private fun HttpExchange.send(status: Int, body: String, vararg headers: Pair<String, String>) {
        responseHeaders.add("Content-Type", "application/json")
        headers.forEach { responseHeaders.add(it.first, it.second) }
        val bytes = body.toByteArray()
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.write(bytes)
    }

    private fun ask(requestTimeoutMillis: Long = 20_000, silenceMillis: Long = 10_000): Completion = runBlocking {
        client.claudeCompletion("sk-test", "claude-fable-5-2", "Write two sentences.", endpoint, requestTimeoutMillis, silenceMillis)
    }

    @Test
    fun anAnswerIsPutTogetherFromTheStreamAsAModelThatThinksFirstSendsIt() {
        answer = {
            it.stream(
                0L to """{"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","model":"claude-fable-5-2","content":[],"stop_reason":null,"usage":{"input_tokens":900,"output_tokens":1}}}""",
                0L to ping,
                // The thinking comes first, and with nothing to show for it.
                0L to """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}""",
                0L to """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":""}}""",
                0L to """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"EqQBCgIYAhIM"}}""",
                0L to """{"type":"content_block_stop","index":0}""",
                0L to """{"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}""",
                0L to textDelta("1 || Wenn es nicht weit ist, gehe ich"),
                0L to textDelta(" lieber zu Fuß. || If it isn't far, I'd rather walk. || lieber = rather\n2 || Wir essen"),
                0L to textDelta(" draußen, weil das Wetter \"herrlich\" ist. || We're eating outside because the weather is \"lovely\". || -"),
                0L to """{"type":"content_block_stop","index":1}""",
                0L to stopped("end_turn"),
                0L to """{"type":"message_stop"}"""
            )
        }

        val completion = ask()

        assertEquals(
            "1 || Wenn es nicht weit ist, gehe ich lieber zu Fuß. || If it isn't far, I'd rather walk. || lieber = rather\n" +
                "2 || Wir essen draußen, weil das Wetter \"herrlich\" ist. || We're eating outside because the weather is \"lovely\". || -",
            completion.text
        )
        // What was sent: the key, and a request for a stream with room for the thinking and the answer.
        assertEquals("sk-test", requestKey)
        assertTrue("\"stream\":true" in requestBody, requestBody)
        assertTrue("\"max_tokens\":$CLAUDE_MAX_TOKENS" in requestBody, requestBody)
        assertTrue("\"model\":\"claude-fable-5-2\"" in requestBody, requestBody)
    }

    @Test
    fun anAnswerThatKeepsComingIsNotHeldToHowLongItMayBeSilent() {
        // Three seconds in all, never more than half a second without a sign of life: silence is limited to
        // two and a half seconds, and the answer still arrives. This is a model thinking for minutes, in small.
        answer = {
            it.stream(
                0L to """{"type":"message_start","message":{"id":"msg_1","content":[]}}""",
                500L to ping, 500L to ping, 500L to ping, 500L to ping, 500L to ping,
                500L to textDelta("1 || Guten Morgen! || Good morning!"),
                0L to stopped("end_turn")
            )
        }

        val completion = ask(requestTimeoutMillis = 20_000, silenceMillis = 2_500)

        assertEquals("1 || Guten Morgen! || Good morning!", completion.text)
    }

    @Test
    fun anAnswerThatTakesLongerThanItMayInAllIsGivenUpAndSaidToBeTooMuch() {
        answer = {
            it.stream(
                0L to """{"type":"message_start","message":{"id":"msg_1","content":[]}}""",
                400L to ping, 400L to ping, 400L to ping, 400L to ping, 400L to ping, 400L to ping, 400L to ping, 400L to ping,
                0L to textDelta("1 || Guten Morgen! || Good morning!"),
                0L to stopped("end_turn")
            )
        }

        val completion = ask(requestTimeoutMillis = 1_200, silenceMillis = 10_000)

        // Not an exception and not a cancellation of whoever asked: an answer that says a smaller request may do.
        assertNull(completion.text)
        assertTrue(completion.isTooLarge)
        assertEquals(tookTooLongMessage("Claude"), completion.errorMessage)
    }

    @Test
    fun aServerThatGoesSilentIsGivenUpToo() {
        answer = {
            it.stream(
                0L to """{"type":"message_start","message":{"id":"msg_1","content":[]}}""",
                8_000L to textDelta("1 || Guten Morgen! || Good morning!")
            )
        }

        val completion = ask(requestTimeoutMillis = 20_000, silenceMillis = 3_000)

        assertNull(completion.text)
        assertTrue(completion.isTooLarge)
        assertEquals(tookTooLongMessage("Claude"), completion.errorMessage)
    }

    @Test
    fun aServerThatNeverBeginsToAnswerWasNotReachedAndIsNotAskedForLess() {
        // The connection is made and then nothing comes, not even the first line of the answer: on a phone
        // this is what a network that swallows the request looks like. It is no model that needs more time.
        answer = { Thread.sleep(6_000) }

        val completion = ask(requestTimeoutMillis = 20_000, silenceMillis = 2_000)

        assertNull(completion.text)
        assertFalse(completion.isTooLarge)
        assertEquals("Couldn't reach Claude. Check your internet connection and try again.", completion.errorMessage)
    }

    @Test
    fun whoeverAskedCanStillCancel() = runBlocking {
        answer = {
            it.stream(
                0L to """{"type":"message_start","message":{"id":"msg_1","content":[]}}""",
                5_000L to textDelta("1 || Guten Morgen! || Good morning!")
            )
        }

        val asking = async { client.claudeCompletion("sk-test", "claude-fable-5-2", "Write.", endpoint, 20_000, 10_000) }
        delay(500)
        asking.cancel()

        // The cancellation is passed on, not turned into an answer.
        val outcome = runCatching { asking.await() }
        assertTrue(outcome.exceptionOrNull() is CancellationException, outcome.exceptionOrNull().toString())
    }

    @Test
    fun anErrorComesAsOrdinaryJsonAndIsReadAsOne() {
        answer = { it.send(401, """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""") }
        assertEquals("Claude didn't accept the API key. Check your Anthropic key in Settings.", ask().errorMessage)

        answer = { it.send(404, """{"type":"error","error":{"type":"not_found_error","message":"model: claude-fable-9"}}""") }
        assertEquals("The selected Claude model wasn't found. Pick another model in Settings.", ask().errorMessage)

        answer = { it.send(429, """{"type":"error","error":{"type":"rate_limit_error","message":"Rate limited."}}""", "retry-after" to "7") }
        val busy = ask()
        assertEquals(7, busy.retryAfterSeconds)
        assertFalse(busy.isTooLarge)
    }

    @Test
    fun anAnswerSentWholeIsStillRead() {
        // Not what the API does when asked for a stream, but nothing is lost by reading it.
        answer = { it.send(200, """{"content":[{"type":"thinking","thinking":""},{"type":"text","text":"Hallo"}],"stop_reason":"end_turn"}""") }

        assertEquals("Hallo", ask().text)
    }

    @Test
    fun anAnswerThatBreaksOffInMidStreamSaysWhy() {
        answer = {
            it.stream(
                0L to """{"type":"message_start","message":{"id":"msg_1","content":[]}}""",
                0L to """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""
            )
        }

        // Overloaded: the same request can be sent again in a moment.
        assertEquals(CLAUDE_RETRY_SECONDS, ask().retryAfterSeconds)
    }

    @Test
    fun partialLinesAreDeliveredLiveWhileClaudeStreams() = runBlocking {
        answer = {
            it.stream(
                0L to """{"type":"message_start","message":{"id":"msg_1","content":[]}}""",
                0L to """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
                0L to textDelta("1 || Erster Satz || First\n"),
                0L to textDelta("2 || Zweiter Satz || Second\n"),
                0L to """{"type":"content_block_stop","index":0}""",
                0L to stopped("end_turn")
            )
        }
        val partials = mutableListOf<String>()
        val completion = client.claudeCompletion("sk-test", "claude-fable-5-2", "Write.", endpoint, 20_000, 10_000) {
            partials += it
        }

        assertEquals("1 || Erster Satz || First\n2 || Zweiter Satz || Second", completion.text)
        assertTrue(partials.size >= 2, partials.toString())
        assertTrue(partials.first().startsWith("1 || Erster Satz"))
    }
}
