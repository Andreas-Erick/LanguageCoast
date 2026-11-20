package com.andreaserick.languagecoast.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
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
 * [Translator] backed by Google Gemini through its REST API
 * (https://ai.google.dev/api/generate-content); expects a [TranslationEngine.Gemini] request.
 *
 * Reads answers as server-sent event streams (`streamGenerateContent?alt=sse`) so models that think
 * before answering over a batch of hard practice-sheet sentences keep the connection alive and report
 * incremental progress, with automatic normalization and 404 fallback for retired model names, transient
 * retry on 502/503, and `MAX_TOKENS`/timeout batch-halving signals.
 */
class GeminiTranslator(private val client: HttpClient) : Translator {

    override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult =
        complete(request.engine, buildTranslationPrompt(request), patient = false, onPartialText = null)
            .toTranslation("Gemini's response couldn't be read. Try again.")

    override suspend fun complete(engine: TranslationEngine, prompt: String): Completion =
        complete(engine, prompt, patient = true, onPartialText = null)

    override suspend fun complete(engine: TranslationEngine, prompt: String, onPartialText: ((String) -> Unit)?): Completion =
        complete(engine, prompt, patient = true, onPartialText = onPartialText)

    private suspend fun complete(
        engine: TranslationEngine,
        prompt: String,
        patient: Boolean,
        onPartialText: ((String) -> Unit)?
    ): Completion {
        if (engine !is TranslationEngine.Gemini) return Completion.failure("This provider can't answer here.")
        val apiKey = engine.apiKey.trim()
        if (apiKey.isBlank()) {
            return Completion.failure("Gemini needs an API key. Add one in Settings.")
        }

        val primaryModel = normalizeGeminiModel(engine.model)
        val promptBody = geminiRequestBody(prompt)
        val reqTimeout = if (patient) GEMINI_TIMEOUT_MILLIS else GEMINI_SHORT_TIMEOUT_MILLIS
        val silenceTimeout = if (patient) GEMINI_SILENCE_MILLIS else GEMINI_SHORT_SILENCE_MILLIS

        val first = askWithRetries(apiKey, primaryModel, promptBody, reqTimeout, silenceTimeout, onPartialText)
        if (first is Attempt.HttpError && first.status == 404) {
            // Google retires models often, and its 404 names the replacement ("use models/gemini-x"):
            // try that, or else the default, so a stale choice keeps translating.
            val replacement = suggestedGeminiModel(first.body)?.let(::normalizeGeminiModel)?.takeIf { it != primaryModel }
                ?: SettingsDefaults.GEMINI_MODEL.takeIf { it != primaryModel }
            if (replacement != null) {
                logError(TAG, "Model $primaryModel not found, retrying with $replacement")
                val fallback = askWithRetries(apiKey, replacement, promptBody, reqTimeout, silenceTimeout, onPartialText)
                return fallback.toCompletion()
            }
        }
        return first.toCompletion()
    }

    private sealed interface Attempt {
        data class Done(val completion: Completion) : Attempt
        data class HttpError(val status: Int, val body: String, val retryAfter: String?) : Attempt {
            fun isTransient(): Boolean =
                status in setOf(502, 503) ||
                    (status == 429 && (parseRetryAfterMillis(retryAfter) ?: 0L) <= 2_000L)
        }

        fun toCompletion(): Completion = when (this) {
            is Done -> completion
            is HttpError -> geminiAnswer(status, body, retryAfter)
        }
    }

    private suspend fun askWithRetries(
        apiKey: String,
        model: String,
        body: String,
        requestTimeoutMillis: Long,
        silenceMillis: Long,
        onPartialText: ((String) -> Unit)?
    ): Attempt = retryHttpWithBackoff(
        shouldRetry = { it is Attempt.HttpError && it.isTransient() },
        retryAfterMillis = { (it as? Attempt.HttpError)?.let { err -> parseRetryAfterMillis(err.retryAfter) } }
    ) {
        streamGenerateContent(apiKey, model, body, requestTimeoutMillis, silenceMillis, onPartialText)
    }

    private suspend fun streamGenerateContent(
        apiKey: String,
        model: String,
        body: String,
        requestTimeoutMillis: Long,
        silenceMillis: Long,
        onPartialText: ((String) -> Unit)?
    ): Attempt {
        var answering = false
        return try {
            client.preparePost("$ENDPOINT/$model:streamGenerateContent?alt=sse") {
                header("x-goog-api-key", apiKey)
                timeout {
                    this.requestTimeoutMillis = requestTimeoutMillis
                    this.socketTimeoutMillis = silenceMillis
                }
                contentType(ContentType.Application.Json)
                setBody(body)
            }.execute { response ->
                answering = true
                val status = response.status.value
                val isStream = response.contentType()?.match(ContentType.Text.EventStream) == true
                if (status >= 400) {
                    Attempt.HttpError(status, response.bodyAsText(), response.headers["retry-after"])
                } else if (!isStream) {
                    Attempt.Done(geminiAnswer(status, response.bodyAsText(), response.headers["retry-after"]))
                } else {
                    val stream = GeminiStream(onPartialText)
                    val channel = response.bodyAsChannel()
                    while (true) {
                        val line = channel.readUTF8Line() ?: break
                        stream.read(line)
                    }
                    Attempt.Done(stream.completion())
                }
            }
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            logError(TAG, "Gemini took too long to answer", e)
            Attempt.Done(Completion.tooLarge(tookTooLongMessage("Gemini")))
        } catch (e: Exception) {
            if (answering && ranOutOfTime(e)) {
                logError(TAG, "Gemini took too long to answer", e)
                Attempt.Done(Completion.tooLarge(tookTooLongMessage("Gemini")))
            } else {
                logError(TAG, "Translation request failed", e)
                Attempt.Done(Completion.failure("Couldn't reach Gemini. Check your internet connection and try again."))
            }
        }
    }

    private companion object {
        const val TAG = "GeminiTranslator"
        const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
    }
}

/** Backstop and silence limits for streamed Gemini requests; thinking models emit thought events while reasoning. */
internal const val GEMINI_TIMEOUT_MILLIS = 600_000L
internal const val GEMINI_SILENCE_MILLIS = 300_000L
internal const val GEMINI_SHORT_TIMEOUT_MILLIS = 120_000L
internal const val GEMINI_SHORT_SILENCE_MILLIS = 125_000L

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
        val errStatus = response?.error?.status.orEmpty()
        logError("GeminiTranslator", "Gemini returned $status: $rawMsg")
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
    val candidate = response?.candidates?.firstOrNull()
    val text = candidate?.content?.parts
        ?.filter { it.thought != true }
        ?.mapNotNull { it.text }
        ?.joinToString("")
        .orEmpty()
    return geminiOutcome(text, candidate?.finishReason, brokeOff = false)
}

/**
 * Assembles a streamed Gemini `streamGenerateContent?alt=sse` response from its `data:` JSON events,
 * ignoring `thought: true` reasoning parts while notifying [onPartialText] as complete lines arrive.
 */
internal class GeminiStream(private val onPartialText: ((String) -> Unit)? = null) {
    private val text = StringBuilder()
    private var finishReason: String? = null
    private var error: GeminiError? = null
    private var sawEvent = false

    fun read(line: String) {
        if (!line.startsWith("data:")) return
        val payload = line.substring(5).trim()
        if (payload.isEmpty() || payload == "[DONE]") return
        val event = try {
            json.decodeFromString(GenerateResponse.serializer(), payload)
        } catch (e: SerializationException) {
            return
        } catch (e: IllegalArgumentException) {
            return
        }
        sawEvent = true
        if (event.error != null) {
            error = event.error
            return
        }
        val candidate = event.candidates.firstOrNull() ?: return
        candidate.finishReason?.let { finishReason = it }
        var sawNewline = false
        candidate.content?.parts?.forEach { part ->
            if (part.thought != true && part.text != null) {
                text.append(part.text)
                if ('\n' in part.text) sawNewline = true
            }
        }
        if (sawNewline || candidate.finishReason != null) {
            if (text.isNotEmpty()) onPartialText?.invoke(text.toString())
        }
    }

    fun completion(): Completion {
        val err = error
        if (err != null) {
            val code = err.code ?: 500
            return geminiAnswer(code, json.encodeToString(GenerateResponse.serializer(), GenerateResponse(error = err)))
        }
        val brokeOff = sawEvent && finishReason == null
        return geminiOutcome(text.toString(), finishReason, brokeOff = brokeOff)
    }
}

/**
 * Turns Gemini's accumulated [text] and [finishReason] into a [Completion], keeping complete lines when an
 * answer hit `MAX_TOKENS` or [brokeOff] mid-stream and flagging empty `MAX_TOKENS` replies as [Completion.tooLarge]
 * so a worksheet request halves its batch size instead of giving up.
 */
internal fun geminiOutcome(text: String, finishReason: String?, brokeOff: Boolean = false): Completion {
    if (finishReason == "SAFETY") {
        return Completion.failure("Gemini's safety filter blocked this text. Try other words, or pick another provider in Settings.")
    }
    val outOfRoom = finishReason == "MAX_TOKENS"
    val whole = if (outOfRoom || brokeOff) text.substringBeforeLast('\n', missingDelimiterValue = "") else text
    if (whole.isBlank()) {
        return when {
            outOfRoom -> Completion.tooLarge("Gemini ran out of room before it had answered. Ask for less at a time, or pick another model in Settings.")
            brokeOff -> Completion.failure("Gemini's answer broke off. Check your internet connection and try again.")
            else -> Completion.failure("Gemini returned an empty answer. Try again or pick another model.")
        }
    }
    return Completion(whole.trim())
}
