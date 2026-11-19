package com.andreaserick.languagecoast.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [Translator] backed by OpenRouter (https://openrouter.ai), which gives one API key access to many
 * cloud models. Uses the same prompt and answer format as Gemini; expects a [TranslationEngine.OpenRouter] request.
 */
class OpenRouterTranslator(private val client: HttpClient) : Translator {

    override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult =
        complete(request.engine, buildTranslationPrompt(request)).toTranslation()

    override suspend fun complete(engine: TranslationEngine, prompt: String): Completion {
        if (engine !is TranslationEngine.OpenRouter) return Completion.failure("This provider can't answer here.")
        val apiKey = engine.apiKey.trim()
        if (apiKey.isBlank()) return Completion.failure("OpenRouter needs an API key. Add one in Settings.")
        val body = openRouterRequestBody(engine.model.trim().ifBlank { SettingsDefaults.OPENROUTER_MODEL }, prompt)
        return try {
            val (status, response, retryAfter) = retryHttpWithBackoff(
                shouldRetry = { (code, _, retryHeader) ->
                    code in setOf(408, 502, 503, 504) ||
                        (code == 429 && (parseRetryAfterMillis(retryHeader) ?: 0L) <= 2_000L)
                },
                retryAfterMillis = { (_, _, retryHeader) -> parseRetryAfterMillis(retryHeader) }
            ) {
                post(apiKey, body)
            }
            openRouterAnswer(status, response, retryAfter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            logError(TAG, "OpenRouter took too long to answer", e)
            Completion.tooLarge(tookTooLongMessage("OpenRouter"))
        } catch (e: Exception) {
            // Everything, as the other translators do: this must never throw, and a key pasted with a line break
            // in it, for one, fails before anything is sent.
            logError(TAG, "OpenRouter request failed", e)
            Completion.failure("Couldn't reach OpenRouter. Check your internet connection and try again.")
        }
    }

    /** Sends [body] to the chat completions endpoint and returns the status code, the response body and its `retry-after` header. */
    private suspend fun post(apiKey: String, body: String): Triple<Int, String, String?> {
        val response = client.post(ENDPOINT) {
            header("Authorization", "Bearer $apiKey")
            // Optional attribution, shown on openrouter.ai.
            header("HTTP-Referer", "https://github.com/Andreas-Erick/LanguageCoast")
            header("X-Title", "Language Coast")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return Triple(response.status.value, response.bodyAsText(), response.headers["retry-after"])
    }

    private companion object {
        const val TAG = "OpenRouterTranslator"
        const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    }
}

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class ChatRequest(val model: String, val messages: List<ChatMessage>)

@Serializable
private data class ChatMessage(val role: String, val content: String? = null)

@Serializable
private data class ChatResponse(val choices: List<ChatChoice> = emptyList(), val error: ApiError? = null)

@Serializable
private data class ChatChoice(val message: ChatMessage? = null)

@Serializable
private data class ApiError(val code: Int? = null, val message: String? = null)

/** The JSON body asking [model] to answer [prompt]. */
internal fun openRouterRequestBody(model: String, prompt: String): String =
    json.encodeToString(ChatRequest.serializer(), ChatRequest(model, listOf(ChatMessage("user", prompt))))

/**
 * Reads an OpenRouter chat completion into a [TranslationResult], turning OpenRouter's errors
 * (https://openrouter.ai/docs/api-reference/errors) into messages the user can act on.
 * Errors can also arrive with status 200, in the body's `error` field.
 */
internal fun parseOpenRouterResponse(status: Int, body: String): TranslationResult = openRouterAnswer(status, body).toTranslation()

/**
 * [parseOpenRouterResponse] without reading the answer as a translation.
 *
 * @param retryAfter The response's `retry-after` header (seconds), which a rate-limited answer may carry.
 */
internal fun openRouterAnswer(status: Int, body: String, retryAfter: String? = null): Completion {
    val response = try {
        json.decodeFromString(ChatResponse.serializer(), body)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
    val error = response?.error
    if (status >= 400 || error != null) {
        val code = error?.code ?: status
        // Too many requests for now, which passes: work that sends many requests waits it out.
        if (code == 429) return Completion.busy("OpenRouter is busy. Wait a moment and try again.", retryAfterHeaderSeconds(retryAfter))
        val message = when (code) {
            401 -> "OpenRouter didn't accept the API key. Check it in Settings."
            402 -> "Your OpenRouter account is out of credits. Add credits at openrouter.ai, or pick another provider in Settings."
            404 -> "The selected OpenRouter model wasn't found. Pick another model in Settings."
            else -> providerErrorMessage("OpenRouter", code, error?.message)
        }
        return Completion.failure(message)
    }
    val content = response?.choices?.firstOrNull()?.message?.content?.trim()?.takeIf { it.isNotEmpty() }
        ?: return Completion.failure("OpenRouter sent an empty answer. Try again or pick another model.")
    return Completion(content)
}
