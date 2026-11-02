package com.andreaserick.languagecoast.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

/** A card the user keeps forgetting: graded Again at least twice, or rated hard by the scheduler. */
fun isLeaky(card: Flashcard): Boolean = card.lapses >= LEAKY_LAPSES || (card.difficulty ?: 0.0) >= LEAKY_DIFFICULTY

const val LEAKY_LAPSES = 2
const val LEAKY_DIFFICULTY = 7.5

/**
 * [cards] with coasts taking turns, so a session across coasts doesn't do all of one language first.
 * Within a coast the order is kept.
 */
fun interleaveByCoast(cards: List<PlacedCard>): List<PlacedCard> {
    val queues = cards.groupBy { it.coastId }.values.map { ArrayDeque(it) }
    val result = ArrayList<PlacedCard>(cards.size)
    while (queues.any { it.isNotEmpty() }) {
        queues.forEach { queue -> queue.removeFirstOrNull()?.let(result::add) }
    }
    return result
}

/** The Monday of the week [day] is in. */
fun startOfWeek(day: LocalDate): LocalDate = day.minus(day.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

// ---------- Cloze ----------

/** A sentence with one word blanked out, for the "fill the gap" study mode. */
data class Cloze(val word: String, val before: String, val after: String) {
    /** The sentence with the gap shown as a line of underscores. */
    val display: String get() = "$before${"_".repeat(word.length.coerceIn(4, 10))}$after"
}

/**
 * [sentence] with its most telling word blanked: the longest one, since short words are mostly
 * articles and prepositions. Null for a single word, which would leave nothing to go on.
 */
fun clozeOf(sentence: String): Cloze? {
    val tokens = Regex("[\\p{L}\\p{N}']+").findAll(sentence).toList()
    if (tokens.size < 2) return null
    val target = tokens.maxWith(compareBy<MatchResult> { it.value.length }.thenBy { -it.range.first })
    return Cloze(
        word = target.value,
        before = sentence.substring(0, target.range.first),
        after = sentence.substring(target.range.last + 1)
    )
}

// ---------- Answer checking ----------

/** How a typed answer compares with what was expected. */
sealed interface AnswerMatch {
    /** Right, allowing for case, punctuation and spacing. */
    data object Exact : AnswerMatch
    /** A slip of a letter or two: [typed] against the [expected] it nearly matched. */
    data class Almost(val expected: String, val typed: String) : AnswerMatch
    data object Wrong : AnswerMatch
}

/**
 * Compares [typed] with each of [expected]: exact if it matches any (see [isSameSentence]), almost if
 * it is within a letter or two of one (one slip for short answers, two for long ones), otherwise wrong.
 * Accents still count: "Strasse" is not "Straße".
 */
fun checkAnswer(typed: String, expected: List<String>): AnswerMatch {
    val candidates = expected.filter { it.isNotBlank() }
    if (candidates.any { isSameSentence(typed, it) }) return AnswerMatch.Exact
    val typedNormalized = normalizedWords(typed).joinToString(" ")
    val nearest = candidates
        .map { it to levenshtein(typedNormalized, normalizedWords(it).joinToString(" ")) }
        .minByOrNull { it.second } ?: return AnswerMatch.Wrong
    val allowed = when {
        typedNormalized.length < 5 -> 0
        typedNormalized.length < 12 -> 1
        else -> 2
    }
    return if (nearest.second in 1..allowed) AnswerMatch.Almost(nearest.first, typed.trim()) else AnswerMatch.Wrong
}

/** Edit distance between [a] and [b]. */
internal fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[b.length]
}

// ---------- Island suggestion ----------

/**
 * The island whose cards share the most words with [text], or null if nothing overlaps or [text] has
 * fewer than two words. Short words (articles, prepositions) don't count.
 */
fun suggestIsland(text: String, cards: List<PlacedCard>): String? {
    val words = normalizedWords(text).filter { it.length >= 4 }.toSet()
    if (words.size < 2 && normalizedWords(text).size < 2) return null
    if (words.isEmpty()) return null
    val scores = cards.groupBy { it.islandName }.mapValues { (_, islandCards) ->
        islandCards.sumOf { card -> normalizedWords(card.card.nativeText).filter { it.length >= 4 }.count { it in words } }
    }
    return scores.filterValues { it > 0 }.maxByOrNull { it.value }?.key
}
