package com.andreaserick.languagecoast.data

import android.util.Log
import com.google.genai.Client
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Translates sentences and assigns them to a category (island). */
interface Translator {
    /**
     * Translates [request]'s sentence into the target language and picks a category for it.
     * Never throws; failures are reported through [TranslationResult.isSuccess].
     */
    suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult
}

/** Where translations come from, as picked in Settings. */
enum class TranslationProvider(val label: String) {
    /** Google ML Kit on the phone: free and offline, but it only translates and can't pick a category. */
    OnDevice("On-device"),
    Gemini("Gemini"),
    /** One API key for many cloud models (GPT, Claude, Llama, …) through openrouter.ai. */
    OpenRouter("OpenRouter")
}

/** The provider for one translation, with what it needs to run. */
sealed interface TranslationEngine {
    data object OnDevice : TranslationEngine
    data class Gemini(val apiKey: String, val model: String) : TranslationEngine
    data class OpenRouter(val apiKey: String, val model: String) : TranslationEngine
}

/**
 * Everything needed for one translation.
 *
 * @property engine The provider that translates, with its key and model.
 * @property nativeSentence The original sentence in the user's native language.
 * @property nativeLanguage The language [nativeSentence] is written in.
 * @property targetLanguage The language to translate the sentence into.
 * @property userCategory An optional category suggested by the user (may be blank).
 * @property existingCategories Already created categories, to help the AI stay consistent.
 */
data class TranslationRequest(
    val engine: TranslationEngine,
    val nativeSentence: String,
    val nativeLanguage: String,
    val targetLanguage: String,
    val userCategory: String,
    val existingCategories: List<String>
)

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

/** Builds the prompt cloud models (Gemini, OpenRouter) get for [request]. */
internal fun buildTranslationPrompt(request: TranslationRequest): String = with(request) {
    val categoryText = if (existingCategories.isEmpty()) {
        "You have no existing categories yet. Generate a brand new, single-word category (the name of the category should be in English)."
    } else {
        "CRITICAL RULE: Prioritize choosing from this list of existing categories: ${
            existingCategories.joinToString(", ")
        }. Only create a brand new category (with the name of the category in English) if none of these fit. For example, possible categories could be: Small Talk, About Me, Asking for Help, Restaurant, Hotel, My Passions, Sport, Academia, Household Items and so on."
    }

    """
        You are a master language translator for a flashcard app.
        Translate the following input (which may be a single word or a full sentence) from $nativeLanguage into $targetLanguage.

        Accuracy is paramount. As these translations are intended for an educational flashcard app, users rely entirely on your output to build their foundational vocabulary and grammar. A single mistranslation or unnatural phrasing can severely derail a learner's progress, leading them to memorize incorrect terms for years to come. You must provide the most precise, natural, and contextually accurate translation possible.

        Input text: $nativeSentence

        If the user provided a category below, use it.
        User Category: $userCategory

        $categoryText

        SPECIAL RULE FOR ICELANDIC NOUNS:
        If the $targetLanguage is Icelandic AND the input text is a single word that is a noun, your translation must follow this exact format:
        [translated noun], [translated noun with definite article], [translated noun in plural]
        (Example: hestur, hesturinn, hestar)

        Also pick a single emoji that pictures the category (for example 🍽️ for Restaurant or ✈️ for Travel).

        Format your EXACT response like this (do not add any other text):
        TRANSLATION: [your translation]
        CATEGORY: [the category]
        EMOJI: [one emoji]
    """.trimIndent()
}

/**
 * Parses a model response of the form `TRANSLATION: ... CATEGORY: ... EMOJI: ...`.
 * Returns [TranslationResult.FAILURE] if the translation or category is missing or empty;
 * the emoji is optional and dropped if it doesn't look like one.
 */
internal fun parseTranslationResponse(responseText: String): TranslationResult {
    // Models occasionally wrap the labels in Markdown bold; strip that before parsing.
    val text = responseText.replace("**", "")
    if (!text.contains("TRANSLATION:") || !text.contains("CATEGORY:")) {
        return TranslationResult.FAILURE
    }

    val translation = text.substringAfter("TRANSLATION:").substringBefore("CATEGORY:").trim()
    val category = text.substringAfter("CATEGORY:").trim().lineSequence().first().trim()

    val emoji = if (text.contains("EMOJI:")) text.substringAfter("EMOJI:").trim().lineSequence().first().trim() else ""

    return if (translation.isNotEmpty() && category.isNotEmpty()) {
        TranslationResult(translation, category, isSuccess = true, emoji = emoji.takeIf(::looksLikeEmoji))
    } else {
        TranslationResult.FAILURE
    }
}

/** A short string with no letters or digits, i.e. an emoji (possibly with modifiers) rather than a word. */
private fun looksLikeEmoji(text: String): Boolean =
    text.isNotEmpty() && text.length <= 8 && text.none { it.isLetterOrDigit() }

/**
 * Result of an AI translation and categorization request.
 *
 * @property translatedText The text translated into the target language.
 * @property finalCategory The category assigned to the translation (from the user, an existing one, or newly generated).
 * @property isSuccess Whether the AI request and parsing succeeded.
 * @property emoji An emoji picturing the category, if the model gave a usable one.
 * @property errorMessage Why the translation failed, worded for the user, if known.
 */
data class TranslationResult(
    val translatedText: String,
    val finalCategory: String,
    val isSuccess: Boolean,
    val emoji: String? = null,
    val errorMessage: String? = null
) {
    companion object {
        val FAILURE = TranslationResult("Error: Could not translate", "Error", isSuccess = false)

        /** A failure with a message the user can act on. */
        fun failure(message: String) = FAILURE.copy(errorMessage = message)
    }
}

/** Category for cards whose translation came without one (manual or on-device) when the user left it blank. */
const val DEFAULT_CATEGORY = "My Words"
