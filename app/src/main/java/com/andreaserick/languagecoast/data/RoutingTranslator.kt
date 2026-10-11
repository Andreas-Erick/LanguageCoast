package com.andreaserick.languagecoast.data

import android.util.Log
import com.google.genai.Client
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** The production [Translator]: hands each request to the translator for its [TranslationRequest.engine]. */
class RoutingTranslator @Inject constructor(
    private val onDevice: OnDeviceTranslator,
    private val gemini: GeminiTranslator,
    private val openRouter: OpenRouterTranslator
) : Translator {
    override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult = when (request.engine) {
        TranslationEngine.OnDevice -> onDevice.translateAndCategorize(request)
        is TranslationEngine.Gemini -> gemini.translateAndCategorize(request)
        is TranslationEngine.OpenRouter -> openRouter.translateAndCategorize(request)
    }
}

/** [Translator] backed by Google Gemini; expects a [TranslationEngine.Gemini] request. */
@Singleton
class GeminiTranslator @Inject constructor() : Translator {

    private var client: Client? = null
    private var clientApiKey: String? = null

    /** Reuses the Gemini client until the API key changes. */
    @Synchronized
    private fun clientFor(apiKey: String): Client =
        client?.takeIf { clientApiKey == apiKey }
            ?: Client.builder().apiKey(apiKey).build().also {
                client = it
                clientApiKey = apiKey
            }

    override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult {
        val engine = request.engine as? TranslationEngine.Gemini ?: return TranslationResult.FAILURE
        val prompt = buildTranslationPrompt(request)
        return try {
            val responseText = withContext(Dispatchers.IO) {
                // The trailing null uses the default GenerateContentConfig.
                clientFor(engine.apiKey).models.generateContent(engine.model, prompt, null).text()
                    ?: throw IllegalStateException("Empty response from AI")
            }
            parseTranslationResponse(responseText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Translation request failed", e)
            TranslationResult.FAILURE
        }
    }

    private companion object {
        const val TAG = "GeminiTranslator"
    }
}
