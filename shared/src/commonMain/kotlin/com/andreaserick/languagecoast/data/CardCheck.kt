package com.andreaserick.languagecoast.data

/**
 * What a language model found when it read one card as a native speaker would (see [buildCardCheckPrompt]).
 * Asked for by the user on a card they doubt, so it costs requests only then, and works on cards made by
 * any provider, including on-device translation, which can't check itself.
 *
 * The check has two steps. First the model translates the card's translation back without seeing what the
 * card is meant to say ([buildCardReadingPrompt]); the user is shown that reading and can compare it
 * themselves, so they need not take the verdict on trust. Then it gives its verdict with both sides and the
 * reading in view.
 */
sealed interface CardVerdict {
    /** The translation means the same and is what people say. */
    data object Fine : CardVerdict

    /**
     * It should be [better]; [reason] says why, in the learner's language.
     *
     * @property currentIsWrong The card's translation has a mistake or means something else, so it must not stay
     *     as an alternative; false when it is correct but not what people say.
     */
    data class Better(val better: String, val reason: String, val currentIsWrong: Boolean) : CardVerdict
}

/**
 * The prompt that reads [targetText] back into [nativeLanguage], word for word. The model isn't told what the
 * card is meant to say, so the reading can't lean on it. Short, so that small local models manage it too, and
 * with the instruction last: whatever an engine appends to a prompt must not look like part of the text.
 */
fun buildCardReadingPrompt(targetText: String, targetLanguage: String, nativeLanguage: String): String = """
    $targetLanguage text: $targetText

    Translate the $targetLanguage text above into $nativeLanguage as literally as you can, word for word, even where that sounds odd in $nativeLanguage. Reply with the $nativeLanguage translation only, on one line, and nothing else.
""".trimIndent()

/**
 * The reading in a reply to [buildCardReadingPrompt], or null when there is none worth showing: an empty
 * reply, or one that only repeats [targetText] (a small model that didn't translate).
 */
fun parseCardReading(text: String, targetText: String): String? {
    val line = checkLines(text).firstOrNull() ?: return null
    val reading = unquoted(unbracketed(line.replace(READING_LABEL, "").trim()))
    return reading.takeIf { it.isNotEmpty() && !isSameSentence(it, targetText) }
}

/**
 * The prompt that has a native speaker of [targetLanguage] (from [region], if given) read one card: whether
 * [targetText] means what [nativeText] says, and whether people say it that way. [reading] is the earlier
 * word-for-word reading of [targetText], if there is one: comparing two sentences in one language is something
 * even a small model can do. The model must name what is wrong to ask for a change, so it doesn't invent problems.
 */
fun buildCardCheckPrompt(
    nativeText: String,
    targetText: String,
    nativeLanguage: String,
    targetLanguage: String,
    region: String?,
    reading: String? = null
): String {
    val where = region?.trim()?.takeIf { it.isNotEmpty() } ?: "the places where it is spoken"
    // Raw strings don't process escapes, so the line break is built outside it.
    val readingLine = if (reading == null) "" else "\n        Read back word for word, the $targetLanguage says: $reading"
    val compare = if (reading == null) "Does the $targetLanguage mean the same as the $nativeLanguage"
    else "Does the $targetLanguage, as read back, mean the same as the $nativeLanguage"
    return """
        You are a native speaker of $targetLanguage from $where, checking one flashcard that a learner is memorizing. The learner's native language is $nativeLanguage.

        $nativeLanguage: $nativeText
        $targetLanguage: $targetText$readingLine

        Decide two things. $compare, with nothing lost, added or changed? And is the $targetLanguage correct, and what people in $where actually say in that situation today?

        Ask for a change only when you can name what is wrong: a meaning that differs, a grammar or spelling mistake, a word or form that is not used in $where, a form of address people there wouldn't use, or wording so stiff that nobody says it. A card with nothing wrong stays as it is: another equally good way to say it is not a reason, and neither is taste. A single word given in its dictionary form, with its article or with its other forms listed, is fine as it is.

        Reply in exactly this format and nothing else:
        VERDICT: [FINE if nothing is wrong, CHANGE if something is]
        Only when the verdict is CHANGE, add these three lines:
        BETTER: [the whole $targetLanguage side as it should be]
        WHY: [what the difference is, in $nativeLanguage, one short sentence]
        CURRENT: [WRONG if the current $targetLanguage has a mistake or means something else, OK if it is correct but not what people say]
    """.trimIndent()
}

/**
 * Parses a reply to [buildCardCheckPrompt] for a card whose translation is [current]. A suggestion that only
 * repeats [current] counts as fine; a reply that is neither FINE nor a suggestion gives null.
 */
fun parseCardCheck(text: String, current: String): CardVerdict? {
    val lines = checkLines(text)
    val better = field(lines, "BETTER")?.takeUnless { it.equals("NONE", ignoreCase = true) || it.equals("FINE", ignoreCase = true) }
    return when {
        field(lines, "VERDICT")?.let(FINE::containsMatchIn) == true -> CardVerdict.Fine
        better != null && !isSameSentence(better, current) ->
            CardVerdict.Better(better, field(lines, "WHY").orEmpty(), currentIsWrong = field(lines, "CURRENT")?.startsWith("WRONG", ignoreCase = true) == true)
        better != null || lines.any { FINE.containsMatchIn(it) } -> CardVerdict.Fine
        else -> null
    }
}

private fun checkLines(text: String): List<String> =
    text.replace("```", "").replace("**", "").lines().map { it.trim() }.filter { it.isNotEmpty() }

/** The value of the line "[label]: value" in [lines], without the prompt's brackets; null if missing or empty. */
private fun field(lines: List<String>, label: String): String? = lines.firstOrNull { it.startsWith("$label:", ignoreCase = true) }
    ?.substringAfter(':')?.trim()?.let(::unbracketed)?.takeIf { it.isNotEmpty() }

/** [value] without the quotation marks a model wrapped it in. */
private fun unquoted(value: String): String =
    if (value.length > 2 && value.first() in "\"“„«" && value.last() in "\"”“»") value.substring(1, value.length - 1).trim() else value

/** A line that says the card is fine: "FINE", "Fine." or "FINE: nothing to change". */
private val FINE = Regex("^FINE\\b", RegexOption.IGNORE_CASE)

/** A label a model put before its reading: "Translation:", "English translation:". */
private val READING_LABEL = Regex("^(\\p{L}+ )?(translation|reads)\\s*:\\s*", RegexOption.IGNORE_CASE)
