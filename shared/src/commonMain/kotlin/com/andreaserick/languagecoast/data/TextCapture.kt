package com.andreaserick.languagecoast.data

/** The most sentences one batch capture offers, so a whole page doesn't become hundreds of cards. */
const val BATCH_MAX_SENTENCES = 40

/**
 * Splits pasted or scanned [text] into sentences worth a card each: at sentence-ending punctuation and
 * line breaks, trimmed, without duplicates, bullets or stray fragments under three letters.
 */
fun splitSentences(text: String): List<String> {
    val pieces = text
        .replace("\r", "")
        .split(Regex("(?<=[.!?…])\\s+|\\n+"))
        .map { it.trim().trimStart('-', '•', '*', '–', ' ').trim() }
        .filter { piece -> piece.count { it.isLetter() } >= 3 }
    return pieces.distinctBy { normalizedWords(it).joinToString(" ") }.take(BATCH_MAX_SENTENCES)
}
