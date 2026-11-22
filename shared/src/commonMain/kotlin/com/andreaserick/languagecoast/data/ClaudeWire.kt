package com.andreaserick.languagecoast.data

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Anthropic's Messages API (https://docs.anthropic.com/en/api/messages), as a [CloudWire]. Answers are asked
 * for as a stream of server-sent events; an error comes back as ordinary JSON.
 */

internal const val CLAUDE_ENDPOINT = "https://api.anthropic.com/v1/messages"

/**
 * A ceiling, not a cost: a batch of fifteen translations or a native-speaker check in a language that
 * tokenizes badly (Japanese, Russian) needs up to about 1500. The Claude 5 models think before they
 * answer, and their thinking counts against this ceiling too, so it must leave room for
 * both: a dozen hard practice-sheet sentences took a top model about 8000 tokens, nearly all of them
 * thinking, and at 16 000 a larger batch came back cut off or empty. Every Claude model on offer can write
 * at least twice this.
 */
internal const val CLAUDE_MAX_TOKENS = 32_000

/** How long to wait after a 429 or 529 that doesn't say: Claude's per-minute limits refill within seconds. */
internal const val CLAUDE_RETRY_SECONDS = 10

/** Claude's wire, asking [model] with [apiKey]. */
internal class ClaudeWire(
    private val apiKey: String,
    private val model: String,
    private val endpoint: String = CLAUDE_ENDPOINT
) : CloudWire {
    override val providerName = "Claude"

    override fun request(builder: HttpRequestBuilder, prompt: String) = with(builder) {
        url(endpoint)
        header("x-api-key", apiKey)
        header("anthropic-version", "2023-06-01")
        contentType(ContentType.Application.Json)
        setBody(claudeRequestBody(model, prompt))
    }

    override fun stream(onPartialText: ((String) -> Unit)?): StreamedAnswer = ClaudeStream(onPartialText)

    override fun answer(status: Int, body: String, retryAfter: String?): Completion = claudeAnswer(status, body, retryAfter)
}

private val claudeJson = Json { ignoreUnknownKeys = true }

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

@Serializable
private data class ClaudeMessage(val role: String, val content: String)

@Serializable
private data class ClaudeError(val message: String? = null, val type: String? = null)

@Serializable
private data class ClaudeResponse(
    val content: List<ClaudeContentBlock> = emptyList(),
    val stop_reason: String? = null,
    val error: ClaudeError? = null
)

@Serializable
private data class ClaudeContentBlock(val type: String? = null, val text: String? = null)

/** One event of a streamed answer: what it is, and the piece of the answer or the news it carries. */
@Serializable
private data class ClaudeStreamEvent(
    val type: String? = null,
    val delta: ClaudeDelta? = null,
    val content_block: ClaudeContentBlock? = null,
    val error: ClaudeError? = null
)

@Serializable
private data class ClaudeDelta(val type: String? = null, val text: String? = null, val stop_reason: String? = null)

internal fun claudeRequestBody(model: String, prompt: String): String =
    claudeJson.encodeToString(
        ClaudeRequest.serializer(),
        ClaudeRequest(model = model, max_tokens = CLAUDE_MAX_TOKENS, stream = true, messages = listOf(ClaudeMessage("user", prompt)))
    )

/** How an answer that stopped for [stopReason] ended. */
private fun claudeEnding(stopReason: String?): Ending? = when (stopReason) {
    null -> null
    "refusal" -> Ending.Declined("Claude declined to answer this. Pick another model in Settings, or change what you asked for and try again.")
    "max_tokens" -> Ending.OutOfRoom
    else -> Ending.Done
}

/**
 * A streamed Claude answer. Only three kinds of event matter: a piece of the answer's text, the reason the
 * answer stopped, and an error in mid-answer. Everything else (the start of the message, thinking, pings that
 * keep the connection open) is passed over.
 */
internal class ClaudeStream(onPartialText: ((String) -> Unit)? = null) : StreamedAnswer("Claude", onPartialText) {
    override fun event(payload: String): Boolean {
        val event = try {
            claudeJson.decodeFromString(ClaudeStreamEvent.serializer(), payload)
        } catch (e: SerializationException) {
            return false
        } catch (e: IllegalArgumentException) {
            return false
        }
        when (event.type) {
            "content_block_start" -> if (event.content_block?.type == "text") event.content_block.text?.let(::append)
            "content_block_delta" -> if (event.delta?.type == "text_delta") event.delta.text?.let(::append)
            "content_block_stop" -> flush()
            "message_delta" -> claudeEnding(event.delta?.stop_reason)?.let { ending = it }
            "error" -> failure = claudeStreamError(event.error ?: ClaudeError())
        }
        return true
    }
}

private fun claudeStreamError(error: ClaudeError): Completion = when (error.type) {
    // Overloaded in mid-answer: the same request can go through in a moment.
    "overloaded_error", "rate_limit_error" -> Completion.busy("Claude is busy or rate-limited. Wait a moment and try again.", CLAUDE_RETRY_SECONDS)
    // Anthropic gave up on the request for time: less at a time can go through.
    "timeout_error" -> Completion.tooLarge(tookTooLongMessage("Claude"))
    else -> Completion.failure(providerErrorMessage("Claude", 500, error.message))
}

/**
 * Reads a Claude answer that came whole, or an error.
 *
 * @param retryAfter The response's `retry-after` header (seconds), which a rate-limited answer carries.
 */
internal fun claudeAnswer(status: Int, body: String, retryAfter: String? = null): Completion {
    val parsed = try {
        claudeJson.decodeFromString(ClaudeResponse.serializer(), body)
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
            logError("ClaudeWire", "Claude answered $status: ${errMsg.orEmpty()}")
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
    return answerOutcome("Claude", text, claudeEnding(parsed?.stop_reason) ?: Ending.Done)
}

/** Reads a Claude messages response into a [TranslationResult]. */
internal fun parseClaudeResponse(status: Int, body: String): TranslationResult =
    claudeAnswer(status, body).toTranslation("Claude's answer couldn't be read. Try again.")
