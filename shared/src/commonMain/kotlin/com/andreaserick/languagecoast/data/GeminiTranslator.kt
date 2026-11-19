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
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [Translator] backed by Google Gemini through its REST API
 * (https://ai.google.dev/api/generate-content); expects a [TranslationEngine.Gemini] request.
 *
 * Includes automatic normalization for retired model names, a fallback to the replacement Google names
 * (or [SettingsDefaults.GEMINI_MODEL]) if the chosen model returns 404, a transient retry on 503/500,
 * and clear user-facing error messages.
 */
class GeminiTranslator(private val client: HttpClient) : Translator {

    override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult =
        complete(request.engine, buildTranslationPrompt(request)).toTranslation("Gemini's response couldn't be read. Try again.")

    override suspend fun complete(engine: TranslationEngine, prompt: String): Completion {
        if (engine !is TranslationEngine.Gemini) return Completion.failure("This provider can't answer here.")
        val apiKey = engine.apiKey.trim()
        if (apiKey.isBlank()) {
            return Completion.failure("Gemini needs an API key. Add one in Settings.")
        }

        val primaryModel = normalizeGeminiModel(engine.model)
        val promptBody = geminiRequestBody(prompt)

        return try {
            var (status, responseText, retryAfter) = retryHttpWithBackoff(
                shouldRetry = { (code, _, retryHeader) ->
                    code in setOf(408, 502, 503, 504) ||
                        (code == 429 && (parseRetryAfterMillis(retryHeader) ?: 0L) <= 2_000L)
                },
                retryAfterMillis = { (_, _, retryHeader) -> parseRetryAfterMillis(retryHeader) }
            ) {
                postGenerateContent(apiKey, primaryModel, promptBody)
            }

            if (status == 404) {
                // Google retires models often, and its 404 names the replacement ("use models/gemini-x"):
                // try that, or else the default, so a stale choice keeps translating.
                val replacement = suggestedGeminiModel(responseText)?.let(::normalizeGeminiModel)?.takeIf { it != primaryModel }
                    ?: SettingsDefaults.GEMINI_MODEL.takeIf { it != primaryModel }
                if (replacement != null) {
                    logError(TAG, "Model $primaryModel not found, retrying with $replacement")
                    val fallback = retryHttpWithBackoff(
                        shouldRetry = { (code, _, retryHeader) ->
                            code in setOf(408, 502, 503, 504) ||
                                (code == 429 && (parseRetryAfterMillis(retryHeader) ?: 0L) <= 2_000L)
                        },
                        retryAfterMillis = { (_, _, retryHeader) -> parseRetryAfterMillis(retryHeader) }
                    ) {
                        postGenerateContent(apiKey, replacement, promptBody)
                    }
                    status = fallback.first
                    responseText = fallback.second
                    retryAfter = fallback.third
                }
            }

            geminiAnswer(status, responseText, retryAfter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            logError(TAG, "Gemini took too long to answer", e)
            Completion.tooLarge(tookTooLongMessage("Gemini"))
        } catch (e: Exception) {
            logError(TAG, "Translation request failed", e)
            Completion.failure("Couldn't reach Gemini. Check your internet connection and try again.")
        }
    }

    /** The response's status, its body, and its `retry-after` header if it has one. */
    private suspend fun postGenerateContent(apiKey: String, model: String, body: String): Triple<Int, String, String?> {
        val response = client.post("$ENDPOINT/$model:generateContent") {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return Triple(response.status.value, response.bodyAsText(), response.headers["retry-after"])
    }

    private companion object {
        const val TAG = "GeminiTranslator"
        const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
    }
}

private val SAFE_GEMINI_MODEL_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")

/**
 * Maps Gemini model ids that Google has retired (or is about to) to their current replacements, so a
 * choice saved by an earlier version of the app doesn't fail with HTTP 404. Unknown ids pass through only
 * when they match a safe URL path segment (blocking path traversal or query injection from crafted backups).
 */
internal fun normalizeGeminiModel(rawModel: String): String {
    val cleaned = rawModel.trim().removePrefix("models/")
    return when (cleaned) {
        "", "gemini-1.5-flash", "gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-2.5-flash",
        "gemini-2.5-flash-lite", "gemini-3.1-flash-lite-preview" -> SettingsDefaults.GEMINI_MODEL
        "gemini-1.5-pro", "gemini-2.5-pro", "gemini-3-pro-preview" -> "gemini-3.1-pro-preview"
        else -> if (SAFE_GEMINI_MODEL_ID.matches(cleaned) && ".." !in cleaned) cleaned else SettingsDefaults.GEMINI_MODEL
    }
}

/**
 * The model Google's 404 tells callers to switch to ("Please update your code to use
 * models/gemini-3.5-flash-lite for the latest features"), or null if the message names none.
 */
internal fun suggestedGeminiModel(errorBody: String): String? =
    Regex("use models/([A-Za-z0-9.\\-]+)").find(errorBody)?.groupValues?.get(1)

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class GenerateRequest(val contents: List<Content>)

@Serializable
private data class Content(val parts: List<Part> = emptyList())

/** [thought] marks the model's reasoning, which isn't part of the answer. */
@Serializable
private data class Part(val text: String? = null, val thought: Boolean? = null)

@Serializable
private data class GenerateResponse(
    val candidates: List<Candidate> = emptyList(),
    val error: GeminiError? = null
)

@Serializable
private data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null
)

@Serializable
private data class GeminiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

/** The JSON body asking Gemini to answer [prompt]. */
internal fun geminiRequestBody(prompt: String): String =
    json.encodeToString(GenerateRequest.serializer(), GenerateRequest(listOf(Content(listOf(Part(text = prompt))))))

/**
 * Reads a Gemini `generateContent` response into a [TranslationResult]: the text of the first candidate's
 * answer, parsed with [parseTranslationResponse]. Errors and empty answers give a failed [TranslationResult]
 * with a clear user-facing [TranslationResult.errorMessage].
 */
internal fun parseGeminiResponse(status: Int, body: String): TranslationResult =
    geminiAnswer(status, body).toTranslation("Gemini's response couldn't be read. Try again.")

/**
 * [parseGeminiResponse] without reading the answer as a translation.
 *
 * @param retryAfter The response's `retry-after` header (seconds), which a rate-limited answer may carry.
 */
internal fun geminiAnswer(status: Int, body: String, retryAfter: String? = null): Completion {
    val response = try {
        json.decodeFromString(GenerateResponse.serializer(), body)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
    if (status >= 400 || response?.error != null) {
        val rawMsg = response?.error?.message.orEmpty()
        logError("GeminiTranslator", "Gemini returned $status: $rawMsg")
        // The requests per minute (or the day's quota) are used up. The first passes, so work that sends many requests waits it out.
        if (status == 429) {
            return Completion.busy("Gemini rate limit or quota reached. Wait a moment or switch to another provider.", retryAfterHeaderSeconds(retryAfter))
        }
        val userMessage = when {
            status == 400 && rawMsg.contains("API key", ignoreCase = true) ->
                "Gemini didn't accept the API key. Check your key in Settings."
            status == 401 || status == 403 ->
                "Gemini didn't accept the API key, or the key can't use this model. Check your key in Settings."
            status == 404 ->
                "The selected Gemini model wasn't found. Pick another model in Settings, or tap Check for new models."
            status >= 500 ->
                "Gemini is overloaded right now. Try again in a moment."
            else ->
                providerErrorMessage("Gemini", status, rawMsg)
        }
        return Completion.failure(userMessage)
    }
    val candidate = response?.candidates?.firstOrNull()
    val text = candidate?.content?.parts
        ?.filter { it.thought != true }
        ?.mapNotNull { it.text }
        ?.joinToString("")
        ?.takeIf { it.isNotBlank() }
        ?: return Completion.failure(
            if (candidate?.finishReason == "SAFETY") {
                "Gemini's safety filter blocked this text. Try other words, or pick another provider in Settings."
            } else {
                "Gemini returned an empty answer. Try again or pick another model."
            }
        )
    return Completion(text.trim())
}
