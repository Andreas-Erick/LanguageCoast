package com.andreaserick.languagecoast.data

/**
 * Translates starter sentences ([PhraseBank]) well and in bulk. A language model gets one prompt per
 * batch of a topic's sentences, with the rules a phrasebook needs (natural wording, the register you'd
 * use with a stranger, dictionary forms for single words, the phone's own wording for its settings),
 * and sees the topic as a whole, so the translations are consistent with each other. On-device
 * translation can only go sentence by sentence.
 */
class StarterTranslator(private val translator: Translator) {

    /**
     * [sentences] (English, blanks already filled) translated into [targetLanguage], and into
     * [nativeLanguage] too when that isn't English, so the card's front is in the user's language.
     * Sentences that couldn't be translated are left out. [onBatch] is told how many were done after each batch.
     */
    suspend fun translate(
        engine: TranslationEngine,
        topic: PhraseBank.Theme,
        nativeLanguage: String,
        targetLanguage: String,
        sentences: List<String>,
        onBatch: suspend (done: Int) -> Unit = {}
    ): List<ProposedSentence> {
        val results = ArrayList<ProposedSentence>(sentences.size)
        var done = 0
        for (batch in sentences.chunked(BATCH_SIZE)) {
            val byModel = if (engine == TranslationEngine.OnDevice) emptyMap() else askModel(engine, topic, nativeLanguage, targetLanguage, batch)
            for (sentence in batch) {
                val translated = byModel[sentence] ?: oneByOne(engine, topic, nativeLanguage, targetLanguage, sentence)
                if (translated != null) results += translated
            }
            done += batch.size
            onBatch(done)
        }
        return results
    }

    /** The batch through one prompt; sentences whose line is missing or unusable are left out of the map. */
    private suspend fun askModel(
        engine: TranslationEngine,
        topic: PhraseBank.Theme,
        nativeLanguage: String,
        targetLanguage: String,
        batch: List<String>
    ): Map<String, ProposedSentence> {
        val completion = translator.complete(engine, buildStarterTranslationPrompt(topic, nativeLanguage, targetLanguage, batch))
        val text = completion.text
        if (text == null) {
            logError(TAG, "Bulk translation failed for ${topic.category}: ${completion.errorMessage}")
            return emptyMap()
        }
        val bothSides = nativeLanguage != BANK_LANGUAGE
        return parseStarterTranslations(text, batch.size, bothSides).mapNotNull { (index, sides) ->
            val english = batch[index - 1]
            val native = if (bothSides) sides.first() else english
            val target = sides.last()
            // A model that echoes the English back hasn't translated; let the single-sentence path retry.
            if (target.isBlank() || (targetLanguage != BANK_LANGUAGE && isSameSentence(target, english) && english.length > 6)) null
            else english to ProposedSentence(native, target, topic.category, topic.emoji)
        }.toMap()
    }

    /** One sentence through the regular translation request (on-device, or a line the model dropped). */
    private suspend fun oneByOne(
        engine: TranslationEngine,
        topic: PhraseBank.Theme,
        nativeLanguage: String,
        targetLanguage: String,
        sentence: String
    ): ProposedSentence? {
        val context = "A phrasebook entry from the topic \"${topic.category}\" for a learner; " + topicRules(topic.category)
        val target = translateOnce(engine, sentence, BANK_LANGUAGE, targetLanguage, topic.category, context) ?: return null
        val native = if (nativeLanguage == BANK_LANGUAGE) sentence
        else translateOnce(engine, sentence, BANK_LANGUAGE, nativeLanguage, topic.category, context) ?: return null
        return ProposedSentence(native, target, topic.category, topic.emoji)
    }

    private suspend fun translateOnce(engine: TranslationEngine, sentence: String, from: String, to: String, category: String, context: String): String? {
        val result = translator.translateAndCategorize(
            TranslationRequest(engine, sentence, nativeLanguage = from, targetLanguage = to, userCategory = category, existingCategories = emptyList(), context = context)
        )
        return result.translatedText.takeIf { result.isSuccess }
    }

    companion object {
        private const val TAG = "StarterTranslator"
        /** Sentences per prompt: enough for consistency, few enough that a small local model keeps the format. */
        const val BATCH_SIZE = 15
        /** The bank's sentences are written in English. */
        const val BANK_LANGUAGE = "English"
    }
}

/** What a translator must know about a topic beyond its name. */
internal fun topicRules(category: String): String = when (category) {
    "Phone Settings" -> "use the exact wording the phone's own system settings (Android and iOS) show in that language, capitalized as on screen, not a literal translation."
    "Numbers" -> "write numbers as words, never digits; for ordinals and fractions give the usual spoken form."
    "Days & Months" -> "give the plain name as a calendar shows it, capitalized as the language does."
    "Signs" -> "give the wording actually printed on signs and doors in that language, not a sentence."
    "Everyday Words", "Question Words", "Family & People", "Colours", "Food & Drink" ->
        "give the dictionary form; for a noun include its article where the language has one, and the plural only if the English is plural."
    "Getting By" -> "these are said to a stranger, so use the polite form of address where the language has one."
    "Wishes" -> "give the set phrase people actually say on that occasion, not a literal translation."
    else -> "translate as a native speaker would naturally say it, using the polite form of address with strangers and shop staff where the language distinguishes."
}

/**
 * The prompt that translates a batch of starter sentences from topic [topic] into [targetLanguage], and into
 * [nativeLanguage] as well when that isn't English. One line per sentence, numbered as the input is.
 */
fun buildStarterTranslationPrompt(topic: PhraseBank.Theme, nativeLanguage: String, targetLanguage: String, sentences: List<String>): String {
    val bothSides = nativeLanguage != StarterTranslator.BANK_LANGUAGE
    val numbered = sentences.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
    val format = if (bothSides) "[number] || [the entry in $nativeLanguage] || [the entry in $targetLanguage]"
    else "[number] || [the entry in $targetLanguage]"
    val sides = if (bothSides) "Translate each into $nativeLanguage (the learner's own language, for the front of the card) and into $targetLanguage (for the back)."
    else "Translate each into $targetLanguage."
    return """
        You are writing the $targetLanguage side of a phrasebook for a learner whose native language is $nativeLanguage. Accuracy matters: the learner will memorize exactly what you write, so a wrong or unnatural entry does lasting damage.

        The entries below all belong to the topic "${topic.category}". For this topic: ${topicRules(topic.category)}
        $sides

        Rules for every entry:
        - Natural, everyday wording that a native speaker would really use, not word for word. Keep the meaning and the tone (a question stays a question, a polite request stays polite).
        - Where the language distinguishes formal and informal address, use the form for a stranger, unless the entry is clearly said to a friend or family.
        - Names, cities and other filled-in words stay as they are, adapted only as grammar requires.
        - Use the punctuation and capitalization conventions of $targetLanguage.
        - Translate every entry on its own; do not merge, skip or reorder them, and add nothing.

        Entries:
        $numbered

        Reply with EXACTLY one line per entry, nothing else, in this format:
        $format
    """.trimIndent()
}

/**
 * Parses a reply to [buildStarterTranslationPrompt]: a map from the entry's number (1-based, at most [count]) to its
 * sides, the last of which is the target-language entry (with [bothSides], the first is the native-language one).
 * Numbering styles, bullets, markdown and the prompt's brackets are tolerated; lines that don't fit are dropped.
 */
fun parseStarterTranslations(text: String, count: Int, bothSides: Boolean): Map<Int, List<String>> {
    val cleaned = text.replace("```", "").replace("**", "")
    val line = Regex("^\\s*[-*•]?\\s*(\\d+)\\s*[.):]?\\s*\\|\\|(.+)$")
    val result = LinkedHashMap<Int, List<String>>()
    for (raw in cleaned.lines()) {
        val match = line.find(raw.trim()) ?: continue
        val index = match.groupValues[1].toIntOrNull() ?: continue
        if (index !in 1..count || index in result) continue
        val sides = match.groupValues[2].split("||").map { unbracketed(it.trim()) }.filter { it.isNotEmpty() }
        val needed = if (bothSides) 2 else 1
        if (sides.size < needed) continue
        result[index] = sides.takeLast(needed)
    }
    return result
}

private fun unbracketed(value: String): String =
    if (value.length >= 2 && value.startsWith("[") && value.endsWith("]")) value.substring(1, value.length - 1).trim() else value
