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
 * [Translator] for OpenAI, Anthropic Claude, Groq, and DeepSeek cloud APIs.
 */
class MultiCloudTranslator(private val client: HttpClient) : Translator {

    override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult =
        complete(request.engine, buildTranslationPrompt(request)).toTranslation()

    override suspend fun complete(engine: TranslationEngine, prompt: String): Completion {
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
                prompt = prompt
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
            return Completion.failure("Please enter a $providerName API key in Settings first!")
        }
        return try {
            val response = client.post(endpoint) {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(openRouterRequestBody(model, prompt))
            }
            cloudChatAnswer(providerName, response.status.value, response.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(TAG, "$providerName translation request failed", e)
            Completion.failure("Couldn't reach $providerName. Check your internet connection and try again.")
        }
    }

    private suspend fun callClaude(
        apiKey: String,
        model: String,
        prompt: String
    ): Completion {
        if (apiKey.isBlank()) {
            return Completion.failure("Please enter an Anthropic Claude API key in Settings first!")
        }
        return try {
            val response = client.post(CLAUDE_ENDPOINT) {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(claudeRequestBody(model, prompt))
            }
            claudeAnswer(response.status.value, response.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(TAG, "Claude translation request failed", e)
            Completion.failure("Couldn't reach Anthropic Claude. Check your internet connection and try again.")
        }
    }

    private companion object {
        const val TAG = "MultiCloudTranslator"
        const val OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions"
        const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        const val DEEPSEEK_ENDPOINT = "https://api.deepseek.com/chat/completions"
        const val CLAUDE_ENDPOINT = "https://api.anthropic.com/v1/messages"
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

/** [parseCloudChatResponse] without reading the answer as a translation. */
internal fun cloudChatAnswer(providerName: String, status: Int, body: String): Completion {
    val parsed = try {
        cloudJson.decodeFromString(CloudChatResponse.serializer(), body)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
    if (status >= 400 || parsed?.error != null) {
        val errMsg = parsed?.error?.message
        val message = when (status) {
            401 -> "$providerName didn't accept the API key. Check your key in Settings."
            402 -> "Your $providerName account has insufficient quota or credits."
            404 -> "The selected $providerName model wasn't found. Pick another model in Settings."
            429 -> "$providerName rate limit reached. Wait a moment and try again."
            else -> "$providerName: ${errMsg ?: "request failed (HTTP $status)"}"
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

@Serializable
private data class ClaudeRequest(
    val model: String,
    val max_tokens: Int = 512,
    val messages: List<ClaudeMessage>
)

@Serializable
private data class ClaudeMessage(val role: String, val content: String)

@Serializable
private data class ClaudeResponse(
    val content: List<ClaudeContentBlock> = emptyList(),
    val error: CloudApiError? = null
)

@Serializable
private data class ClaudeContentBlock(val type: String? = null, val text: String? = null)

internal fun claudeRequestBody(model: String, prompt: String): String =
    cloudJson.encodeToString(
        ClaudeRequest.serializer(),
        ClaudeRequest(model = model, max_tokens = 512, messages = listOf(ClaudeMessage("user", prompt)))
    )

/** [parseClaudeResponse] without reading the answer as a translation. */
internal fun claudeAnswer(status: Int, body: String): Completion {
    val parsed = try {
        cloudJson.decodeFromString(ClaudeResponse.serializer(), body)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
    if (status >= 400 || parsed?.error != null) {
        val errMsg = parsed?.error?.message
        val message = when (status) {
            401, 403 -> "Claude didn't accept the API key. Check your Anthropic key in Settings."
            404 -> "The selected Claude model wasn't found. Pick another model in Settings."
            429, 529 -> "Claude is busy or rate-limited. Wait a moment and try again."
            else -> "Claude: ${errMsg ?: "request failed (HTTP $status)"}"
        }
        return Completion.failure(message)
    }
    val text = parsed?.content
        ?.filter { it.type == null || it.type == "text" }
        ?.mapNotNull { it.text }
        ?.joinToString("")
        ?.takeIf { it.isNotBlank() }
        ?: return Completion.failure("Claude returned an empty answer. Try again.")
    return Completion(text.trim())
}

/** Reads a Claude messages response into a [TranslationResult]. */
internal fun parseClaudeResponse(status: Int, body: String): TranslationResult =
    claudeAnswer(status, body).toTranslation("Claude's answer couldn't be read. Try again.")
