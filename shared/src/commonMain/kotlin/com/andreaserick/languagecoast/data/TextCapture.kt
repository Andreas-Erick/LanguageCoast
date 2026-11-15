package com.andreaserick.languagecoast.data

/** The most sentences one batch capture offers, so a whole page doesn't become hundreds of cards. */
const val BATCH_MAX_SENTENCES = 40

/**
 * Where a sentence ends: after `.`, `!`, `?` or `…` followed by a space, right after the full-width marks
 * Japanese and Chinese end a sentence with (which no space follows), and at line breaks.
 */
private val SENTENCE_END = Regex("(?<=[.!?…])\\s+|(?<=[。！？])|\\n+")

/**
 * Splits pasted or scanned [text] into sentences worth a card each: at sentence-ending punctuation and
 * line breaks, trimmed, without duplicates, bullets or stray fragments under three letters.
 */
fun splitSentences(text: String): List<String> {
    val pieces = text
        .replace("\r", "")
        .split(SENTENCE_END)
        .map { it.trim().trimStart('-', '•', '*', '–', ' ').trim() }
        .filter { piece -> piece.count { it.isLetter() } >= 3 }
    return pieces.distinctBy { normalizedWords(it).joinToString(" ") }.take(BATCH_MAX_SENTENCES)
}
