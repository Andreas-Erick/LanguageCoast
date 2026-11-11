package com.andreaserick.languagecoast.data

/*
 * A question the learner asks about one card while studying ("why is it 'dem' here?", "when would I say
 * this?"), answered by the AI provider picked in Settings. The model gets the whole card and what was
 * already asked about it, so a follow-up question can lean on the answer before it.
 */

/** A question about a card that the provider has answered. */
data class CardExchange(val question: String, val answer: String)

/** Questions offered in place of typing one, for a learner who doesn't know what to ask yet. */
val CARD_QUESTION_STARTERS = listOf("Break it down word by word", "When would I say this?", "Give me two more examples")

/** How many earlier exchanges a follow-up question is sent with; older ones are left out to keep the prompt short. */
const val CARD_QUESTION_MEMORY = 4

/**
 * The prompt that has a teacher of [targetLanguage] answer [question] about [card], in [nativeLanguage].
 * [region] is where the learner will use the language, if they said; [earlier] is what they already asked
 * about this card. The instruction comes last: whatever an engine appends to a prompt must not look like
 * part of the question.
 */
fun buildCardQuestionPrompt(
    card: Flashcard,
    nativeLanguage: String,
    targetLanguage: String,
    region: String?,
    earlier: List<CardExchange>,
    question: String
): String = buildString {
    appendLine("You are a friendly $targetLanguage teacher. A learner whose native language is $nativeLanguage is studying this flashcard:")
    appendLine()
    appendLine("$nativeLanguage: ${card.nativeText}")
    appendLine("$targetLanguage: ${card.targetText}")
    if (card.alternatives.isNotEmpty()) appendLine("Other correct translations on the card: ${card.alternatives.joinToString(" / ")}")
    card.note?.takeIf { it.isNotBlank() }?.let { appendLine("Note on the card: $it") }
    region?.trim()?.takeIf { it.isNotEmpty() }?.let { appendLine("The learner will use $targetLanguage in $it.") }
    appendLine()
    val remembered = earlier.takeLast(CARD_QUESTION_MEMORY)
    if (remembered.isNotEmpty()) {
        appendLine("Earlier in this conversation:")
        remembered.forEach { exchange ->
            appendLine("Learner: ${exchange.question}")
            appendLine("You: ${exchange.answer}")
        }
        appendLine()
    }
    appendLine("The learner asks: ${question.trim()}")
    appendLine()
    append(
        "Answer the learner's question about this flashcard in $nativeLanguage, in at most 120 words and plain text " +
            "(no markdown, no headings). Put $targetLanguage words and examples in quotation marks, with what they mean in $nativeLanguage."
    )
}

/**
 * The answer in a reply to [buildCardQuestionPrompt], without the markdown a model used anyway (the sheet
 * shows plain text); null when the reply is empty.
 */
fun parseCardAnswer(text: String): String? = text
    .replace("```", "")
    .replace("**", "")
    .lines()
    .joinToString("\n") { it.trimEnd().replace(MARKDOWN_HEADING, "").replace(MARKDOWN_BULLET, "$1• ") }
    .replace(BLANK_LINES, "\n\n")
    .trim()
    .takeIf { it.isNotEmpty() }

private val MARKDOWN_HEADING = Regex("^\\s*#{1,6}\\s+")
private val MARKDOWN_BULLET = Regex("^(\\s*)[*-]\\s+")
private val BLANK_LINES = Regex("\\n{3,}")
