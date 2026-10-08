package com.andreaserick.languagecoast.data

import android.util.Log
import com.google.genai.Client
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles communication with Google Gemini to translate sentences and categorize them.
 *
 * @param apiKey The Google Gemini API key used for authentication.
 */
class AiTranslator(apiKey: String) {

    private val client = Client.builder().apiKey(apiKey).build()

    /**
     * Translates a given sentence into the target language and assigns it a category.
     * It uses a list of existing categories to encourage consistency in categorization.
     *
     * @param nativeSentence The original sentence in the user's native language.
     * @param targetLanguage The language to translate the sentence into.
     * @param nativeLanguage The language [nativeSentence] is written in.
     * @param userCategory An optional category suggested by the user (may be blank).
     * @param existingCategories Already created categories, to help the AI stay consistent.
     * @param modelName The Gemini model to use.
     * @return A [TranslationResult] containing the translated text, the chosen category, and success status.
     */
    suspend fun translateAndCategorize(
        nativeSentence: String,
        targetLanguage: String,
        nativeLanguage: String,
        userCategory: String,
        existingCategories: List<String>,
        modelName: String = SettingsManager.DEFAULT_GEMINI_MODEL
    ): TranslationResult {

        val categoryText = if (existingCategories.isEmpty()) {
            "You have no existing categories yet. Generate a brand new, single-word category (the name of the category should be in English)."
        } else {
            "CRITICAL RULE: Prioritize choosing from this list of existing categories: ${
                existingCategories.joinToString(", ")
            }. Only create a brand new category (with the name of the category in English) if none of these fit. For example, possible categories could be: Small Talk, About Me, Asking for Help, Restaurant, Hotel, My Passions, Sport, Academia, Household Items and so on."
        }

        val prompt = """
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

            Format your EXACT response like this (do not add any other text):
            TRANSLATION: [your translation]
            CATEGORY: [the category]
        """.trimIndent()

        return try {
            val responseText = withContext(Dispatchers.IO) {
                // The trailing null uses the default GenerateContentConfig.
                client.models.generateContent(modelName, prompt, null).text()
                    ?: throw IllegalStateException("Empty response from AI")
            }
            parseTranslationResponse(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "Translation request failed", e)
            TranslationResult.FAILURE
        }
    }

    private companion object {
        const val TAG = "AiTranslator"
    }
}

/**
 * Parses a model response of the form `TRANSLATION: ... CATEGORY: ...`.
 * Returns [TranslationResult.FAILURE] if either field is missing or empty.
 */
internal fun parseTranslationResponse(responseText: String): TranslationResult {
    // Models occasionally wrap the labels in Markdown bold; strip that before parsing.
    val text = responseText.replace("**", "")
    if (!text.contains("TRANSLATION:") || !text.contains("CATEGORY:")) {
        return TranslationResult.FAILURE
    }

    val translation = text.substringAfter("TRANSLATION:").substringBefore("CATEGORY:").trim()
    val category = text.substringAfter("CATEGORY:").trim().lineSequence().first().trim()

    return if (translation.isNotEmpty() && category.isNotEmpty()) {
        TranslationResult(translation, category, isSuccess = true)
    } else {
        TranslationResult.FAILURE
    }
}

/**
 * Result of an AI translation and categorization request.
 *
 * @property translatedText The text translated into the target language.
 * @property finalCategory The category assigned to the translation (from the user, an existing one, or newly generated).
 * @property isSuccess Whether the AI request and parsing succeeded.
 */
data class TranslationResult(
    val translatedText: String,
    val finalCategory: String,
    val isSuccess: Boolean
) {
    companion object {
        val FAILURE = TranslationResult("Error: Could not translate", "Error", isSuccess = false)
    }
}
