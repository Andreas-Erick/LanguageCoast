package com.andreaserick.languagecoast.data

import io.ktor.client.HttpClient
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
 * [Translator] backed by Google Gemini through its REST API
 * (https://ai.google.dev/api/generate-content); expects a [TranslationEngine.Gemini] request.
 */
class GeminiTranslator(private val client: HttpClient) : Translator {

    override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult {
        val engine = request.engine as? TranslationEngine.Gemini ?: return TranslationResult.FAILURE
        return try {
            val response = client.post("$ENDPOINT/${engine.model}:generateContent") {
                header("x-goog-api-key", engine.apiKey)
                contentType(ContentType.Application.Json)
                setBody(geminiRequestBody(buildTranslationPrompt(request)))
            }
            parseGeminiResponse(response.status.value, response.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(TAG, "Translation request failed", e)
            TranslationResult.FAILURE
        }
    }

    private companion object {
        const val TAG = "GeminiTranslator"
        const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
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
private data class GenerateResponse(val candidates: List<Candidate> = emptyList(), val error: GeminiError? = null)

@Serializable
private data class Candidate(val content: Content? = null)

@Serializable
private data class GeminiError(val code: Int? = null, val message: String? = null)

/** The JSON body asking Gemini to answer [prompt]. */
internal fun geminiRequestBody(prompt: String): String =
    json.encodeToString(GenerateRequest.serializer(), GenerateRequest(listOf(Content(listOf(Part(text = prompt))))))

/**
 * Reads a Gemini `generateContent` response into a [TranslationResult]: the text of the first candidate's
 * answer, parsed with [parseTranslationResponse]. Errors and empty answers give [TranslationResult.FAILURE].
 */
internal fun parseGeminiResponse(status: Int, body: String): TranslationResult {
    val response = try {
        json.decodeFromString(GenerateResponse.serializer(), body)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
    if (status >= 400 || response?.error != null) {
        logError("GeminiTranslator", "Gemini returned $status: ${response?.error?.message}")
        return TranslationResult.FAILURE
    }
    val text = response?.candidates?.firstOrNull()?.content?.parts
        ?.filter { it.thought != true }
        ?.mapNotNull { it.text }
        ?.joinToString("")
        ?.takeIf { it.isNotBlank() }
        ?: return TranslationResult.FAILURE
    return parseTranslationResponse(text)
}
