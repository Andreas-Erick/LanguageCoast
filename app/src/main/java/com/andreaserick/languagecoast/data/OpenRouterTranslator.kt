package com.andreaserick.languagecoast.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [Translator] backed by OpenRouter (https://openrouter.ai), which gives one API key access to many
 * cloud models. Uses the same prompt and answer format as Gemini; expects a [TranslationEngine.OpenRouter] request.
 */
@Singleton
class OpenRouterTranslator @Inject constructor() : Translator {

    override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult {
        val engine = request.engine as? TranslationEngine.OpenRouter ?: return TranslationResult.FAILURE
        val body = openRouterRequestBody(engine.model, buildTranslationPrompt(request))
        return try {
            val (status, response) = withContext(Dispatchers.IO) { post(engine.apiKey, body) }
            parseOpenRouterResponse(status, response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "OpenRouter request failed", e)
            TranslationResult.failure("Couldn't reach OpenRouter. Check your internet connection and try again.")
        }
    }

    /** Sends [body] to the chat completions endpoint and returns the status code and response body. */
    private fun post(apiKey: String, body: String): Pair<Int, String> {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            // Optional attribution, shown on openrouter.ai.
            connection.setRequestProperty("HTTP-Referer", "https://github.com/Andreas-Erick/LanguageCoast")
            connection.setRequestProperty("X-Title", "Language Coast")
            connection.outputStream.use { it.write(body.toByteArray()) }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            return status to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            connection.disconnect()
        }
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
internal fun parseOpenRouterResponse(status: Int, body: String): TranslationResult {
    val response = try {
        json.decodeFromString(ChatResponse.serializer(), body)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
    val error = response?.error
    if (status >= 400 || error != null) {
        val message = when (error?.code ?: status) {
            401 -> "OpenRouter didn't accept the API key. Check it in Settings."
            402 -> "Your OpenRouter account is out of credits."
            429 -> "OpenRouter is busy. Wait a moment and try again."
            else -> "OpenRouter: ${error?.message ?: "request failed ($status)"}"
        }
        return TranslationResult.failure(message)
    }
    val content = response?.choices?.firstOrNull()?.message?.content
        ?: return TranslationResult.failure("OpenRouter sent an empty answer. Try again or pick another model.")
    return parseTranslationResponse(content).let {
        if (it.isSuccess) it else TranslationResult.failure("The model's answer couldn't be read. Try again or pick another model.")
    }
}
