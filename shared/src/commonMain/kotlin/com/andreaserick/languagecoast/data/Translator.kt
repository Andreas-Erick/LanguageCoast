package com.andreaserick.languagecoast.data

/** Translates sentences and assigns them to a category (island). */
interface Translator {
    /**
     * Translates [request]'s sentence into the target language and picks a category for it.
     * Never throws; failures are reported through [TranslationResult.isSuccess].
     */
    suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult
}

/** Where translations come from, as picked in Settings. */
enum class TranslationProvider(
    val label: String,
    val subtitle: String,
    val badge: String,
    val isCloud: Boolean
) {
    /** Google ML Kit on the phone: free and offline, but it only translates and can't pick a category. */
    OnDevice("On-device", "Google ML Kit · Fast basic translation", "Offline", isCloud = false),
    /** Local `.litertlm` LLM on the phone: free, private and offline with full categorization and alternatives. */
    LocalAi("Local AI", "On-device LLM · Full AI categorization offline", "Private AI", isCloud = false),
    /** Google Gemini API. */
    Gemini("Gemini", "Google AI Studio · Fast & accurate", "Google", isCloud = true),
    /** OpenAI ChatGPT / GPT-4.1 / GPT-4o API. */
    OpenAI("OpenAI", "GPT-4.1 & GPT-4o · High precision", "OpenAI", isCloud = true),
    /** Anthropic Claude API. */
    Claude("Claude", "Anthropic Claude · Natural phrasing & nuance", "Anthropic", isCloud = true),
    /** Groq Cloud ultra-fast LPU inference. */
    Groq("Groq", "Ultra-fast LPU · Llama & Qwen cloud models", "Ultra-Fast", isCloud = true),
    /** DeepSeek API. */
    DeepSeek("DeepSeek", "DeepSeek V3 · Cost-effective multilingual AI", "DeepSeek", isCloud = true),
    /** One API key for many cloud models (GPT, Claude, Llama, …) through openrouter.ai. */
    OpenRouter("OpenRouter", "All cloud models with one API key", "Multi-Model", isCloud = true)
}

/** The provider for one translation, with what it needs to run. */
sealed interface TranslationEngine {
    data object OnDevice : TranslationEngine
    data class LocalAi(
        val modelId: String = LocalAiCatalog.DEFAULT_MODEL_ID,
        val modelPath: String? = null
    ) : TranslationEngine
    data class Gemini(val apiKey: String, val model: String) : TranslationEngine
    data class OpenAI(val apiKey: String, val model: String) : TranslationEngine
    data class Claude(val apiKey: String, val model: String) : TranslationEngine
    data class Groq(val apiKey: String, val model: String) : TranslationEngine
    data class DeepSeek(val apiKey: String, val model: String) : TranslationEngine
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

/** Builds the prompt AI models (Gemini, OpenAI, Claude, Groq, DeepSeek, OpenRouter, Local AI) get for [request]. */
fun buildTranslationPrompt(request: TranslationRequest): String = with(request) {
    val categoryText = if (existingCategories.isEmpty()) {
        "You have no existing categories yet. Generate a brand new, single-word category (the name of the category should be in English)."
    } else {
        "CRITICAL RULE: Prioritize choosing from this list of existing categories: ${
            existingCategories.joinToString(", ")
        }. Only create a brand new category (with the name of the category in English) if none of these fit. For example, possible categories could be: Small Talk, About Me, Asking for Help, Restaurant, Hotel, My Passions, Sport, Academia, Household Items and so on."
    }

    val instructions = """
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
    """.trimIndent()

    // Longer sentences are where several translations can be right, so ask what a learner should know about them.
    val alternativesText = if (wantsAlternatives(nativeSentence)) {
        "\n\nThis input is long enough that more than one translation can be right. Also give up to two other natural " +
            "translations that differ in a way a learner should know (formality, register, word choice or regional usage), " +
            "and one short sentence in $nativeLanguage explaining how they differ. In that sentence, refer to each version by " +
            "its wording (e.g. \"du\" vs. \"Sie\"), never by position such as \"the first alternative\", because the user can reorder them. " +
            "If there is no meaningful alternative, write NONE for both."
    } else ""

    val format = buildString {
        appendLine("Format your EXACT response like this (do not add any other text):")
        appendLine("TRANSLATION: [your translation]")
        appendLine("CATEGORY: [the category]")
        append("EMOJI: [one emoji]")
        if (wantsAlternatives(nativeSentence)) {
            appendLine()
            appendLine("ALTERNATIVES: [alternative 1] || [alternative 2]")
            append("NOTE: [one sentence]")
        }
    }

    "$instructions$alternativesText\n\n$format"
}

/** Inputs with at least this many words get alternative translations (see [buildTranslationPrompt]). */
const val ALTERNATIVES_MIN_WORDS = 6

/** Whether [sentence] is long enough to ask for alternative translations. */
internal fun wantsAlternatives(sentence: String): Boolean =
    sentence.split(Regex("\\s+")).count { it.isNotBlank() } >= ALTERNATIVES_MIN_WORDS

private val KNOWN_LABELS = listOf("TRANSLATION", "CATEGORY", "EMOJI", "ALTERNATIVES", "NOTE")

/**
 * Parses a model response of the form `TRANSLATION: ... CATEGORY: ... EMOJI: ...`, optionally followed by
 * `ALTERNATIVES: a || b` and `NOTE: ...`.
 *
 * Resilient against markdown formatting (`**`, code fences, bullet points), case variations (`Translation:`),
 * reordered labels (e.g. `EMOJI:` appearing before `CATEGORY:`), and literal `[...]` brackets around values.
 * Returns [TranslationResult.FAILURE] if the translation or category is missing or empty;
 * the emoji is optional and dropped if it doesn't look like one, and "NONE" means no alternatives or note.
 */
fun parseTranslationResponse(responseText: String): TranslationResult {
    // Strip markdown bold, code fences, and normalize known field labels to uppercase so case-insensitive
    // model outputs ("Translation:", "- **Category**:") parse reliably.
    val cleaned = responseText
        .replace(Regex("```[a-zA-Z]*"), "")
        .replace("```", "")
        .replace("**", "")
        .replace(Regex("(?m)^\\s*[-*•#]+\\s*(?=(?:translation|category|emoji|alternatives|note)\\s*:)", RegexOption.IGNORE_CASE), "")

    val normalized = KNOWN_LABELS.fold(cleaned) { acc, label ->
        acc.replace(Regex("(?i)\\b$label\\s*:"), "$label:")
    }

    if (!normalized.contains("TRANSLATION:") || !normalized.contains("CATEGORY:")) {
        return TranslationResult.FAILURE
    }

    fun extractField(label: String, singleLineOnly: Boolean): String {
        val marker = "$label:"
        val startIndex = normalized.indexOf(marker)
        if (startIndex == -1) return ""
        val afterMarker = normalized.substring(startIndex + marker.length)

        // Stop at the earliest occurrence of any other known label.
        val nextLabelOffset = KNOWN_LABELS
            .filter { it != label }
            .mapNotNull { other ->
                val idx = afterMarker.indexOf("$other:")
                if (idx >= 0) idx else null
            }
            .minOrNull()

        val rawSegment = if (nextLabelOffset != null) {
            afterMarker.substring(0, nextLabelOffset)
        } else {
            afterMarker
        }

        val value = if (singleLineOnly) {
            rawSegment.trim().lineSequence().firstOrNull()?.trim().orEmpty()
        } else {
            rawSegment.trim().lines().firstOrNull { it.isNotBlank() }?.trim() ?: rawSegment.trim()
        }
        return stripPromptBrackets(value)
    }

    val translation = extractField("TRANSLATION", singleLineOnly = false)
    val category = extractField("CATEGORY", singleLineOnly = true)
    val emoji = extractField("EMOJI", singleLineOnly = true)

    val alternatives = extractField("ALTERNATIVES", singleLineOnly = true)
        .split("||")
        .map { stripPromptBrackets(it.trim()) }
        .filter { it.isNotEmpty() && !it.equals("NONE", ignoreCase = true) && it != translation }
        .distinct()
        .take(2)
    val note = extractField("NOTE", singleLineOnly = true)
        .takeIf { it.isNotEmpty() && !it.equals("NONE", ignoreCase = true) && alternatives.isNotEmpty() }

    return if (translation.isNotEmpty() && category.isNotEmpty()) {
        TranslationResult(
            translation,
            category,
            isSuccess = true,
            emoji = emoji.takeIf(::looksLikeEmoji),
            alternatives = alternatives,
            note = note
        )
    } else {
        TranslationResult.FAILURE
    }
}

/** Strips literal `[` and `]` if a model echoed the prompt's placeholder brackets (e.g. `[Greetings]` -> `Greetings`). */
private fun stripPromptBrackets(value: String): String {
    val trimmed = value.trim()
    return if (trimmed.length >= 2 && trimmed.startsWith("[") && trimmed.endsWith("]") && !trimmed.substring(1, trimmed.length - 1).contains("[")) {
        trimmed.substring(1, trimmed.length - 1).trim()
    } else {
        trimmed
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
 * @property alternatives Other correct translations of a longer sentence (at most two), for the user to pick from.
 * @property note How [alternatives] differ from the translation (e.g. formal vs. informal), if there are any.
 */
data class TranslationResult(
    val translatedText: String,
    val finalCategory: String,
    val isSuccess: Boolean,
    val emoji: String? = null,
    val errorMessage: String? = null,
    val alternatives: List<String> = emptyList(),
    val note: String? = null
) {
    companion object {
        val FAILURE = TranslationResult("Error: Could not translate", "Error", isSuccess = false)

        /** A failure with a message the user can act on. */
        fun failure(message: String) = FAILURE.copy(errorMessage = message)
    }
}

/** Category for cards whose translation came without one (manual or on-device) when the user left it blank. */
const val DEFAULT_CATEGORY = "My Words"
