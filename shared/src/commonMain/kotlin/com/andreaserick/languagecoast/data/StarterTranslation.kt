package com.andreaserick.languagecoast.data

/**
 * Translates starter sentences ([PhraseBank]) well and in bulk. A language model gets one prompt per
 * batch of a topic's sentences, with the rules a phrasebook needs (natural wording, the register you'd
 * use with a stranger, dictionary forms for single words, the phone's own wording for its settings, the
 * region the language is learned for), and sees the topic as a whole, so the translations are consistent
 * with each other. A second prompt per batch then reads the result as a native speaker would and
 * rewrites what one wouldn't say (see [naturalCheck]). On-device translation can only go sentence by
 * sentence, with no check.
 */
class StarterTranslator(private val translator: Translator) {

    /**
     * Why the last [translate] left sentences out, in words for the user (the provider's own reason: a key
     * it didn't accept, a limit, no connection), or null if it left none out or gave no reason.
     */
    var lastError: String? = null
        private set

    /**
     * [sentences] (English, blanks already filled) translated into [targetLanguage] as spoken in [region]
     * (null for no preference), and into [nativeLanguage] too when that isn't English, so the card's
     * front is in the user's language. Sentences that couldn't be translated are left out, and [lastError]
     * says why if the provider did. [onBatch] is told how many were done after each batch.
     *
     * A provider that fails [GIVE_UP_AFTER] requests in a row is not asked for the rest: it is down, out of
     * quota or, on the device, without its language pack, and the rest would only fail the same way.
     */
    suspend fun translate(
        engine: TranslationEngine,
        topic: PhraseBank.Theme,
        nativeLanguage: String,
        targetLanguage: String,
        region: String?,
        sentences: List<String>,
        wording: Wording = Wording.Unspecified,
        onBatch: suspend (done: Int) -> Unit = {}
    ): List<ProposedSentence> {
        lastError = null
        val results = ArrayList<ProposedSentence>(sentences.size)
        val isModel = engine != TranslationEngine.OnDevice
        var done = 0
        var failedInARow = 0
        for (batch in sentences.chunked(BATCH_SIZE)) {
            if (failedInARow >= GIVE_UP_AFTER) break
            val translated = ArrayList<ProposedSentence>(batch.size)
            if (isModel) {
                val byModel = askModel(engine, topic, nativeLanguage, targetLanguage, region, batch, wording)
                failedInARow = if (byModel == null) failedInARow + 1 else 0
                // Only lines an answered batch left out are asked for again, one by one. After a request that
                // failed, that would be up to thirty more requests to a provider that is turning them away.
                if (byModel != null) {
                    batch.mapNotNullTo(translated) { sentence ->
                        byModel[sentence] ?: oneByOne(engine, topic, nativeLanguage, targetLanguage, region, sentence, wording)
                    }
                }
            } else {
                for (sentence in batch) {
                    if (failedInARow >= GIVE_UP_AFTER) break
                    val proposed = oneByOne(engine, topic, nativeLanguage, targetLanguage, region, sentence, wording)
                    if (proposed == null) {
                        failedInARow++
                    } else {
                        failedInARow = 0
                        translated += proposed
                    }
                }
            }
            results += if (isModel) naturalCheck(engine, topic, nativeLanguage, targetLanguage, region, translated) else translated
            done += batch.size
            onBatch(done)
        }
        return results
    }

    /**
     * The batch through one prompt; sentences whose line is missing or unusable are left out of the map.
     * Null when the request itself failed (see [lastError]), which is not the same as an answer with lines missing.
     */
    private suspend fun askModel(
        engine: TranslationEngine,
        topic: PhraseBank.Theme,
        nativeLanguage: String,
        targetLanguage: String,
        region: String?,
        batch: List<String>,
        wording: Wording
    ): Map<String, ProposedSentence>? {
        // A topic is many requests in a row; a per-minute limit is waited out instead of ending the batch.
        val completion = translator.completeWaiting(engine, buildStarterTranslationPrompt(topic, nativeLanguage, targetLanguage, region, batch, wording))
        val text = completion.text
        if (text == null) {
            logError(TAG, "Bulk translation failed for ${topic.category}: ${completion.errorMessage}")
            completion.errorMessage?.let { lastError = it }
            return null
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

    /**
     * Reads [translated] as a native speaker from [region] would and, where one would say it differently,
     * makes that the translation: the first version moves to the card's alternatives and the reason becomes
     * its note, so the learner sees both and why. Entries the model doesn't flag are returned as they are.
     */
    suspend fun naturalCheck(
        engine: TranslationEngine,
        topic: PhraseBank.Theme,
        nativeLanguage: String,
        targetLanguage: String,
        region: String?,
        translated: List<ProposedSentence>
    ): List<ProposedSentence> {
        if (translated.isEmpty()) return translated
        val completion = translator.complete(engine, buildNaturalCheckPrompt(topic, nativeLanguage, targetLanguage, region, translated))
        val text = completion.text ?: return translated.also { logError(TAG, "Natural check failed for ${topic.category}: ${completion.errorMessage}") }
        val fixes = parseNaturalCheck(text, translated.size)
        return translated.mapIndexed { i, sentence ->
            val (better, reason) = fixes[i + 1] ?: return@mapIndexed sentence
            if (isSameSentence(better, sentence.targetText)) sentence
            else sentence.copy(targetText = better, alternatives = listOf(sentence.targetText), note = reason)
        }
    }

    /** One sentence through the regular translation request (on-device, or a line the model dropped). */
    private suspend fun oneByOne(
        engine: TranslationEngine,
        topic: PhraseBank.Theme,
        nativeLanguage: String,
        targetLanguage: String,
        region: String?,
        sentence: String,
        wording: Wording
    ): ProposedSentence? {
        val speaker = speakerRule(wording, targetLanguage).removePrefix("- ").let { if (it.isEmpty()) "" else " $it" }
        val context = "A phrasebook entry from the topic \"${topic.category}\" for a learner; " + topicRules(topic.category) + speaker
        val target = translateOnce(engine, sentence, BANK_LANGUAGE, targetLanguage, region, topic.category, context) ?: return null
        val native = if (nativeLanguage == BANK_LANGUAGE) sentence
        else translateOnce(engine, sentence, BANK_LANGUAGE, nativeLanguage, null, topic.category, context) ?: return null
        return ProposedSentence(native, target, topic.category, topic.emoji)
    }

    private suspend fun translateOnce(engine: TranslationEngine, sentence: String, from: String, to: String, region: String?, category: String, context: String): String? {
        val result = translator.translateAndCategorize(
            TranslationRequest(
                engine, sentence, nativeLanguage = from, targetLanguage = to, userCategory = category, existingCategories = emptyList(),
                context = context, targetRegion = region
            )
        )
        if (!result.isSuccess) result.errorMessage?.let { lastError = it }
        return result.translatedText.takeIf { result.isSuccess }
    }

    companion object {
        private const val TAG = "StarterTranslator"
        /** Sentences per prompt: enough for consistency, few enough that a small local model keeps the format. */
        const val BATCH_SIZE = 15
        /** After this many failed requests in a row, the rest of a [translate] call isn't sent. */
        const val GIVE_UP_AFTER = 2
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
    "Everyday Words", "Question Words", "Family & People", "Colours", "Food & Drink", "Clothes", "Body & Symptoms", "House & Furniture", "Places in Town" ->
        "give the dictionary form; for a noun include its article where the language has one, and the plural only if the English is plural."
    "Getting By" -> "these are said to a stranger, so use the polite form of address where the language has one."
    "Wishes" -> "give the set phrase people actually say on that occasion, not a literal translation."
    else -> "translate as a native speaker would naturally say it, using the polite form of address with strangers and shop staff where the language distinguishes."
}

/** "German as spoken in Switzerland", or just "German" when no region was given. */
fun languageIn(language: String, region: String?): String =
    region?.trim()?.takeIf { it.isNotEmpty() }?.let { "$language as spoken in $it" } ?: language

/**
 * The prompt that translates a batch of starter sentences from topic [topic] into [targetLanguage] (as spoken in
 * [region], if given), and into [nativeLanguage] as well when that isn't English. One line per sentence, numbered
 * as the input is.
 */
fun buildStarterTranslationPrompt(
    topic: PhraseBank.Theme,
    nativeLanguage: String,
    targetLanguage: String,
    region: String?,
    sentences: List<String>,
    wording: Wording = Wording.Unspecified
): String {
    val bothSides = nativeLanguage != StarterTranslator.BANK_LANGUAGE
    val target = languageIn(targetLanguage, region)
    val numbered = sentences.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
    val format = if (bothSides) "[number] || [the entry in $nativeLanguage] || [the entry in $targetLanguage]"
    else "[number] || [the entry in $targetLanguage]"
    val sides = if (bothSides) "Translate each into $nativeLanguage (the learner's own language, for the front of the card) and into $target (for the back)."
    else "Translate each into $target."
    val regionRule = region?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { "\n        - The learner will use this in $it: use the words, forms, spelling and register of $it, not of other places the language is spoken." }
        ?: ""
    val speakerLine = speakerRule(wording, targetLanguage).let { if (it.isEmpty()) "" else "\n        $it" }
    return """
        You are writing the $targetLanguage side of a phrasebook for a learner whose native language is $nativeLanguage. Accuracy matters: the learner will memorize exactly what you write, so a wrong or unnatural entry does lasting damage.

        The entries below all belong to the topic "${topic.category}". For this topic: ${topicRules(topic.category)}
        $sides

        Rules for every entry:
        - Natural, everyday wording that a native speaker would really use, not word for word. Keep the meaning and the tone (a question stays a question, a polite request stays polite).
        - Where the language distinguishes formal and informal address, use the form for a stranger, unless the entry is clearly said to a friend or family.$regionRule$speakerLine
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
 * The prompt that has a native speaker of [targetLanguage] (from [region], if given) read [translated] and say,
 * for each entry that isn't how people actually talk, what they would say instead and why.
 */
fun buildNaturalCheckPrompt(topic: PhraseBank.Theme, nativeLanguage: String, targetLanguage: String, region: String?, translated: List<ProposedSentence>): String {
    val where = region?.trim()?.takeIf { it.isNotEmpty() } ?: "the places where it is spoken"
    val numbered = translated.mapIndexed { i, s -> "${i + 1}. ${s.nativeText} => ${s.targetText}" }.joinToString("\n")
    return """
        You are a native speaker of $targetLanguage from $where, reviewing a learner's phrasebook. Each entry below is a sentence in the learner's language ($nativeLanguage) and the $targetLanguage version the learner is about to memorize, from the topic "${topic.category}".

        For each entry, decide whether the $targetLanguage is what people in $where actually say in that situation today. Correct grammar is not enough: flag anything stiff or textbook-like, a word or form that is not used in $where, a form of address people there wouldn't use, or a meaning that drifted. Do not flag entries that are fine; small matters of taste are fine.

        Entries:
        $numbered

        Reply with one line for each entry that should change, and nothing else, in this format:
        [number] || [what a native speaker in $where would say] || [why, in $nativeLanguage, one short sentence]
        If every entry is fine, reply with the single word NONE.
    """.trimIndent()
}

/** Parses a reply to [buildNaturalCheckPrompt]: entry number to (better phrasing, reason). NONE or chatter gives nothing. */
fun parseNaturalCheck(text: String, count: Int): Map<Int, Pair<String, String>> =
    parseStarterTranslations(text, count, bothSides = true)
        .mapValues { (_, sides) -> sides[0] to sides[1] }
        .filterValues { (better, _) -> better.isNotBlank() && !better.equals("NONE", ignoreCase = true) }

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

/** [value] without the square brackets a model copied from the prompt's format. */
internal fun unbracketed(value: String): String =
    if (value.length >= 2 && value.startsWith("[") && value.endsWith("]")) value.substring(1, value.length - 1).trim() else value
