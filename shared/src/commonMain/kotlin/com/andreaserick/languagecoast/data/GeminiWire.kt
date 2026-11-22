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
 * Google Gemini's REST API (https://ai.google.dev/api/generate-content), as a [CloudWire]. Answers are asked for
 * as a stream (`streamGenerateContent?alt=sse`); the model's thoughts are left out of the answer. Model ids that
 * Google retired are mapped to their replacements, and a 404 is asked again with the model Google names instead.
 */

internal const val GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"

/** Gemini's wire, asking [rawModel] (normalized, see [normalizeGeminiModel]) with [apiKey]. */
internal class GeminiWire(
    private val apiKey: String,
    rawModel: String,
    private val endpoint: String = GEMINI_ENDPOINT
) : CloudWire {
    private val model = normalizeGeminiModel(rawModel)

    override val providerName = "Gemini"

    override fun request(builder: HttpRequestBuilder, prompt: String) = with(builder) {
        url("$endpoint/$model:streamGenerateContent?alt=sse")
        header("x-goog-api-key", apiKey)
        contentType(ContentType.Application.Json)
        setBody(geminiRequestBody(prompt))
    }

    override fun stream(onPartialText: ((String) -> Unit)?): StreamedAnswer = GeminiStream(onPartialText)

    override fun answer(status: Int, body: String, retryAfter: String?): Completion = geminiAnswer(status, body, retryAfter)

    /**
     * Google retires models often, and its 404 names the replacement ("use models/gemini-x"): that, or else the
     * default, so a stale choice keeps working.
     */
    override fun instead(status: Int, body: String): CloudWire? {
        if (status != 404) return null
        val replacement = suggestedGeminiModel(body)?.let(::normalizeGeminiModel)?.takeIf { it != model }
            ?: SettingsDefaults.GEMINI_MODEL.takeIf { it != model }
            ?: return null
        return GeminiWire(apiKey, replacement, endpoint)
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

/** How an answer that finished for [finishReason] ended. */
private fun geminiEnding(finishReason: String?): Ending? = when (finishReason) {
    null -> null
    "SAFETY" -> Ending.Declined("Gemini's safety filter blocked this text. Try other words, or pick another provider in Settings.")
    "MAX_TOKENS" -> Ending.OutOfRoom
    else -> Ending.Done
}

/**
 * Reads a Gemini `generateContent` response into a [TranslationResult]: the text of the first candidate's
 * answer, parsed with [parseTranslationResponse]. Errors and empty answers give a failed [TranslationResult]
 * with a clear user-facing [TranslationResult.errorMessage].
 */
internal fun parseGeminiResponse(status: Int, body: String): TranslationResult =
    geminiAnswer(status, body).toTranslation("Gemini's response couldn't be read. Try again.")

/**
 * Reads a Gemini answer that came whole, or an error.
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
    if (status >= 400 || response?.error != null) return geminiError(status, response?.error, retryAfter)
    val candidate = response?.candidates?.firstOrNull()
    val text = candidate?.content?.parts
        ?.filter { it.thought != true }
        ?.mapNotNull { it.text }
        ?.joinToString("")
        .orEmpty()
    return answerOutcome("Gemini", text, geminiEnding(candidate?.finishReason) ?: Ending.Done)
}

private fun geminiError(status: Int, error: GeminiError?, retryAfter: String?): Completion {
    val rawMsg = error?.message.orEmpty()
    val errStatus = error?.status.orEmpty()
    logError("GeminiWire", "Gemini returned $status: $rawMsg")
    // The requests per minute (or the day's quota) are used up. The first passes, so work that sends many requests waits it out.
    if (status == 429 || errStatus == "RESOURCE_EXHAUSTED") {
        return Completion.busy("Gemini rate limit or quota reached. Wait a moment or switch to another provider.", retryAfterHeaderSeconds(retryAfter))
    }
    if (status == 408 || status == 504 || errStatus == "DEADLINE_EXCEEDED") {
        return Completion.tooLarge(tookTooLongMessage("Gemini"))
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

/** A streamed Gemini answer: its candidates' text without the `thought: true` reasoning parts. */
internal class GeminiStream(onPartialText: ((String) -> Unit)? = null) : StreamedAnswer("Gemini", onPartialText) {
    override fun event(payload: String): Boolean {
        val event = try {
            json.decodeFromString(GenerateResponse.serializer(), payload)
        } catch (e: SerializationException) {
            return false
        } catch (e: IllegalArgumentException) {
            return false
        }
        event.error?.let {
            failure = geminiError(it.code ?: 500, it, retryAfter = null)
            return true
        }
        val candidate = event.candidates.firstOrNull() ?: return true
        candidate.content?.parts?.forEach { part -> if (part.thought != true && part.text != null) append(part.text) }
        geminiEnding(candidate.finishReason)?.let {
            ending = it
            flush()
        }
        return true
    }
}
