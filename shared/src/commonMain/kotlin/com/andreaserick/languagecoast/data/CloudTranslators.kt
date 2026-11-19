package com.andreaserick.languagecoast.data

import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [Translator] for OpenAI, Anthropic Claude, Groq, and DeepSeek cloud APIs.
 */
class MultiCloudTranslator(private val client: HttpClient) : Translator {

    override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult =
        complete(request.engine, buildTranslationPrompt(request), patient = false).toTranslation()

    override suspend fun complete(engine: TranslationEngine, prompt: String): Completion = complete(engine, prompt, patient = true)

    /**
     * @param patient Whether the answer may take as long as a model that thinks over a batch of sentences
     *     needs (see [claudeCompletion]). One sentence's translation keeps the two minutes every request has:
     *     the screen that asks for it has no way out while it waits.
     */
    private suspend fun complete(engine: TranslationEngine, prompt: String, patient: Boolean): Completion {
        return when (engine) {
            is TranslationEngine.OpenAI -> callOpenAiCompatible(
                providerName = "OpenAI",
                endpoint = OPENAI_ENDPOINT,
                apiKey = engine.apiKey.trim(),
                model = engine.model.trim().ifBlank { SettingsDefaults.OPENAI_MODEL },
                prompt = prompt
            )
            is TranslationEngine.Groq -> callOpenAiCompatible(
                providerName = "Groq",
                endpoint = GROQ_ENDPOINT,
                apiKey = engine.apiKey.trim(),
                model = engine.model.trim().ifBlank { SettingsDefaults.GROQ_MODEL },
                prompt = prompt
            )
            is TranslationEngine.DeepSeek -> callOpenAiCompatible(
                providerName = "DeepSeek",
                endpoint = DEEPSEEK_ENDPOINT,
                apiKey = engine.apiKey.trim(),
                model = engine.model.trim().ifBlank { SettingsDefaults.DEEPSEEK_MODEL },
                prompt = prompt
            )
            is TranslationEngine.Claude -> callClaude(
                apiKey = engine.apiKey.trim(),
                model = engine.model.trim().ifBlank { SettingsDefaults.CLAUDE_MODEL },
                prompt = prompt,
                patient = patient
            )
            else -> Completion.failure("This provider can't answer here.")
        }
    }

    private suspend fun callOpenAiCompatible(
        providerName: String,
        endpoint: String,
        apiKey: String,
        model: String,
        prompt: String
    ): Completion {
        if (apiKey.isBlank()) {
            return Completion.failure("$providerName needs an API key. Add one in Settings.")
        }
        return try {
            val (status, responseText, retryAfter) = retryHttpWithBackoff(
                shouldRetry = { (code, _, retryHeader) ->
                    code in setOf(408, 502, 503, 504) ||
                        (code == 429 && (parseRetryAfterMillis(retryHeader) ?: 0L) <= 2_000L)
                },
                retryAfterMillis = { (_, _, retryHeader) -> parseRetryAfterMillis(retryHeader) }
            ) {
                val response = client.post(endpoint) {
                    header("Authorization", "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(openRouterRequestBody(model, prompt))
                }
                Triple(response.status.value, response.bodyAsText(), response.headers["retry-after"])
            }
            cloudChatAnswer(providerName, status, responseText, retryAfter = retryAfter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            logError(TAG, "$providerName took too long to answer", e)
            Completion.tooLarge(tookTooLongMessage(providerName))
        } catch (e: Exception) {
            logError(TAG, "$providerName translation request failed", e)
            Completion.failure("Couldn't reach $providerName. Check your internet connection and try again.")
        }
    }

    private suspend fun callClaude(
        apiKey: String,
        model: String,
        prompt: String,
        patient: Boolean
    ): Completion {
        if (apiKey.isBlank()) {
            return Completion.failure("Claude needs an API key. Add one in Settings.")
        }
        return if (patient) client.claudeCompletion(apiKey, model, prompt)
        else client.claudeCompletion(apiKey, model, prompt, requestTimeoutMillis = CLAUDE_SHORT_TIMEOUT_MILLIS, silenceMillis = CLAUDE_SHORT_SILENCE_MILLIS)
    }

    private companion object {
        const val TAG = "MultiCloudTranslator"
        const val OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions"
        const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        const val DEEPSEEK_ENDPOINT = "https://api.deepseek.com/chat/completions"
    }
}

private val cloudJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class CloudChatResponse(
    val choices: List<CloudChatChoice> = emptyList(),
    val error: CloudApiError? = null
)

@Serializable
private data class CloudChatChoice(val message: CloudChatMessage? = null)

@Serializable
private data class CloudChatMessage(val content: String? = null)

@Serializable
private data class CloudApiError(val message: String? = null, val type: String? = null)

/** What the user is told when [providerName] didn't answer within the time a request is given. */
internal fun tookTooLongMessage(providerName: String): String = "$providerName took too long to answer. Try again or pick a faster model."

/** Words in a provider's own error text that say the account, not the request, is the problem. */
private val ACCOUNT_WORDS = listOf("credit", "billing", "quota", "balance", "payment")

/**
 * What the user is told when [providerName] turned a request down for a reason this app has no message of
 * its own for. The provider's own text ([detail]) is English, technical and often long, so it goes to the
 * log and not on screen. The one thing read out of it is an account without credit, which only the user can fix.
 */
internal fun providerErrorMessage(providerName: String, status: Int, detail: String?): String {
    logError("CloudTranslators", "$providerName answered $status: ${detail.orEmpty()}")
    val aboutAccount = detail != null && ACCOUNT_WORDS.any { detail.contains(it, ignoreCase = true) }
    return when {
        status == 402 || aboutAccount ->
            "Your $providerName account is out of credit or over its quota. Check your plan with $providerName, or pick another provider in Settings."
        status == 403 -> "$providerName didn't allow this request. Check that your key can use the selected model, or pick another model in Settings."
        status >= 500 -> "$providerName is having trouble right now. Try again in a moment."
        else -> "$providerName couldn't answer. Try again, or pick another model in Settings."
    }
}

/**
 * [parseCloudChatResponse] without reading the answer as a translation.
 *
 * @param retryAfter The response's `retry-after` header (seconds), which a rate-limited answer may carry.
 */
internal fun cloudChatAnswer(providerName: String, status: Int, body: String, retryAfter: String? = null): Completion {
    val parsed = try {
        cloudJson.decodeFromString(CloudChatResponse.serializer(), body)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
    if (status >= 400 || parsed?.error != null) {
        val errMsg = parsed?.error?.message
        // The requests or tokens per minute are used up, which passes: work that sends many requests waits it out.
        // OpenAI answers 429 as well when the account has no credit left, which no wait fixes.
        val outOfCredit = parsed?.error?.type == "insufficient_quota"
        if (status == 429 && !outOfCredit) {
            return Completion.busy("$providerName rate limit reached. Wait a moment and try again.", retryAfterHeaderSeconds(retryAfter))
        }
        val message = when (status) {
            401 -> "$providerName didn't accept the API key. Check your key in Settings."
            404 -> "The selected $providerName model wasn't found. Pick another model in Settings."
            429 -> providerErrorMessage(providerName, 402, errMsg)
            else -> providerErrorMessage(providerName, status, errMsg)
        }
        return Completion.failure(message)
    }
    val rawContent = parsed?.choices?.firstOrNull()?.message?.content
        ?.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return Completion.failure("$providerName sent an empty answer. Try again or pick another model.")
    return Completion(rawContent)
}

/** Reads an OpenAI-style chat completion from [providerName] into a [TranslationResult]. */
internal fun parseCloudChatResponse(providerName: String, status: Int, body: String): TranslationResult =
    cloudChatAnswer(providerName, status, body).toTranslation()

/**
 * This answer read as a translation: [parseTranslationResponse] of its text, or the failure it already is.
 * An answer that doesn't parse is reported with [unreadable].
 */
internal fun Completion.toTranslation(unreadable: String = "The model's answer couldn't be read. Try again or pick another model."): TranslationResult {
    val answer = text ?: return TranslationResult.failure(errorMessage ?: "The model sent no answer.")
    return parseTranslationResponse(answer).let { if (it.isSuccess) it else TranslationResult.failure(unreadable) }
}

/**
 * The Messages API requires `max_tokens`. It must not have a default here, and neither may `stream`:
 * kotlinx.serialization leaves out fields equal to their default, and the API then answers
 * "max_tokens: Field required", or sends the answer whole.
 */
@Serializable
private data class ClaudeRequest(
    val model: String,
    val max_tokens: Int,
    val stream: Boolean,
    val messages: List<ClaudeMessage>
)

/**
 * A ceiling, not a cost: a batch of fifteen translations or a native-speaker check in a language that
 * tokenizes badly (Japanese, Russian) needs up to about 1500. The Claude 5 models think before they
 * answer, and their thinking counts against this ceiling too, so it must leave room for
 * both: a dozen hard practice-sheet sentences took a top model about 8000 tokens, nearly all of them
 * thinking, and at 16 000 a larger batch came back cut off or empty. Every Claude model on offer can write
 * at least twice this.
 */
internal const val CLAUDE_MAX_TOKENS = 32_000

/**
 * How long a Claude answer may take in all. It is read as it is written (see [claudeCompletion]), so this
 * is a backstop against an answer that never ends, not the time a slow model is given: a model that
 * thinks first takes a minute or two over a dozen sentences, and more on a busy day.
 */
internal const val CLAUDE_TIMEOUT_MILLIS = 600_000L

/**
 * How long Claude may send nothing at all. While a model thinks, the stream can stay quiet for minutes,
 * so this is far above the client's own limit for silence; a connection that is gone is still noticed.
 */
internal const val CLAUDE_SILENCE_MILLIS = 300_000L

/** The limits of a request that is one sentence's translation: the two minutes every other request has. */
internal const val CLAUDE_SHORT_TIMEOUT_MILLIS = 120_000L
internal const val CLAUDE_SHORT_SILENCE_MILLIS = 125_000L

internal const val CLAUDE_ENDPOINT = "https://api.anthropic.com/v1/messages"

@Serializable
private data class ClaudeMessage(val role: String, val content: String)

@Serializable
private data class ClaudeResponse(
    val content: List<ClaudeContentBlock> = emptyList(),
    val stop_reason: String? = null,
    val error: CloudApiError? = null
)

@Serializable
private data class ClaudeContentBlock(val type: String? = null, val text: String? = null)

/** One event of a streamed answer: what it is, and the piece of the answer or the news it carries. */
@Serializable
private data class ClaudeStreamEvent(
    val type: String? = null,
    val delta: ClaudeDelta? = null,
    val content_block: ClaudeContentBlock? = null,
    val error: CloudApiError? = null
)

@Serializable
private data class ClaudeDelta(val type: String? = null, val text: String? = null, val stop_reason: String? = null)

internal fun claudeRequestBody(model: String, prompt: String): String =
    cloudJson.encodeToString(
        ClaudeRequest.serializer(),
        ClaudeRequest(model = model, max_tokens = CLAUDE_MAX_TOKENS, stream = true, messages = listOf(ClaudeMessage("user", prompt)))
    )

/**
 * Asks Claude to answer [prompt] and reads the answer as it is written, a stream of server-sent events.
 *
 * The Claude 5 models think before they answer, over a practice sheet's sentences for a minute or more.
 * An answer awaited whole has to arrive inside one time limit, and a connection that carries nothing all
 * that while can be dropped on the way; with two minutes for the whole answer, a hard sheet from a top
 * model failed. Read as a stream, an answer may take as long as it keeps coming ([requestTimeoutMillis] is
 * only a backstop).
 *
 * An error comes back as ordinary JSON, not as a stream, and is read by [claudeAnswer].
 */
internal suspend fun HttpClient.claudeCompletion(
    apiKey: String,
    model: String,
    prompt: String,
    endpoint: String = CLAUDE_ENDPOINT,
    requestTimeoutMillis: Long = CLAUDE_TIMEOUT_MILLIS,
    silenceMillis: Long = CLAUDE_SILENCE_MILLIS
): Completion {
    // Set once Claude has begun to answer. A time limit that runs out before that is a connection that couldn't
    // be made, which both platforms report as a timeout too: asking for less would not help then.
    var answering = false
    return try {
        preparePost(endpoint) {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            timeout {
                this.requestTimeoutMillis = requestTimeoutMillis
                this.socketTimeoutMillis = silenceMillis
            }
            contentType(ContentType.Application.Json)
            setBody(claudeRequestBody(model, prompt))
        }.execute { response ->
            answering = true
            val isStream = response.contentType()?.match(ContentType.Text.EventStream) == true
            if (response.status.value >= 400 || !isStream) {
                claudeAnswer(response.status.value, response.bodyAsText(), retryAfter = response.headers["retry-after"])
            } else {
                val stream = ClaudeStream()
                val channel = response.bodyAsChannel()
                while (true) {
                    val line = channel.readUTF8Line() ?: break
                    stream.read(line)
                }
                stream.completion()
            }
        }
    } catch (e: CancellationException) {
        // Ours to pass on if this coroutine was cancelled. If it wasn't, the request was cancelled under us,
        // which is how a time limit ends an answer that is being read.
        currentCoroutineContext().ensureActive()
        logError(CLAUDE_TAG, "Claude took too long to answer", e)
        Completion.tooLarge(tookTooLongMessage("Claude"))
    } catch (e: Exception) {
        if (answering && ranOutOfTime(e)) {
            logError(CLAUDE_TAG, "Claude took too long to answer", e)
            Completion.tooLarge(tookTooLongMessage("Claude"))
        } else {
            logError(CLAUDE_TAG, "Claude request failed", e)
            Completion.failure("Couldn't reach Claude. Check your internet connection and try again.")
        }
    }
}

private const val CLAUDE_TAG = "ClaudeCompletion"

/**
 * Whether [error] says an answer took too long or stopped coming. Each platform's networking says that its
 * own way, and while an answer is being read it arrives wrapped ("channel closed: Read timed out"), so the
 * causes are looked through as well.
 */
private fun ranOutOfTime(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.take(8).any {
        it is HttpRequestTimeoutException || it is SocketTimeoutException ||
            it::class.simpleName?.contains("Timeout") == true || it.message?.contains("timed out", ignoreCase = true) == true
    }

/**
 * A streamed Claude answer, put together from its events line by line. Only three kinds of event matter:
 * a piece of the answer's text, the reason the answer stopped, and an error in mid-answer. Everything else
 * (the start of the message, thinking, pings that keep the connection open) is passed over.
 */
internal class ClaudeStream {
    private val text = StringBuilder()
    private var stopReason: String? = null
    private var error: CloudApiError? = null

    /** Takes one line of the stream; only `data:` lines carry anything. */
    fun read(line: String) {
        if (!line.startsWith("data:")) return
        val event = try {
            cloudJson.decodeFromString(ClaudeStreamEvent.serializer(), line.substring(5).trim())
        } catch (e: SerializationException) {
            return
        } catch (e: IllegalArgumentException) {
            return
        }
        when (event.type) {
            "content_block_start" -> if (event.content_block?.type == "text") event.content_block.text?.let { text.append(it) }
            "content_block_delta" -> if (event.delta?.type == "text_delta") event.delta.text?.let { text.append(it) }
            "message_delta" -> event.delta?.stop_reason?.let { stopReason = it }
            "error" -> error = event.error ?: CloudApiError()
        }
    }

    fun completion(): Completion {
        // A stream that ends without saying why it stopped broke off on the way: what it brought may end in half a line.
        val failed = error ?: return claudeOutcome(text.toString(), stopReason, brokeOff = stopReason == null)
        // Overloaded in mid-answer: the same request can go through in a moment.
        if (failed.type == "overloaded_error" || failed.type == "rate_limit_error") {
            return Completion.busy("Claude is busy or rate-limited. Wait a moment and try again.", CLAUDE_RETRY_SECONDS)
        }
        // Anthropic gave up on the request for time: less at a time can go through.
        if (failed.type == "timeout_error") return Completion.tooLarge(tookTooLongMessage("Claude"))
        return Completion.failure(providerErrorMessage("Claude", 500, failed.message))
    }
}

/**
 * What an answer that ended for [stopReason] with [text] comes to. Two endings are no answer: the model
 * declined, or it ran out of room ([CLAUDE_MAX_TOKENS], which its thinking uses too). An answer that ran
 * out of room after it had started stops in the middle of a line, and so does one whose stream broke off
 * ([brokeOff]): the lines before that one are kept.
 */
internal fun claudeOutcome(text: String, stopReason: String?, brokeOff: Boolean = false): Completion {
    if (stopReason == "refusal") {
        return Completion.failure("Claude declined to answer this. Pick another model in Settings, or change what you asked for and try again.")
    }
    val outOfRoom = stopReason == "max_tokens"
    val whole = if (outOfRoom || brokeOff) text.substringBeforeLast('\n', missingDelimiterValue = "") else text
    if (whole.isBlank()) {
        return when {
            outOfRoom -> Completion.tooLarge("Claude ran out of room before it had answered. Ask for less at a time, or pick another model in Settings.")
            brokeOff -> Completion.failure("Claude's answer broke off. Check your internet connection and try again.")
            else -> Completion.failure("Claude returned an empty answer. Try again.")
        }
    }
    return Completion(whole.trim())
}

/** How long to wait after a 429 or 529 that doesn't say: Claude's per-minute limits refill within seconds. */
internal const val CLAUDE_RETRY_SECONDS = 10

/**
 * [parseClaudeResponse] without reading the answer as a translation.
 *
 * @param retryAfter The response's `retry-after` header (seconds), which a rate-limited answer carries.
 */
internal fun claudeAnswer(status: Int, body: String, retryAfter: String? = null): Completion {
    val parsed = try {
        cloudJson.decodeFromString(ClaudeResponse.serializer(), body)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
    if (status >= 400 || parsed?.error != null) {
        val errMsg = parsed?.error?.message
        // 429: the key's requests or tokens per minute are used up. 529: Anthropic is overloaded. Both pass.
        if (status == 429 || status == 529) {
            val seconds = retryAfter?.trim()?.toDoubleOrNull()?.takeIf { it >= 0 }?.let { (it + 0.999).toInt() } ?: CLAUDE_RETRY_SECONDS
            return Completion.busy("Claude is busy or rate-limited. Wait a moment and try again.", seconds.coerceAtLeast(1))
        }
        // Anthropic gave up on the request for time: less at a time can go through.
        if (status == 504 || parsed?.error?.type == "timeout_error") {
            logError("CloudTranslators", "Claude answered $status: ${errMsg.orEmpty()}")
            return Completion.tooLarge(tookTooLongMessage("Claude"))
        }
        val message = when (status) {
            401, 403 -> "Claude didn't accept the API key. Check your Anthropic key in Settings."
            404 -> "The selected Claude model wasn't found. Pick another model in Settings."
            else -> providerErrorMessage("Claude", status, errMsg)
        }
        return Completion.failure(message)
    }
    val text = parsed?.content
        ?.filter { it.type == null || it.type == "text" }
        ?.mapNotNull { it.text }
        ?.joinToString("")
        .orEmpty()
    return claudeOutcome(text, parsed?.stop_reason)
}

/** Reads a Claude messages response into a [TranslationResult]. */
internal fun parseClaudeResponse(status: Int, body: String): TranslationResult =
    claudeAnswer(status, body).toTranslation("Claude's answer couldn't be read. Try again.")
