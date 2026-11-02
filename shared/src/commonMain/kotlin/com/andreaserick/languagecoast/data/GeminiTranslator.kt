package com.andreaserick.languagecoast.data

import io.ktor.client.HttpClient
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
 * Includes automatic normalization for deprecated/invalid model names, a fallback to
 * [SettingsDefaults.GEMINI_MODEL] if a chosen preview model returns 404, a transient retry on 503/429,
 * and clear user-facing error messages.
 */
class GeminiTranslator(private val client: HttpClient) : Translator {

    override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult =
        complete(request.engine, buildTranslationPrompt(request)).toTranslation("Gemini's response couldn't be read. Try again.")

    override suspend fun complete(engine: TranslationEngine, prompt: String): Completion {
        if (engine !is TranslationEngine.Gemini) return Completion.failure("This provider can't answer here.")
        val apiKey = engine.apiKey.trim()
        if (apiKey.isBlank()) {
            return Completion.failure("Please enter a Gemini API key in Settings first!")
        }

        val primaryModel = normalizeGeminiModel(engine.model)
        val promptBody = geminiRequestBody(prompt)

        return try {
            var (status, responseText) = postGenerateContent(apiKey, primaryModel, promptBody)

            // If a custom or preview model is unavailable (404), automatically fall back to gemini-2.5-flash.
            if (status == 404 && primaryModel != SettingsDefaults.GEMINI_MODEL) {
                val fallback = postGenerateContent(apiKey, SettingsDefaults.GEMINI_MODEL, promptBody)
                status = fallback.first
                responseText = fallback.second
            } else if (status == 503 || status == 500) {
                // Brief retry on transient Gemini server overload.
                delay(400)
                val retry = postGenerateContent(apiKey, primaryModel, promptBody)
                status = retry.first
                responseText = retry.second
            }

            geminiAnswer(status, responseText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(TAG, "Translation request failed", e)
            Completion.failure("Couldn't reach Google Gemini. Check your internet connection and try again.")
        }
    }

    private suspend fun postGenerateContent(apiKey: String, model: String, body: String): Pair<Int, String> {
        val response = client.post("$ENDPOINT/$model:generateContent") {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return response.status.value to response.bodyAsText()
    }

    private companion object {
        const val TAG = "GeminiTranslator"
        const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
    }
}

/**
 * Maps legacy or invalid Gemini model identifiers that previously existed in settings to valid
 * v1beta Gemini models so requests don't fail with HTTP 404.
 */
internal fun normalizeGeminiModel(rawModel: String): String {
    val cleaned = rawModel.trim().removePrefix("models/")
    return when (cleaned) {
        "", "gemini-3.5-flash", "gemini-1.5-flash" -> SettingsDefaults.GEMINI_MODEL
        "gemini-3.1-pro-preview", "gemini-1.5-pro" -> "gemini-2.5-pro"
        "gemini-3.1-flash-lite-preview" -> "gemini-2.5-flash-lite"
        else -> cleaned
    }
}

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

/** [parseGeminiResponse] without reading the answer as a translation. */
internal fun geminiAnswer(status: Int, body: String): Completion {
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
        val userMessage = when {
            status == 400 && rawMsg.contains("API key", ignoreCase = true) ->
                "Gemini didn't accept the API key. Double-check your key in Settings."
            status == 401 || status == 403 ->
                "Gemini API key was rejected or lacks permission. Check your key in Settings."
            status == 404 ->
                "The selected Gemini model wasn't found. Pick gemini-2.5-flash in Settings."
            status == 429 ->
                "Gemini rate limit or quota reached. Wait a moment or switch to another provider."
            status >= 500 ->
                "Google Gemini is temporarily overloaded ($status). Try again in a moment."
            rawMsg.isNotBlank() ->
                "Gemini: $rawMsg"
            else ->
                "Gemini request failed (HTTP $status)."
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
                "Gemini blocked this phrase due to safety filters. Try rephrasing or use another provider."
            } else {
                "Gemini returned an empty answer. Try again or pick another model."
            }
        )
    return Completion(text.trim())
}
