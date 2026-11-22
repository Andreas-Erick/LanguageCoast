package com.andreaserick.languagecoast.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The [CloudTransport] over a real connection, for what every provider shares: requests sent again after a
 * failure of the moment, a second way of asking when the first is turned down, and the OpenAI-style stream that
 * OpenAI, Groq, DeepSeek and OpenRouter send. Claude's own stream is in [ClaudeStreamingTest].
 */
class CloudTransportTest {

    private lateinit var server: HttpServer
    private lateinit var client: HttpClient
    private val transport get() = CloudTransport(client)

    /** Each request's path and body, in order. */
    private val requests: MutableList<Pair<String, String>> = Collections.synchronizedList(mutableListOf())

    /** Answers the request with this index; the last one answers any later requests too. */
    private var answers: List<(HttpExchange) -> Unit> = emptyList()

    private val base get() = "http://127.0.0.1:${server.address.port}"

    @BeforeTest
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/") { exchange ->
            val index = synchronized(requests) {
                requests += exchange.requestURI.toString() to exchange.requestBody.readBytes().decodeToString()
                requests.size - 1
            }
            runCatching { answers[index.coerceAtMost(answers.size - 1)](exchange) }
            runCatching { exchange.close() }
        }
        server.start()
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

    private fun HttpExchange.stream(vararg events: String) {
        responseHeaders.add("Content-Type", "text/event-stream")
        sendResponseHeaders(200, 0)
        events.forEach { responseBody.write("data: $it\n\n".toByteArray()); responseBody.flush() }
    }

    private fun HttpExchange.send(status: Int, body: String) {
        responseHeaders.add("Content-Type", "application/json")
        val bytes = body.toByteArray()
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.write(bytes)
    }

    private fun json(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun delta(text: String) = """{"choices":[{"index":0,"delta":{"content":"${json(text)}"},"finish_reason":null}]}"""

    private fun finished(reason: String) = """{"choices":[{"index":0,"delta":{},"finish_reason":"$reason"}]}"""

    private fun ask(wire: CloudWire, onPartialText: ((String) -> Unit)? = null) =
        runBlocking { transport.ask(wire, "Write two sentences.", Patience(20_000, 10_000), onPartialText) }

    @Test
    fun anOpenAiStyleStreamIsReadAsItComesWithoutTheModelsThinking() {
        answers = listOf({
            it.stream(
                delta("<think>Two sentences,\nshort ones"),
                delta(".</think>1 || Guten Morgen! || Good morning!\n"),
                delta("2 || Gute Nacht! || Good night!"),
                finished("stop"),
                "[DONE]"
            )
        })
        val partials = mutableListOf<String>()

        val answer = ask(ChatWire.groq("gsk-test", "qwen3", "$base/v1/chat/completions"), onPartialText = { partials += it })

        assertEquals("1 || Guten Morgen! || Good morning!\n2 || Gute Nacht! || Good night!", answer.text)
        // What was shown while it came never had the thinking in it.
        assertTrue(partials.isNotEmpty())
        assertTrue(partials.none { "Two sentences" in it }, partials.toString())
        assertTrue("\"stream\":true" in requests.single().second)
        assertTrue("\"model\":\"qwen3\"" in requests.single().second)
    }

    @Test
    fun anOpenAiStyleAnswerThatRanOutOfRoomKeepsItsWholeLines() {
        answers = listOf({ it.stream(delta("1 || Guten Morgen! || Good morning!\n2 || Gute Na"), finished("length")) })

        assertEquals("1 || Guten Morgen! || Good morning!", ask(ChatWire.openAi("sk-test", "gpt-5", "$base/v1/chat/completions")).text)

        answers = listOf({ it.stream(delta("1 || Gute"), finished("length")) })
        assertTrue(ask(ChatWire.openAi("sk-test", "gpt-5", "$base/v1/chat/completions")).isTooLarge)
    }

    @Test
    fun anErrorInMidStreamIsReadTheProvidersWay() {
        answers = listOf({ it.stream(delta("1 || Gu"), """{"error":{"code":402,"message":"Insufficient credits"}}""") })

        val answer = ask(ChatWire.openRouter("sk-or", "openrouter/auto", "$base/api/v1/chat/completions"))

        assertNull(answer.text)
        assertTrue("out of credits" in answer.errorMessage.orEmpty(), answer.errorMessage)
    }

    @Test
    fun aModelThatMayNotBeStreamedIsAskedForItsAnswerWhole() {
        answers = listOf(
            { it.send(400, """{"error":{"message":"Your organization must be verified to stream this model.","type":"invalid_request_error","param":"stream"}}""") },
            { it.send(200, """{"choices":[{"message":{"role":"assistant","content":"Hallo"},"finish_reason":"stop"}]}""") }
        )

        val answer = ask(ChatWire.openAi("sk-test", "o3", "$base/v1/chat/completions"))

        assertEquals("Hallo", answer.text)
        assertTrue("\"stream\":true" in requests[0].second)
        assertTrue("\"stream\":false" in requests[1].second)
    }

    @Test
    fun anOverloadedProviderIsAskedAgainAtOnceEveryProviderAlike() {
        val overloaded: (HttpExchange) -> Unit = { it.send(529, """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""") }
        answers = listOf(
            overloaded,
            {
                it.stream(
                    """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hallo"}}""",
                    """{"type":"message_delta","delta":{"stop_reason":"end_turn"}}"""
                )
            }
        )

        // Claude had no retry of its own before the transport was shared.
        assertEquals("Hallo", ask(ClaudeWire("sk-test", "claude-fable-5-2", "$base/v1/messages")).text)
        assertEquals(2, requests.size)
    }

    @Test
    fun aRateLimitThatTakesLongerIsLeftToTheCallerToWaitOut() {
        answers = listOf({
            it.responseHeaders.add("retry-after", "30")
            it.send(429, """{"error":{"message":"Rate limit reached","type":"tokens"}}""")
        })

        val answer = ask(ChatWire.deepSeek("sk-test", "deepseek-chat", "$base/chat/completions"))

        assertEquals(30, answer.retryAfterSeconds)
        assertEquals(1, requests.size)
    }

    @Test
    fun aRetiredGeminiModelIsAskedAgainWithTheOneGoogleNames() {
        answers = listOf(
            { it.send(404, """{"error":{"code":404,"message":"models/gemini-x is not found. Please update your code to use models/gemini-9-flash for the latest features.","status":"NOT_FOUND"}}""") },
            {
                it.stream(
                    """{"candidates":[{"content":{"parts":[{"text":"thinking","thought":true},{"text":"Hallo"}]},"finishReason":"STOP"}]}"""
                )
            }
        )

        val answer = ask(GeminiWire("g-key", "gemini-x", "$base/v1beta/models"))

        assertEquals("Hallo", answer.text)
        assertTrue(requests[0].first.startsWith("/v1beta/models/gemini-x:streamGenerateContent"), requests[0].first)
        assertTrue(requests[1].first.startsWith("/v1beta/models/gemini-9-flash:streamGenerateContent"), requests[1].first)
    }

    @Test
    fun aProviderWithoutAKeyIsNotAskedAtAll() = runBlocking {
        val translator = CloudTranslator(client)

        val answer = translator.complete(TranslationEngine.Cloud(TranslationProvider.Claude, apiKey = " ", model = ""), "Hi")

        assertEquals("Claude needs an API key. Add one in Settings.", answer.errorMessage)
        assertTrue(requests.isEmpty())
    }
}
