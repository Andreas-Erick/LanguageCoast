package com.andreaserick.languagecoast.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.math.ln
import kotlin.random.Random
import kotlin.time.Clock

/* Which of the sentences a model wrote go on the sheet: those closest to the coast's words, and no repeats. */


/**
 * The words a coast's cards teach (translations and alternatives), to tell how far a sentence strays from
 * them. It knows nothing about grammar: a word counts as known when it is on a card or starts like a word
 * that is ("gehe" for "gehen", "Kindern" for "Kind"), so irregular forms count as unknown and some
 * strangers slip through. That is good enough to rank sentences against each other, which is all it is
 * used for; it is not a verdict on any single sentence.
 */
class CoastVocabulary(texts: List<String>) {
    private val wordCounts = HashMap<String, Int>()
    private val foldedWords = HashSet<String>()
    private val stems = HashSet<String>()
    private val foldedStems = HashSet<String>()
    private val characters = HashSet<Char>()
    private var textCount = 0

    init {
        // A text at a time: a large coast's words, all in one list, would be millions.
        val seenInText = HashSet<String>()
        for (text in texts) {
            seenInText.clear()
            val normalized = normalizedWords(text)
            if (normalized.isNotEmpty()) textCount++
            for (word in normalized) {
                val isFirst = word !in wordCounts
                if (seenInText.add(word)) wordCounts[word] = (wordCounts[word] ?: 0) + 1
                if (!isFirst) continue
                for (length in stemLength(word.length, STEM_SHARE)..word.length) stems += word.substring(0, length)
                val folded = foldWord(word)
                if (folded != word && foldedWords.add(folded)) {
                    for (length in stemLength(folded.length, STEM_SHARE)..folded.length) foldedStems += folded.substring(0, length)
                }
                word.forEach { if (writtenWithoutSpaces(it)) characters += it }
            }
        }
    }

    /** The words of [sentence] that are not the coast's: for scripts written without spaces, the characters. */
    fun unknownWords(sentence: String): List<String> = normalizedWords(sentence).flatMap { word ->
        when {
            word.any(::writtenWithoutSpaces) -> word.filter { writtenWithoutSpaces(it) && it !in characters }.map { it.toString() }
            isKnown(word) -> emptyList()
            else -> listOf(word)
        }
    }

    /** How much of [sentence] is not the coast's, from 0 (every word is known) to 1 (none is). */
    fun unknownShare(sentence: String): Double {
        val all = normalizedWords(sentence).sumOf { word -> if (word.any(::writtenWithoutSpaces)) word.count(::writtenWithoutSpaces).coerceAtLeast(1) else 1 }
        return if (all == 0) 1.0 else unknownWords(sentence).size.toDouble() / all
    }

    /**
     * Whether [sentence] keeps to the coast's words, the [newWords] new ones a sheet's sentence may bring
     * aside. In a script without spaces a new word shows as its characters, mostly two of them.
     */
    fun keepsToCoast(sentence: String, newWords: Int = WORKSHEET_NEW_WORDS): Boolean {
        val unknown = unknownWords(sentence)
        val perWord = if (unknown.all { it.length == 1 && writtenWithoutSpaces(it[0]) }) CHARACTERS_PER_NEW_WORD else 1
        return unknown.size <= newWords * perWord
    }

    /**
     * Whether [sentence] reuses at least one distinctive content word (or script character) from [card] or its
     * [alternatives], matching inflected, umlauted/accented, and participial forms while ignoring ubiquitous
     * function words ("der", "ist") when the card has more specific vocabulary.
     */
    fun usesCard(sentence: String, card: WorksheetCard, alternatives: List<String> = emptyList()): Boolean {
        val perCardText = buildList {
            add(normalizedWords(card.targetText))
            for (alt in alternatives) add(normalizedWords(alt))
        }
        val cardWords = perCardText.flatten()
        if (cardWords.isEmpty()) return false
        val sentenceWords = normalizedWords(sentence)
        if (sentenceWords.isEmpty()) return false
        val candidates = cardWords.distinct().filter { it.length >= MIN_STEM || it.any(::writtenWithoutSpaces) }.ifEmpty { cardWords.distinct() }
        fun outsideCount(term: String): Int {
            val inCard = perCardText.count { term in it }
            return ((wordCounts[term] ?: 0) - inCard).coerceAtLeast(0)
        }
        val minOutside = candidates.minOf(::outsideCount)
        val anchorTerms = candidates.filter { term ->
            term.any(::writtenWithoutSpaces) ||
                outsideCount(term) == minOutside ||
                (term.length >= 4 && outsideCount(term) <= minOutside + 1) ||
                (textCount >= 10 && outsideCount(term) * 5 <= textCount)
        }.ifEmpty { candidates }
        return anchorTerms.any { term ->
            if (term.any(::writtenWithoutSpaces)) {
                term.any { ch -> writtenWithoutSpaces(ch) && sentence.contains(ch) }
            } else {
                sentenceWords.any { word -> isSameWord(word, term) }
            }
        }
    }

    /**
     * Whether [sentence] contains words unknown to the coast that are missing from [help]. In active
     * translation into the coast's language, an unglossed new word forces the learner to guess a stranger.
     */
    fun hasUnexplainedUnknownWords(sentence: String, help: List<WordHelp>): Boolean {
        val unknown = unknownWords(sentence)
        if (unknown.isEmpty()) return false
        if (help.isEmpty()) return true
        val helpWords = help.flatMap { normalizedWords(it.word) }
        return unknown.any { word ->
            if (word.length == 1 && writtenWithoutSpaces(word[0])) {
                help.none { word in it.word }
            } else {
                helpWords.none { isSameWord(word, it) }
            }
        }
    }

    /**
     * The entries of [help] worth printing under [sentence]: the word is in the sentence, in some form, and
     * on none of the coast's cards. A model also lists small words the learner has ("nicht = not"), and now
     * and then a word that isn't in the sentence; neither helps. At most [WORKSHEET_WORD_HELP] are kept.
     */
    fun wordHelp(sentence: String, help: List<WordHelp>, maxWords: Int = WORKSHEET_WORD_HELP): List<WordHelp> {
        if (help.isEmpty()) return help
        val inSentence = normalizedWords(sentence)
        val written = sentence.lowercase()
        return help
            .filter { entry ->
                normalizedWords(entry.word).any { word ->
                    // A model lists a Japanese verb in its dictionary form ("遅れる"); the sentence has it with another ending ("遅れています").
                    if (word.any(::writtenWithoutSpaces)) {
                        val inSentence = word in written || word.length > 1 && word.last() in '\u3040'..'\u309F' && word.dropLast(1) in written
                        inSentence && word.any { writtenWithoutSpaces(it) && it !in characters }
                    }
                    else !isKnown(word) && inSentence.any { isSameWord(it, word) }
                }
            }
            .distinctBy { normalizedWords(it.word) }
            .take(maxWords)
    }

    private fun isKnown(word: String): Boolean {
        if (word in wordCounts || word.all { it.isDigit() }) return true
        val folded = foldWord(word)
        if (folded in wordCounts || folded in foldedWords) return true
        if (word.length >= MIN_STEM && (stemLength(word.length, FORM_SHARE)..word.length).any { word.substring(0, it) in stems }) return true
        if (folded.length >= MIN_STEM && (stemLength(folded.length, FORM_SHARE)..folded.length).any {
                val stem = folded.substring(0, it)
                stem in stems || stem in foldedStems
            }
        ) return true
        return participleInfinitives(word).any { inf ->
            val foldedInf = foldWord(inf)
            inf in wordCounts || foldedInf in wordCounts || foldedInf in foldedWords
        }
    }

    private companion object {
        /** Stems shorter than this say too little: "der" would vouch for "derzeit". */
        const val MIN_STEM = 3

        /** How much of a card's word must match for it to vouch for another form of itself. */
        const val STEM_SHARE = 0.6

        /** How much of a sentence's word must be such a stem: half lets an ending as long as the stem pass ("búð", "búðina"). */
        const val FORM_SHARE = 0.5

        /** How many unknown characters a new word may be in a script without spaces. */
        const val CHARACTERS_PER_NEW_WORD = 2

        /** Productive separable verb prefixes before "-ge-" in Germanic past participles ("eingekauft", "angekommen"). */
        val SEPARABLE_PREFIXES = arrayOf(
            "zurück", "zusammen", "weiter", "wieder",
            "statt", "teil", "fern", "fest", "nach",
            "auf", "aus", "bei", "ein", "her", "hin", "los", "mit", "vor", "weg",
            "ab", "an", "um", "zu"
        )

        fun stemLength(length: Int, share: Double): Int = maxOf(MIN_STEM, (length * share + 0.999).toInt()).coerceAtMost(length)

        /** Folds diacritics and vowel mutations ("ä" -> "a", "ø" -> "o") so umlauted/accented inflections match their base stem. */
        fun foldWord(word: String): String {
            if (word.all { it.code < 128 }) return word
            val stripped = removeAccents(word)
            if (stripped.all { it.code < 128 }) return stripped
            return buildString(stripped.length + 2) {
                for (ch in stripped) when (ch) {
                    'ø' -> append('o')
                    'æ' -> append("ae")
                    'œ' -> append("oe")
                    'ł' -> append('l')
                    'đ' -> append('d')
                    else -> append(ch)
                }
            }
        }

        /** Candidate verb infinitives for a Germanic past participle ("gekauft" -> "kaufen", "eingekauft" -> "einkaufen"). */
        fun participleInfinitives(word: String): List<String> {
            if (word.length < 6) return emptyList()
            val stems = ArrayList<String>(2)
            if (word.startsWith("ge")) stems += word.substring(2)
            for (prefix in SEPARABLE_PREFIXES) {
                if (word.length >= prefix.length + 6 && word.startsWith(prefix) && word.startsWith("ge", startIndex = prefix.length)) {
                    stems += prefix + word.substring(prefix.length + 2)
                    break
                }
            }
            if (stems.isEmpty()) return emptyList()
            val infinitives = ArrayList<String>(4)
            for (raw in stems) {
                val base = when {
                    raw.length >= 6 && (raw.endsWith("ten") || raw.endsWith("ter") || raw.endsWith("tes") || raw.endsWith("tem")) -> raw.dropLast(2)
                    raw.length >= 5 && raw.endsWith("te") -> raw.dropLast(1)
                    else -> raw
                }
                when {
                    base.endsWith("en") && base.length >= 5 -> infinitives += base
                    base.endsWith("et") && base.length >= 5 -> {
                        val root = base.dropLast(2)
                        infinitives += "${root}en"
                        infinitives += "${root}n"
                    }
                    (base.endsWith("t") || base.endsWith("d")) && base.length >= 4 -> {
                        val root = base.dropLast(1)
                        infinitives += "${root}en"
                        infinitives += "${root}n"
                    }
                }
            }
            return infinitives
        }

        /**
         * Whether [a] and [b] are one word in two forms ("liegen"/"liegt", "fahren"/"fährt", "kaufen"/"gekauft"):
         * they start alike (raw or accent-folded) for most of the shorter one, or one is the participle of the other.
         */
        fun isSameWord(a: String, b: String): Boolean {
            if (a == b) return true
            val shorter = minOf(a.length, b.length)
            // Korean writes a syllable as one character, so a noun is often two of them, with its particle attached: "학교", "학교에".
            if (shorter < MIN_STEM) return shorter == 2 && a[0] in '\uAC00'..'\uD7A3' && (a.startsWith(b) || b.startsWith(a))
            if (a.commonPrefixWith(b).length >= stemLength(shorter, STEM_SHARE)) return true
            val foldedA = foldWord(a)
            val foldedB = foldWord(b)
            val foldedShorter = minOf(foldedA.length, foldedB.length)
            if (foldedShorter >= MIN_STEM && foldedA.commonPrefixWith(foldedB).length >= stemLength(foldedShorter, STEM_SHARE)) return true
            return participleInfinitives(a).any { it == b || foldWord(it) == foldedB } ||
                participleInfinitives(b).any { it == a || foldWord(it) == foldedA }
        }

        /** Chinese, Japanese, Thai, Lao, Khmer and Burmese write sentences without spaces between words. */
        fun writtenWithoutSpaces(char: Char): Boolean =
            char in '\u0E00'..'\u0EFF' || char in '\u1000'..'\u109F' || char in '\u1780'..'\u17FF' ||
                char in '\u3040'..'\u30FF' || char in '\u3400'..'\u4DBF' || char in '\u4E00'..'\u9FFF'
    }
}

/**
 * The [count] sentences of [candidates] that make the best sheet, in the order the model wrote them. When
 * there are some to spare, the first to go are copies of a card ([known], the cards' translations). Then
 * come the sentences with something against them: far more words the coast doesn't have than a sentence of
 * [difficulty] may bring ([CoastVocabulary.keepsToCoast]), a length well outside what the difficulty
 * promises, a question on one side and not on the other, dropping or ignoring the card the line was meant
 * to practise, or leaving a new word unexplained when translating into the coast's language. Among the rest,
 * a near-repeat of a sentence already taken ("Where is the bus?" after "Where is the station?"), or a second
 * sentence for the same card while its island still has another clean card waiting, waits until the others
 * are in: a sheet of one pattern or one card over and over teaches little.
 *
 * None of this drops a sentence that the sheet needs to reach [count]; it only decides which ones stay out.
 *
 * @param difficulty What the sheet asked for; null to judge by the coast's words alone.
 */
fun pickWorksheetSentences(
    candidates: List<WorksheetSentence>,
    vocabulary: CoastVocabulary,
    known: List<String>,
    count: Int,
    difficulty: WorksheetDifficulty? = null,
    cardAlternatives: Map<WorksheetCard, List<String>> = emptyMap(),
    direction: WorksheetDirection? = null,
    expectsWordHelp: Boolean = false,
    recentSentences: List<String> = emptyList()
): List<WorksheetSentence> {
    if (candidates.size <= count) return candidates
    // Worked out once per sentence, not once per comparison: a long sheet from a large coast has hundreds.
    val words = candidates.map { normalizedWords(it.targetText) }
    // The cards are looked up among the sentences, not the other way round: there are a few hundred
    // sentences, and there can be tens of thousands of cards, which this way are never all held at once.
    val written = words.toHashSet()
    val copies = HashSet<List<String>>()
    for (text in known) normalizedWords(text).let { if (it in written) copies += it }
    val recentExact = HashSet<List<String>>()
    val recentSets = ArrayList<Set<String>>(recentSentences.size)
    for (text in recentSentences) {
        val w = normalizedWords(text)
        if (w.isNotEmpty()) {
            recentExact += w
            recentSets += w.toHashSet()
        }
    }
    val newWords = difficulty?.newWordsFor(direction)
        ?: (WORKSHEET_NEW_WORDS + if (direction == WorksheetDirection.ToNative) RECEPTIVE_EXTRA_NEW_WORDS else 0)
    val matchesAnchor = BooleanArray(candidates.size) { index ->
        val card = candidates[index].card
        card != null && vocabulary.usesCard(candidates[index].targetText, card, cardAlternatives[card].orEmpty())
    }
    val anyAnchored = matchesAnchor.any { it }
    val sets = HashMap<Int, Set<String>>()
    fun wordSet(index: Int) = sets.getOrPut(index) { words[index].toHashSet() }
    class Ranked(val index: Int, val isCopy: Boolean, val faults: Double)
    val ranked = candidates
        .mapIndexed { index, sentence ->
            val text = sentence.targetText
            val strays = if (vocabulary.keepsToCoast(text, newWords)) 0.0 else vocabulary.unknownShare(text)
            val anchorPenalty = if (anyAnchored && !matchesAnchor[index]) ANCHOR_FAULT else 0.0
            val helpPenalty = if (expectsWordHelp && direction == WorksheetDirection.ToTarget &&
                vocabulary.hasUnexplainedUnknownWords(text, sentence.newWords)
            ) UNEXPLAINED_FAULT else 0.0
            val recentPenalty = when {
                recentExact.isEmpty() -> 0.0
                words[index] in recentExact -> RECENT_REPEAT_FAULT
                recentSets.any { isNearRepeat(it, wordSet(index)) } -> RECENT_NEAR_REPEAT_FAULT
                else -> 0.0
            }
            Ranked(
                index = index,
                isCopy = words[index] in copies,
                faults = strays + lengthFault(text, words[index].size, difficulty) + questionFault(sentence) +
                    anchorPenalty + helpPenalty + recentPenalty
            )
        }
        .sortedWith(compareBy({ it.isCopy }, { it.faults }, { it.index }))
    // Best first. A near-repeat of a sentence already taken waits until the other clean ones are in; ranked has
    // the clean sentences first, so those waiting are the near-repeats, then the faulty ones, then the copies.
    val taken = ArrayList<Int>(count)
    val waiting = ArrayList<Int>()
    val takenCards = HashSet<WorksheetCard>()
    for ((position, candidate) in ranked.withIndex()) {
        if (taken.size >= count) break
        val card = candidates[candidate.index].card
        val repeatsCardOnIsland = card != null && card in takenCards &&
            ranked.subList(position + 1, ranked.size)
                .asSequence()
                .takeWhile { !it.isCopy && it.faults == 0.0 }
                .any { later -> candidates[later.index].card?.let { it.island == card.island && it !in takenCards } == true }
        val waits = candidate.isCopy || candidate.faults > 0.0 || repeatsCardOnIsland ||
            taken.any { isNearRepeat(wordSet(it), wordSet(candidate.index)) }
        if (waits) {
            waiting += candidate.index
        } else {
            taken += candidate.index
            if (card != null) takenCards += card
        }
    }
    for (index in waiting) if (taken.size >= count) break else taken += index
    return taken.sorted().map { candidates[it] }
}

/** How much a sentence counts against itself for each thing wrong with its shape; a stray word counts by its share of the sentence. */
private const val SHAPE_FAULT = 0.3

/** Penalty when a sentence repeats or closely echoes a sentence from a recent practice sheet on the same coast. */
private const val RECENT_REPEAT_FAULT = 0.25
private const val RECENT_NEAR_REPEAT_FAULT = 0.05

/** Penalty when a sentence dropped or ignored its assigned anchor card while other candidates honoured theirs. */
private const val ANCHOR_FAULT = 0.15

/** Penalty when a sentence into the coast's language introduces an unknown word without explaining it in word help. */
private const val UNEXPLAINED_FAULT = 0.1

/** A sentence may be this many words shorter or longer than its difficulty says before it counts as off. */
private const val LENGTH_SLACK = 2

/** Two sentences share this much of the shorter one's words: they differ by a word or two. */
private const val NEAR_REPEAT = 0.75

/** Sentences shorter than this have too few words for their overlap to mean anything. */
private const val NEAR_REPEAT_WORDS = 3

/** Whether [target]'s length is well outside what [difficulty] asks for. Scripts without spaces have no words to count. */
private fun lengthFault(target: String, wordCount: Int, difficulty: WorksheetDifficulty?): Double {
    if (difficulty == null || target.any { it in '\u3040'..'\u30FF' || it in '\u4E00'..'\u9FFF' || it in '\u0E00'..'\u0EFF' }) return 0.0
    val range = difficulty.words
    return if (wordCount < range.first - LENGTH_SLACK || wordCount > range.last + LENGTH_SLACK) SHAPE_FAULT else 0.0
}

/** Whether one side of [sentence] asks and the other doesn't, in scripts that mark a question with a question mark. */
private fun questionFault(sentence: WorksheetSentence): Double {
    fun asks(text: String) = text.trimEnd().trimEnd('"', '”', '»', ')', ' ').lastOrNull()?.let { it == '?' || it == '？' || it == '؟' } == true
    val target = sentence.targetText
    // Japanese, Chinese and Thai often end a question without one, and Greek uses ";": on either side.
    if ((target + sentence.nativeText).any { it in '\u3040'..'\u30FF' || it in '\u4E00'..'\u9FFF' || it in '\u0E00'..'\u0EFF' || it in '\u0370'..'\u03FF' }) return 0.0
    return if (asks(target) != asks(sentence.nativeText)) SHAPE_FAULT else 0.0
}

/** Whether two sentences, as their sets of words, say nearly the same: most of the shorter one's words are in the other. */
private fun isNearRepeat(a: Set<String>, b: Set<String>): Boolean {
    val shorter = minOf(a.size, b.size)
    if (shorter < NEAR_REPEAT_WORDS) return false
    val shared = if (a.size <= b.size) a.count { it in b } else b.count { it in a }
    return shared >= shorter * NEAR_REPEAT
}
