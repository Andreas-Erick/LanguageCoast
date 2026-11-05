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

/**
 * What a language model said about another translation the learner proposed for a card ("is this correct
 * as well?", see [buildAlternativeCheckPrompt]).
 */
sealed interface AlternativeVerdict {
    /** It is correct as well. [note] says how it differs from the card's translation, if in a way worth knowing. */
    data class Correct(val note: String?) : AlternativeVerdict

    /** It isn't. [reason] says why, in the learner's language; [corrected] is their sentence put right, if a small fix does it. */
    data class Incorrect(val reason: String, val corrected: String?) : AlternativeVerdict

    /** The card has it already, as its translation or as an alternative, so there was nothing to ask. */
    data object OnTheCard : AlternativeVerdict
}

/**
 * The prompt that has a native speaker of [targetLanguage] (from [region], if given) judge [proposed], the
 * learner's own way of saying what [nativeText] says, next to the card's [targetText]. It is judged on its
 * own merits: it need not resemble the card, but it must mean the same and be something people say.
 */
fun buildAlternativeCheckPrompt(
    nativeText: String,
    targetText: String,
    proposed: String,
    nativeLanguage: String,
    targetLanguage: String,
    region: String?
): String {
    val where = region?.trim()?.takeIf { it.isNotEmpty() } ?: "the places where it is spoken"
    return """
        You are a native speaker of $targetLanguage from $where, helping a learner whose native language is $nativeLanguage. Their flashcard says:

        $nativeLanguage: $nativeText
        $targetLanguage: $targetText

        The learner asks whether this is correct as well: $proposed

        Judge the learner's $targetLanguage on its own. It does not have to resemble the flashcard: other words, another word order or another register are fine. It is correct as well when it means what the $nativeLanguage says, with nothing lost, added or changed, has no grammar or spelling mistake, and is something people in $where say. Do not mind capital letters or punctuation at the end. If it means something else, has a mistake or is wording nobody would use, it is not.

        Reply in exactly this format and nothing else:
        VERDICT: [YES if it is correct as well, NO if it is not]
        WHY: [in $nativeLanguage, one short sentence. For YES: how it differs from the flashcard's $targetLanguage in register, emphasis or region, quoting the wording it is about, or NONE if they are interchangeable. For NO: what is wrong]
        FIXED: [only for NO, and only if a small correction makes it right: the learner's $targetLanguage corrected. Otherwise NONE]
    """.trimIndent()
}

/**
 * Parses a reply to [buildAlternativeCheckPrompt] about [proposed]. A YES that comes with a different
 * sentence under FIXED counts as a no, since the model changed something after all; a reply without a
 * verdict gives null.
 */
fun parseAlternativeCheck(text: String, proposed: String): AlternativeVerdict? {
    val lines = checkLines(text)
    fun value(label: String) = field(lines, label)?.takeUnless { it.equals("NONE", ignoreCase = true) }
    val verdict = field(lines, "VERDICT") ?: lines.firstOrNull { YES.containsMatchIn(it) || NO.containsMatchIn(it) } ?: return null
    val why = value("WHY")
    val fixed = value("FIXED")?.let(::unquoted)?.takeUnless { isSameSentence(it, proposed) }
    return when {
        YES.containsMatchIn(verdict) && fixed == null -> AlternativeVerdict.Correct(why)
        YES.containsMatchIn(verdict) || NO.containsMatchIn(verdict) -> AlternativeVerdict.Incorrect(why.orEmpty(), fixed)
        else -> null
    }
}

/** A verdict that says yes ("YES", "Yes, it is") or no ("NO", "No."). */
private val YES = Regex("^YES\\b", RegexOption.IGNORE_CASE)
private val NO = Regex("^NO\\b", RegexOption.IGNORE_CASE)

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
