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
 * The OpenAI-style chat completions API, which OpenAI, Groq, DeepSeek and OpenRouter all speak, as a
 * [CloudWire]. Answers are asked for as a stream (`"stream": true`, events of `choices[0].delta.content`).
 * A model that may not be streamed (OpenAI asks some organizations to be verified first) is asked again
 * for its answer whole.
 */

internal const val OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions"
internal const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
internal const val DEEPSEEK_ENDPOINT = "https://api.deepseek.com/chat/completions"
internal const val OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

/**
 * One OpenAI-style provider's wire.
 *
 * @param readAnswer Reads an answer that came whole, or an error, the way this provider words them
 *     ([cloudChatAnswer], or [openRouterAnswer] for OpenRouter's own error codes).
 * @param extraHeaders Sent with every request (OpenRouter's optional attribution).
 * @param stream Whether the answer is asked for as a stream.
 */
internal class ChatWire(
    override val providerName: String,
    private val endpoint: String,
    private val apiKey: String,
    private val model: String,
    private val readAnswer: (status: Int, body: String, retryAfter: String?) -> Completion,
    private val extraHeaders: Map<String, String> = emptyMap(),
    private val stream: Boolean = true
) : CloudWire {

    override fun request(builder: HttpRequestBuilder, prompt: String) = with(builder) {
        url(endpoint)
        header("Authorization", "Bearer $apiKey")
        extraHeaders.forEach { (name, value) -> header(name, value) }
        contentType(ContentType.Application.Json)
        setBody(chatRequestBody(model, prompt, stream))
    }

    override fun stream(onPartialText: ((String) -> Unit)?): StreamedAnswer = ChatStream(providerName, readAnswer, onPartialText)

    override fun answer(status: Int, body: String, retryAfter: String?): Completion = readAnswer(status, body, retryAfter)

    /** A model that may not be streamed, for this key, is asked for its answer whole. */
    override fun instead(status: Int, body: String): CloudWire? =
        if (stream && status == 400 && body.contains("stream", ignoreCase = true)) {
            ChatWire(providerName, endpoint, apiKey, model, readAnswer, extraHeaders, stream = false)
        } else null

    companion object {
        fun openAi(apiKey: String, model: String, endpoint: String = OPENAI_ENDPOINT) =
            ChatWire("OpenAI", endpoint, apiKey, model, { s, b, r -> cloudChatAnswer("OpenAI", s, b, r) })

        fun groq(apiKey: String, model: String, endpoint: String = GROQ_ENDPOINT) =
            ChatWire("Groq", endpoint, apiKey, model, { s, b, r -> cloudChatAnswer("Groq", s, b, r) })

        fun deepSeek(apiKey: String, model: String, endpoint: String = DEEPSEEK_ENDPOINT) =
            ChatWire("DeepSeek", endpoint, apiKey, model, { s, b, r -> cloudChatAnswer("DeepSeek", s, b, r) })

        fun openRouter(apiKey: String, model: String, endpoint: String = OPENROUTER_ENDPOINT) =
            ChatWire(
                "OpenRouter", endpoint, apiKey, model, ::openRouterAnswer,
                // Optional attribution, shown on openrouter.ai.
                extraHeaders = mapOf("HTTP-Referer" to "https://github.com/Andreas-Erick/LanguageCoast", "X-Title" to "Language Coast")
            )
    }
}

private val chatJson = Json { ignoreUnknownKeys = true }

/** [stream] has no default: kotlinx.serialization leaves out a field equal to its default, and the answer would come whole. */
@Serializable
private data class ChatRequest(val model: String, val messages: List<ChatMessage>, val stream: Boolean)

@Serializable
private data class ChatMessage(val role: String? = null, val content: String? = null)

@Serializable
private data class ChatResponse(val choices: List<ChatChoice> = emptyList(), val error: ChatError? = null)

/** [message] in an answer sent whole, [delta] in each event of a stream. */
@Serializable
private data class ChatChoice(val message: ChatMessage? = null, val delta: ChatMessage? = null, val finish_reason: String? = null)

@Serializable
private data class ChatError(val code: Int? = null, val message: String? = null, val type: String? = null)

/** The JSON body asking [model] to answer [prompt], as a stream when [stream]. */
internal fun chatRequestBody(model: String, prompt: String, stream: Boolean = true): String =
    chatJson.encodeToString(ChatRequest.serializer(), ChatRequest(model, listOf(ChatMessage("user", prompt)), stream))

/** How an answer that finished for [finishReason] ended. */
private fun chatEnding(providerName: String, finishReason: String?): Ending? = when (finishReason) {
    null -> null
    "length" -> Ending.OutOfRoom
    "content_filter" -> Ending.Declined("$providerName's content filter blocked this text. Try other words, or pick another provider in Settings.")
    else -> Ending.Done
}

/**
 * [text] without a model's thinking out loud: `<think>…</think>` blocks (DeepSeek, Qwen on Groq), and a block
 * that was opened and not yet closed.
 */
private fun withoutThinking(text: String): String =
    text.replace(THINK_BLOCK, "").let { rest ->
        val open = rest.indexOf("<think>", ignoreCase = true)
        if (open >= 0) rest.substring(0, open) else rest
    }

private val THINK_BLOCK = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE)

/** A streamed OpenAI-style answer: the pieces of `choices[0].delta.content`, and how it finished. */
private class ChatStream(
    providerName: String,
    private val readAnswer: (Int, String, String?) -> Completion,
    onPartialText: ((String) -> Unit)?
) : StreamedAnswer(providerName, onPartialText?.let { report -> { text: String -> report(withoutThinking(text)) } }) {
    private val name = providerName

    override fun event(payload: String): Boolean {
        val event = try {
            chatJson.decodeFromString(ChatResponse.serializer(), payload)
        } catch (e: SerializationException) {
            return false
        } catch (e: IllegalArgumentException) {
            return false
        }
        event.error?.let {
            failure = readAnswer(it.code ?: 500, payload, null)
            return true
        }
        val choice = event.choices.firstOrNull() ?: return true
        choice.delta?.content?.let(::append)
        chatEnding(name, choice.finish_reason)?.let {
            ending = it
            flush()
        }
        return true
    }

    override fun finalText(text: String): String = withoutThinking(text)
}

/**
 * Reads an OpenAI-style chat completion from [providerName] that came whole, or an error.
 *
 * @param retryAfter The response's `retry-after` header (seconds), which a rate-limited answer may carry.
 */
internal fun cloudChatAnswer(providerName: String, status: Int, body: String, retryAfter: String? = null): Completion {
    val parsed = decodeChat(body)
    if (status >= 400 || parsed?.error != null) {
        val errMsg = parsed?.error?.message
        // The requests or tokens per minute are used up, which passes: work that sends many requests waits it out.
        // OpenAI answers 429 as well when the account has no credit left, which no wait fixes.
        val outOfCredit = parsed?.error?.type == "insufficient_quota"
        if (status == 429 && !outOfCredit) {
            return Completion.busy("$providerName rate limit reached. Wait a moment and try again.", retryAfterHeaderSeconds(retryAfter))
        }
        if (status == 408 || status == 504) {
            logError("ChatWire", "$providerName answered $status: ${errMsg.orEmpty()}")
            return Completion.tooLarge(tookTooLongMessage(providerName))
        }
        val message = when (status) {
            401 -> "$providerName didn't accept the API key. Check your key in Settings."
            404 -> "The selected $providerName model wasn't found. Pick another model in Settings."
            429 -> providerErrorMessage(providerName, 402, errMsg)
            else -> providerErrorMessage(providerName, status, errMsg)
        }
        return Completion.failure(message)
    }
    return wholeChatAnswer(providerName, parsed)
}

/** Reads an OpenAI-style chat completion from [providerName] into a [TranslationResult]. */
internal fun parseCloudChatResponse(providerName: String, status: Int, body: String): TranslationResult =
    cloudChatAnswer(providerName, status, body).toTranslation()

/**
 * Reads an OpenRouter chat completion that came whole, or an error, turning OpenRouter's errors
 * (https://openrouter.ai/docs/api-reference/errors) into messages the user can act on. Errors can also
 * arrive with status 200, in the body's `error` field.
 *
 * @param retryAfter The response's `retry-after` header (seconds), which a rate-limited answer may carry.
 */
internal fun openRouterAnswer(status: Int, body: String, retryAfter: String? = null): Completion {
    val response = decodeChat(body)
    val error = response?.error
    if (status >= 400 || error != null) {
        val code = error?.code ?: status
        // Too many requests for now, which passes: work that sends many requests waits it out.
        if (code == 429) return Completion.busy("OpenRouter is busy. Wait a moment and try again.", retryAfterHeaderSeconds(retryAfter))
        if (code == 408 || code == 504) {
            logError("ChatWire", "OpenRouter answered $code: ${error?.message.orEmpty()}")
            return Completion.tooLarge(tookTooLongMessage("OpenRouter"))
        }
        val message = when (code) {
            401 -> "OpenRouter didn't accept the API key. Check it in Settings."
            402 -> "Your OpenRouter account is out of credits. Add credits at openrouter.ai, or pick another provider in Settings."
            404 -> "The selected OpenRouter model wasn't found. Pick another model in Settings."
            else -> providerErrorMessage("OpenRouter", code, error?.message)
        }
        return Completion.failure(message)
    }
    return wholeChatAnswer("OpenRouter", response)
}

/** Reads an OpenRouter chat completion into a [TranslationResult]. */
internal fun parseOpenRouterResponse(status: Int, body: String): TranslationResult = openRouterAnswer(status, body).toTranslation()

private fun decodeChat(body: String): ChatResponse? = try {
    chatJson.decodeFromString(ChatResponse.serializer(), body)
} catch (e: SerializationException) {
    null
} catch (e: IllegalArgumentException) {
    null
}

private fun wholeChatAnswer(providerName: String, response: ChatResponse?): Completion {
    val choice = response?.choices?.firstOrNull()
    val text = withoutThinking(choice?.message?.content.orEmpty())
    if (text.isBlank()) return Completion.failure("$providerName sent an empty answer. Try again or pick another model.")
    return answerOutcome(providerName, text, chatEnding(providerName, choice?.finish_reason) ?: Ending.Done)
}
