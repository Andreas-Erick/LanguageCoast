package com.andreaserick.languagecoast.data

/**
 * The lowercase words of [text], with punctuation and extra spaces removed
 * ("Wo ist der Strand?" → [wo, ist, der, strand]). Accents are kept.
 */
fun normalizedWords(text: String): List<String> = text.lowercase()
    .map { if (it.isLetterOrDigit()) it else ' ' }
    .joinToString("")
    .split(' ')
    .filter { it.isNotEmpty() }

/** Whether [a] and [b] are the same sentence, ignoring case, punctuation and extra spaces. */
fun isSameSentence(a: String, b: String): Boolean = normalizedWords(a) == normalizedWords(b)

/**
 * Whether [card] matches the search [query]: every word of the query appears, ignoring case and
 * punctuation, in the card's native text, translation or alternatives. Words may be partial
 * ("bahn" finds "Bahnhof"). A query without any words matches nothing.
 */
fun matchesSearch(card: Flashcard, query: String): Boolean {
    val words = normalizedWords(query)
    if (words.isEmpty()) return false
    val texts = (listOf(card.nativeText, card.targetText) + card.alternatives).flatMap(::normalizedWords)
    return words.all { word -> texts.any { it.contains(word) } }
}
