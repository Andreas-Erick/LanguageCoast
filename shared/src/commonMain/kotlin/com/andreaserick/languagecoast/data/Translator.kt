package com.andreaserick.languagecoast.data

/** Translates sentences and assigns them to a category (island). */
interface Translator {
    /**
     * Translates [request]'s sentence into the target language and picks a category for it.
     * Never throws; failures are reported through [TranslationResult.isSuccess].
     */
    suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult

    /**
     * Answers [prompt] with [engine] as plain text, e.g. to explain a word. Never throws. Providers that
     * only translate (on-device ML Kit) can't, and say so in [Completion.errorMessage].
     */
    suspend fun complete(engine: TranslationEngine, prompt: String): Completion =
        Completion.failure("This provider only translates. Pick an AI provider in Settings to get explanations.")

    /** Gets [engine] ready ahead of its first request (loads a local model); a no-op for most providers. */
    suspend fun prepare(engine: TranslationEngine) = Unit
}

/** A model's free-form answer to a prompt, or why there is none. */
data class Completion(val text: String?, val errorMessage: String? = null) {
    val isSuccess: Boolean get() = text != null

    companion object {
        fun failure(message: String) = Completion(null, message)
    }
}

/**
 * The prompt that explains [word] as used in [sentence] (in [targetLanguage]) to a learner whose language is
 * [nativeLanguage]: meaning, grammar and one more example, kept short enough for a sheet.
 */
fun buildExplainPrompt(word: String, sentence: String, targetLanguage: String, nativeLanguage: String): String = """
    You are a friendly $targetLanguage teacher. A learner whose native language is $nativeLanguage is studying this $targetLanguage sentence:
    "$sentence"
    Explain the word "$word" as it is used there, in $nativeLanguage, in at most 90 words and plain text (no markdown, no headings):
    1. What it means here and its dictionary form (for a noun its gender and plural, for a verb its infinitive).
    2. Why it takes this form in the sentence (case, tense, agreement) if that matters.
    3. One more short example sentence in $targetLanguage with its $nativeLanguage translation.
""".trimIndent()

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
 * @property writtenInTarget The user wrote [nativeSentence] in [targetLanguage] themselves: the model translates it
 *     back into [nativeLanguage] and corrects mistakes (see [TranslationResult.corrected]).
 */
data class TranslationRequest(
    val engine: TranslationEngine,
    val nativeSentence: String,
    val nativeLanguage: String,
    val targetLanguage: String,
    val userCategory: String,
    val existingCategories: List<String>,
    val writtenInTarget: Boolean = false
)

/** Builds the prompt AI models (Gemini, OpenAI, Claude, Groq, DeepSeek, OpenRouter, Local AI) get for [request]. */
fun buildTranslationPrompt(request: TranslationRequest): String = with(request) {
    if (writtenInTarget) return buildReversePrompt(request)
    // Never leave the label empty: a small model copies "User Category:" back as "(empty)".
    val userCategoryText = if (userCategory.isBlank()) "The user did not choose a category." else "The user chose this category; use it: $userCategory"
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

        $userCategoryText

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

/**
 * The prompt for a sentence the user wrote in the target language themselves: translate it back into their
 * language, and correct it if it has mistakes.
 */
private fun buildReversePrompt(request: TranslationRequest): String = with(request) {
    val userCategoryText = if (userCategory.isBlank()) "The user did not choose a category." else "The user chose this category; use it: $userCategory"
    val categoryText = if (existingCategories.isEmpty()) {
        "You have no existing categories yet. Generate a brand new, single-word category (the name of the category should be in English)."
    } else {
        "CRITICAL RULE: Prioritize choosing from this list of existing categories: ${existingCategories.joinToString(", ")}. " +
            "Only create a brand new category (with the name of the category in English) if none of these fit."
    }
    """
        You are a patient $targetLanguage teacher for a flashcard app.
        A learner whose native language is $nativeLanguage tried to write this in $targetLanguage:

        Learner's text: $nativeSentence

        First, translate what the learner meant into natural $nativeLanguage.
        Then check the learner's $targetLanguage for mistakes in spelling, grammar, word choice or word order. If it has any, give the corrected $targetLanguage sentence (keep the learner's wording wherever it is right) and one short sentence in $nativeLanguage saying what was wrong. If it is already correct and natural, write NONE for both.

        $userCategoryText

        $categoryText

        Also pick a single emoji that pictures the category.

        Format your EXACT response like this (do not add any other text):
        TRANSLATION: [the meaning in $nativeLanguage]
        CORRECTED: [the corrected $targetLanguage sentence, or NONE]
        NOTE: [what was wrong, in $nativeLanguage, or NONE]
        CATEGORY: [the category]
        EMOJI: [one emoji]
    """.trimIndent()
}

/** Inputs with at least this many words get alternative translations (see [buildTranslationPrompt]). */
const val ALTERNATIVES_MIN_WORDS = 6

/** Whether [sentence] is long enough to ask for alternative translations. */
internal fun wantsAlternatives(sentence: String): Boolean =
    sentence.split(Regex("\\s+")).count { it.isNotBlank() } >= ALTERNATIVES_MIN_WORDS

private val KNOWN_LABELS = listOf("TRANSLATION", "CATEGORY", "EMOJI", "ALTERNATIVES", "NOTE", "CORRECTED")

/**
 * Parses a model response of the form `TRANSLATION: ... CATEGORY: ... EMOJI: ...`, optionally followed by
 * `ALTERNATIVES: a || b` and `NOTE: ...`.
 *
 * Resilient against markdown formatting (`**`, code fences, bullet points), case variations (`Translation:`),
 * reordered labels (e.g. `EMOJI:` appearing before `CATEGORY:`), and literal `[...]` brackets around values.
 * Returns [TranslationResult.FAILURE] if the translation or category is missing or empty;
 * the emoji is optional and dropped if it doesn't look like one, and "NONE" means no alternatives or note.
 */
/** Things a model writes in place of a category when it has none; never an island name. */
private val CATEGORY_PLACEHOLDERS = setOf("", "(empty)", "empty", "none", "n/a", "na", "null", "-", "unknown", "the category", "category")

/**
 * Parses a model's reply into a [TranslationResult].
 *
 * @param fallbackCategory Used when the reply's category is missing or a placeholder like "(empty)";
 *     without one, such a reply fails.
 */
fun parseTranslationResponse(responseText: String, fallbackCategory: String? = null): TranslationResult {
    // Strip markdown bold, code fences, and normalize known field labels to uppercase so case-insensitive
    // model outputs ("Translation:", "- **Category**:") parse reliably.
    val cleaned = responseText
        .replace(Regex("```[a-zA-Z]*"), "")
        .replace("```", "")
        .replace("**", "")
        .replace(Regex("(?m)^\\s*[-*•#]+\\s*(?=(?:translation|category|emoji|alternatives|note|corrected)\\s*:)", RegexOption.IGNORE_CASE), "")

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
        .trim('"', '\'', '*', '[', ']', '(', ')', ' ')
        .let { if (it.lowercase() in CATEGORY_PLACEHOLDERS) fallbackCategory.orEmpty() else it }
    val emoji = extractField("EMOJI", singleLineOnly = true)

    val alternatives = extractField("ALTERNATIVES", singleLineOnly = true)
        .split("||")
        .map { stripPromptBrackets(it.trim()) }
        .filter { it.isNotEmpty() && !it.equals("NONE", ignoreCase = true) && it != translation }
        .distinct()
        .take(2)
    val corrected = extractField("CORRECTED", singleLineOnly = true)
        .takeIf { it.isNotEmpty() && !it.equals("NONE", ignoreCase = true) }
    // A note explains the alternatives, or what was corrected; without either there is nothing to explain.
    val note = extractField("NOTE", singleLineOnly = true)
        .takeIf { it.isNotEmpty() && !it.equals("NONE", ignoreCase = true) && (alternatives.isNotEmpty() || corrected != null) }

    return if (translation.isNotEmpty() && category.isNotEmpty()) {
        TranslationResult(
            translation,
            category,
            isSuccess = true,
            emoji = emoji.takeIf(::looksLikeEmoji),
            alternatives = alternatives,
            note = note,
            corrected = corrected
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
 * @property note How [alternatives] differ from the translation (e.g. formal vs. informal), or what [corrected] fixed.
 * @property corrected For a sentence the user wrote in the target language ([TranslationRequest.writtenInTarget]):
 *     the corrected sentence if theirs had mistakes, or null if it was right.
 */
data class TranslationResult(
    val translatedText: String,
    val finalCategory: String,
    val isSuccess: Boolean,
    val emoji: String? = null,
    val errorMessage: String? = null,
    val alternatives: List<String> = emptyList(),
    val note: String? = null,
    val corrected: String? = null
) {
    companion object {
        val FAILURE = TranslationResult("Error: Could not translate", "Error", isSuccess = false)

        /** A failure with a message the user can act on. */
        fun failure(message: String) = FAILURE.copy(errorMessage = message)
    }
}

/** Category for cards whose translation came without one (manual or on-device) when the user left it blank. */
const val DEFAULT_CATEGORY = "My Words"
