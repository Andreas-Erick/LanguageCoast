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

/*
 * The practice sheet: new sentences to translate, written by the AI provider from the cards of one coast
 * and laid out as a PDF (see [WorksheetPrinter]).
 *
 * Every sentence is built from one card the learner is forgetting ([WorksheetAnchor]): the prompt names the
 * card, and the model says something new with its key word or phrase, as a question, about someone else,
 * with a reason. That is the step from a memorised line to using it, and it gives the model a small, clear
 * job. What a sentence must be comes in the order it matters: correct and natural first, a faithful
 * translation second, the right length third, the learner's own words fourth. An earlier version put the words first ("only the
 * cards' words, and one more") and got sentences nobody would say, from the best models most of all, since
 * they keep to a rule. Measured on five coasts in four languages and graded blind, the new order took a top
 * model's sheets from one sentence in nine with something to mark to none in sixty.
 *
 * What comes back is read line by line, lines that can't be a sentence pair are dropped, and when the model
 * wrote some to spare, the ones left out are the copies of a card, the near-repeats and the ones furthest
 * from the coast's words (see [pickWorksheetSentences] and [CoastVocabulary]). Words a sentence needed that
 * are on no card are printed under it as word help.
 */

/**
 * How hard a practice sheet's sentences are.
 *
 * @property words How long a sentence is to be, in words; the prompt says so and the pick checks it.
 * @property newWords How many words that are on no card a sentence may bring before it counts as straying.
 */
enum class WorksheetDifficulty(
    val label: String,
    val hint: String,
    internal val words: IntRange,
    internal val newWords: Int,
    internal val wordHelpLimit: Int = WORKSHEET_WORD_HELP
) {
    Easy("Easy", "Short sentences that stay close to your cards.", 3..7, 1),
    Medium("Medium", "Your cards turned into new everyday sentences: questions, negations, other people.", 5..10, 2),
    // Measured before 3.1.0: a small model bolts a reason onto a question ("Where is the station, because my train leaves?").
    Hard(
        "Hard",
        "Longer sentences that add a reason, a condition or a contrast. Small models write these less naturally.",
        9..16,
        3,
        HARD_WORD_HELP
    );

    /**
     * Allowed unknown words per sentence for [direction]: reading comprehension ([WorksheetDirection.ToNative])
     * tolerates one additional contextual `i+1` word compared to active recall ([WorksheetDirection.ToTarget]).
     */
    internal fun newWordsFor(direction: WorksheetDirection? = null): Int =
        newWords + if (direction == WorksheetDirection.ToNative) RECEPTIVE_EXTRA_NEW_WORDS else 0
}

/** Extra unknown words allowed when reading in the coast's language rather than producing it from scratch. */
internal const val RECEPTIVE_EXTRA_NEW_WORDS = 1

/** Which way a practice sheet is translated. */
enum class WorksheetDirection {
    /** The sheet shows sentences in the user's language, to write in the coast's language. */
    ToTarget,

    /** The sheet shows sentences in the coast's language, to write in the user's language. */
    ToNative
}

/**
 * What the user picks for a practice sheet.
 *
 * @property islandIds The islands whose cards the sheet is made from, or null for every island of the coast.
 */
data class WorksheetOptions(
    val count: Int = WORKSHEET_DEFAULT_COUNT,
    val difficulty: WorksheetDifficulty = WorksheetDifficulty.Medium,
    val direction: WorksheetDirection = WorksheetDirection.ToTarget,
    val islandIds: Set<Int>? = null
) {
    /** Whether the sheet may use the island with [islandId]. */
    fun includes(islandId: Int): Boolean = islandIds == null || islandId in islandIds
}

/** Sentences on a practice sheet unless the user picks another number. */
const val WORKSHEET_DEFAULT_COUNT = 10

/** The fewest and the most sentences a practice sheet can have. */
val WORKSHEET_COUNT_RANGE = 3..500

/**
 * How a sheet is asked of a provider: how many sentences a request ([sentences]), about how long one request
 * takes ([seconds]), and whether a few sentences more are asked for than the sheet needs, so that the
 * weakest can be left out ([hasSpares]).
 *
 * A local model writes a handful at a time and is too slow for spares. The Claude models think before they
 * answer, over a dozen hard sentences for a minute or more, so they are asked for a dozen: twice that in one
 * request took a top model past the time and the room a request has, and the sheet failed. Other cloud
 * models answer a few dozen sentence pairs in seconds.
 */
enum class WorksheetBatch(val sentences: Int, val seconds: Int, val hasSpares: Boolean) {
    Local(5, 40, false),
    Thinking(12, 90, true),
    HardThinking(8, 75, true),
    Cloud(25, 10, true),
    HardCloud(12, 35, true);

    companion object {
        fun of(provider: TranslationProvider, difficulty: WorksheetDifficulty = WorksheetDifficulty.Medium): WorksheetBatch = when {
            provider == TranslationProvider.LocalAi -> Local
            provider == TranslationProvider.Claude && difficulty == WorksheetDifficulty.Hard -> HardThinking
            provider == TranslationProvider.Claude -> Thinking
            difficulty == WorksheetDifficulty.Hard -> HardCloud
            else -> Cloud
        }
    }
}

/** A request that was too much for the model is tried again with half as many sentences, down to this many. */
private const val SMALLEST_BATCH = 3

/**
 * How many sentences a sheet of [count] asks a model for in all. A cloud model is asked for a few to spare,
 * so the ones that stray furthest from the cards can be left out; a local model is too slow for that.
 */
internal fun worksheetSentencesWanted(count: Int, batch: WorksheetBatch): Int = if (batch.hasSpares) count + (count / 4).coerceIn(2, 10) else count

/**
 * How many of the [left] sentences the next request asks for: [batch] of them, or all that are left when
 * that is only a few more. A request for the last two or three would carry every card once more for them.
 */
internal fun worksheetAsk(left: Int, batch: Int): Int = if (left <= batch + batch / 4) left else batch

/** How many requests a sheet of [count] sentences takes if every answer comes back complete. */
fun worksheetRequests(count: Int, batch: WorksheetBatch): Int {
    val size = batch.sentences
    // The last few sentences go with the request before them (see worksheetAsk).
    return maxOf(1, (worksheetSentencesWanted(count, batch) - size / 4 + size - 1) / size)
}

/** A coast needs this many cards before a sheet can be made from it: fewer leave nothing to recombine. */
const val WORKSHEET_MIN_CARDS = 5

/**
 * Words an easy sheet's sentence may use that are on none of the coast's cards before it counts as straying
 * from them; a harder sheet's may use more ([WorksheetDifficulty.newWords]). The prompt sets no number: a
 * sentence takes the words it needs to be natural, and lists them.
 */
const val WORKSHEET_NEW_WORDS = 1

/** Word help is printed for at most this many words of a sentence; more would give the exercise away. */
const val WORKSHEET_WORD_HELP = 3

/** Two-clause hard sentences legitimately carry a connector plus two or three new words. */
internal const val HARD_WORD_HELP = 4

/** A word of a sheet's sentence that is on none of the learner's cards, and what it means. */
data class WordHelp(val word: String, val meaning: String)

/**
 * One sentence of a practice sheet, in the coast's language and in the user's.
 *
 * @property card The card it was built from, when the prompt named one; the answer key shows it.
 * @property newWords The words in it that the learner hasn't met, printed under the sentence.
 * @property hint How the user's-language side is meant where it could be translated two ways ("formal",
 *     "to a friend"); shown after the sentence when it is the one to translate.
 */
data class WorksheetSentence(
    val targetText: String,
    val nativeText: String,
    val card: WorksheetCard? = null,
    val newWords: List<WordHelp> = emptyList(),
    val hint: String? = null
) {
    /** The island it practises, when it was built from a card. */
    val island: String? get() = card?.island
}

/**
 * A practice sheet, ready to be drawn.
 *
 * @property coastName The coast it was made from, e.g. "German Coast".
 * @property providerLabel The AI provider that wrote the sentences, named on the sheet.
 * @property cardCount How many cards the coast had, and on how many islands ([islandCount]).
 */
data class Worksheet(
    val coastName: String,
    val targetLanguage: String,
    val nativeLanguage: String,
    val options: WorksheetOptions,
    val sentences: List<WorksheetSentence>,
    val date: LocalDate,
    val providerLabel: String,
    val cardCount: Int,
    val islandCount: Int
) {
    /** The language the sentences are printed in, and the one they are to be written in. */
    val fromLanguage: String get() = if (options.direction == WorksheetDirection.ToTarget) nativeLanguage else targetLanguage
    val toLanguage: String get() = if (options.direction == WorksheetDirection.ToTarget) targetLanguage else nativeLanguage

    /**
     * What sentence [sentence] shows on the sheet, and what the answer key gives for it. A sentence in the
     * user's language that could be translated two ways carries its hint: "Where are you from? (formal)".
     * The bracket stays with that side when it is the answer, too: it is part of what the model wrote there.
     */
    fun question(sentence: WorksheetSentence): String = when (options.direction) {
        WorksheetDirection.ToTarget -> withHint(sentence)
        WorksheetDirection.ToNative -> sentence.targetText
    }
    fun answer(sentence: WorksheetSentence): String = when (options.direction) {
        WorksheetDirection.ToTarget -> sentence.targetText
        WorksheetDirection.ToNative -> withHint(sentence)
    }

    private fun withHint(sentence: WorksheetSentence): String = sentence.nativeText + (sentence.hint?.let { " ($it)" } ?: "")

    /** The line of word help under [sentence], "besuchen = to visit · bar = in cash", or null when it needs none. */
    fun wordHelp(sentence: WorksheetSentence): String? =
        sentence.newWords.takeIf { it.isNotEmpty() }?.joinToString(" · ") { "${it.word} = ${it.meaning}" }

    /** The card [sentence] was built from, as the answer key shows it under the answer; null when it had none. */
    fun cardLine(sentence: WorksheetSentence): String? =
        sentence.card?.let { "Your card: ${shortened(it.targetText, CARD_SIDE)} = ${shortened(it.nativeText, CARD_SIDE)}" }

    /**
     * What the sheet practises, for its first page: "Built from your cards on Restaurant (4), Directions (3)
     * and Small Talk (3)." Null when the sentences came without cards, or the coast has a single island.
     */
    val focusLine: String?
        get() {
            val shares = sentences.mapNotNull { it.island }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            if (shares.isEmpty() || islandCount <= 1) return null
            val named = shares.take(FOCUS_ISLANDS).map { "${it.key} (${it.value})" }
            val more = shares.size - named.size
            val listed = when {
                more > 0 -> named.joinToString(", ") + " and ${if (more == 1) "1 more island" else "$more more islands"}"
                named.size == 1 -> named.single()
                else -> named.dropLast(1).joinToString(", ") + " and " + named.last()
            }
            return "Built from your cards on $listed."
        }

    /** A file name such as "german-coast-practice-2026-11-05.pdf". */
    val fileName: String
        get() {
            val coast = coastName.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").replace(Regex("-+"), "-").trim('-')
            return "${coast.ifEmpty { "coast" }}-practice-$date.pdf"
        }

    private companion object {
        /** Islands the first page names before it says "and 3 more islands". */
        const val FOCUS_ISLANDS = 4

        /** A card's side longer than this is cut short under an answer; only an island of nothing but long cards has such a card. */
        const val CARD_SIDE = 160
    }
}

/** A practice sheet drawn as a PDF file in the app's cache. */
data class WorksheetPdf(val path: String, val fileName: String, val pageCount: Int)

/** Draws practice sheets as PDF files, with each platform's own PDF library. */
interface WorksheetPrinter {
    /** Whether this platform can make PDFs; iPhone can't yet. */
    val isAvailable: Boolean

    /** Draws [worksheet] as a PDF in the app's cache; null if the file couldn't be made. */
    suspend fun print(worksheet: Worksheet): WorksheetPdf?
}

/** The [WorksheetPrinter] of a platform that can't make PDFs yet. */
class NoWorksheetPrinter : WorksheetPrinter {
    override val isAvailable = false
    override suspend fun print(worksheet: Worksheet): WorksheetPdf? = null
}

// ---------- Asking the AI for sentences ----------

/** A card as the prompt shows it: its two sides and the island it is on. */
data class WorksheetCard(val island: String, val targetText: String, val nativeText: String) {
    /** Stable per-coast key for cross-sheet anchor freshness tracking. */
    internal fun historyKey(): String = "$island\t$targetText"
}

/** How many of a prompt's sentences are to be about one island. */
data class IslandQuota(val island: String, val sentences: Int)

/**
 * A numbered line of a prompt: the card one sentence is to be built from, plus an optional register/context
 * [note] stored on the flashcard.
 */
data class WorksheetAnchor(val card: WorksheetCard, val note: String? = null)

/**
 * What one prompt of a sheet is made from: the cards it shows, the cards its sentences are built from
 * ([anchors], one per sentence), and how many of them are about which island.
 */
internal class WorksheetDeal(val cards: List<WorksheetCard>, val quotas: List<IslandQuota>, val anchors: List<WorksheetAnchor> = emptyList())

/** [text], or its first [limit] characters and "…" when it is longer. */
private fun shortened(text: String, limit: Int): String {
    if (text.length <= limit) return text
    val cut = text.take(limit)
    // Not through the middle of an emoji, which is two characters to Kotlin.
    return (if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut).trimEnd() + "…"
}

/** A numbered line of the prompt shows this much of a card; the whole card is in the list above it. */
private const val ANCHOR_LINE = 160

/** Maximum length for an anchor's usage/register note or a learner's goal in the prompt. */
private const val ANCHOR_NOTE_LINE = 80
private const val GOAL_LINE = 120

/** How long and how built a sentence of [difficulty] is, as the prompt says it after "It is". */
private fun lengthRule(difficulty: WorksheetDifficulty): String {
    val words = "${difficulty.words.first} to ${difficulty.words.last} words"
    return when (difficulty) {
        WorksheetDifficulty.Easy -> "short and simple: $words in one clause"
        WorksheetDifficulty.Medium -> "an everyday sentence of $words"
        WorksheetDifficulty.Hard ->
            "a longer sentence of $words in two parts that belong together (a reason, a condition, a contrast or a second step), never two unrelated things joined"
    }
}

/**
 * What a sheet of [difficulty] asks the model to do with a card, besides saying something new with it. An
 * easy sheet asks for nothing more. The model picks the change that suits each card: one it is handed can
 * be the one that card takes badly ("lovely" doesn't negate well), and a small model then writes the odd
 * sentence anyway.
 */
private fun changeRule(difficulty: WorksheetDifficulty): String = when (difficulty) {
    WorksheetDifficulty.Easy -> ""
    WorksheetDifficulty.Medium ->
        " Do something different with the flashcard each time: ask a question with it, say what is not the case, say it about someone else " +
            "(he, she, we or they), or add a time or a place. For each line pick the change that gives the most natural sentence, and vary the changes over the exercise."
    WorksheetDifficulty.Hard ->
        " Give each sentence a second part that belongs to the first: a reason (because), a condition (if, when), a contrast (but, although) or " +
            "what happens next. For each line pick the one that fits the flashcard best, and vary them over the exercise. " +
            "Never bolt \"because\" onto a direct question, and when a flashcard is a short greeting, thanks or polite phrase, " +
            "weave it into a natural situation (such as why the speaker is leaving, apologising or asking politely) rather than forcing an awkward clause onto it."
}

/**
 * The prompt that asks a model for new sentences in [targetLanguage] built from [cards], each with its
 * translation into [nativeLanguage]: one sentence for each of [anchors], the cards the sheet practises, or
 * [count] sentences spread over the islands when there are none.
 *
 * What a sentence must be comes most important first: correct and natural, a faithful translation, the
 * right length, and only then the learner's own words, so a model never bends a sentence to fit the cards.
 *
 * @param cards The coast's cards (or some of its islands, when there are too many), island by island.
 * @param region Where the language will be used, so the sentences use that place's words; null for no preference.
 * @param avoid Sentences in [targetLanguage] already written for this sheet, when asking for more.
 * @param quotas Without [anchors]: how many of the sentences are to be about which island, adding up to [count].
 * @param level The level the learner has reached on the coast's route, or null when the route says nothing about them.
 * @param wording How the learner speaks of themselves, so "I am tired" comes out in the right gender.
 * @param direction Which side the learner is given: for [WorksheetDirection.ToTarget] a sentence that could be
 *     translated two ways gets a hint.
 * @param wordHelp Whether to ask for the words that are on no card, to print under the sentence. A small local
 *     model does better without a fourth field.
 */
fun buildWorksheetPrompt(
    cards: List<WorksheetCard>,
    targetLanguage: String,
    nativeLanguage: String,
    count: Int,
    difficulty: WorksheetDifficulty,
    region: String? = null,
    avoid: List<String> = emptyList(),
    quotas: List<IslandQuota> = emptyList(),
    anchors: List<WorksheetAnchor> = emptyList(),
    level: Level? = null,
    wording: Wording = Wording.Unspecified,
    direction: WorksheetDirection = WorksheetDirection.ToTarget,
    wordHelp: Boolean = true,
    goal: String? = null
): String {
    val byIsland = cards.groupBy { it.island }
    val flashcards = byIsland.entries.joinToString("\n\n") { (island, group) ->
        "[$island]\n" + group.joinToString("\n") { "${it.targetText} = ${it.nativeText}" }
    }
    val where = region?.trim()?.takeIf { it.isNotEmpty() }?.let { " from $it" } ?: ""
    val levelClause = level?.let { " and their level is about ${it.label}" } ?: ""
    val goalClause = goal?.let(WorksheetCardDealer::oneLine)?.takeIf { it.isNotEmpty() }
        ?.let { " Why they are learning $targetLanguage: \"${shortened(it, GOAL_LINE)}\"." } ?: ""
    // The flashcards and the numbered lines are joined on afterwards: trimIndent would trip over their unindented lines.
    val intro = """
        You are a native $targetLanguage speaker$where who teaches $targetLanguage to adults. You are writing a translation exercise for one learner. Their native language is $nativeLanguage$levelClause.$goalClause

        These are the learner's flashcards, grouped by topic, one per line, as "$targetLanguage = $nativeLanguage":
    """.trimIndent()

    val task: String
    val lines: String
    if (anchors.isNotEmpty()) {
        task = "Write one new $targetLanguage sentence for each numbered line below, with its $nativeLanguage translation. " +
            "A line names a topic and one of the flashcards. " +
            "The sentence must use that flashcard's key word or phrase, in whatever form the sentence needs, and say something new with it: " +
            "something that really is said in that topic's situation." +
            changeRule(difficulty)
        // In words and without the brackets of the topics' headings: a small model copies brackets into its answer.
        lines = "\n\n" + anchors.withIndex().joinToString("\n") { (index, anchor) ->
            val noteSuffix = anchor.note?.let(WorksheetCardDealer::oneLine)?.takeIf { it.isNotEmpty() }
                ?.replace('[', '(')?.replace(']', ')')?.replace("||", ";")
                ?.let { " (Note: ${shortened(it, ANCHOR_NOTE_LINE)})" } ?: ""
            "${index + 1}. ${anchor.card.island}: ${shortened(anchor.card.targetText, ANCHOR_LINE)}$noteSuffix"
        }
    } else {
        val spread = when {
            quotas.isEmpty() -> "Spread the sentences over the topics."
            else -> {
                val shares = quotas.map { "${it.sentences} about ${it.island}" }
                val listed = if (shares.size == 1) shares.single() else shares.dropLast(1).joinToString(", ") + " and " + shares.last()
                "Of the sentences, write $listed." +
                    if (byIsland.keys.any { island -> quotas.none { it.island == island } }) " The other topics get no sentence of their own this time." else ""
            }
        }
        val sentences = if (count == 1) "1 new $targetLanguage sentence" else "$count new $targetLanguage sentences"
        task = "Write $sentences for the learner to translate, each with its $nativeLanguage translation. " +
            "Each sentence takes a word or phrase from one topic's flashcards and says something new with it: something that really is said in that topic's situation. " +
            spread + changeRule(difficulty)
                .replace("For each line", "For each sentence")
                .replace("with the flashcard each time", "with the word or phrase each time")
                .replace("fits the flashcard best", "fits the word or phrase best")
        lines = ""
    }

    // The difficulty sets the length and the level the words and grammar. Said as one ("it suits level A1: longer
    // sentences of 9 to 16 words"), a hard sheet for a beginner would contradict itself.
    val suits = if (level != null) "Its words and grammar suit level ${level.label}." else "Its words and grammar suit the learner, whose level the flashcards show."
    val notes = buildList {
        speakerRule(wording, targetLanguage).takeIf { it.isNotEmpty() }?.let { add(it) }
        if (direction == WorksheetDirection.ToTarget) {
            add(
                "- Where the $nativeLanguage line could fairly be translated in more than one way (formal or informal \"you\", one person or several), " +
                    "end it with a short hint in round brackets, " +
                    // The examples are English; a learner who reads another language gets the hint in theirs.
                    if (nativeLanguage == "English") "such as (formal) or (to a friend)."
                    else "written in $nativeLanguage: what (formal) or (to a friend) would be in $nativeLanguage."
            )
        }
        if (difficulty == WorksheetDifficulty.Hard) {
            add(
                "- Build the second part from a realistic everyday situation and, only where they genuinely fit together, words from the other flashcards above; " +
                    "never stitch two unrelated flashcards into a contrived scene just to reuse vocabulary. " +
                    "Introduce at most two or three new $targetLanguage content words per sentence so the learner is not overwhelmed" +
                    (if (wordHelp) ", and list every new $targetLanguage word in the last field." else ".")
            )
        }
        if (avoid.isNotEmpty()) add("- Do not repeat any of these, which are already in the exercise: " + avoid.joinToString(" | "))
    }
    val newWords = if (wordHelp) "; list them after the translation" else ""
    val rules = """
        What matters, most important first:
        1. The $targetLanguage sentence is correct and natural: what a native speaker$where would really say, with every ending, article, case, preposition and the word order right. Picture who says it, to whom and when. If a sentence only exists to reuse a word, write a different one.
        2. The $nativeLanguage line says the same thing the way a native speaker of $nativeLanguage would say it: the same meaning, person, tense and politeness, nothing added and nothing dropped, and not word for word where that sounds odd.
        3. It is ${lengthRule(difficulty)}. $suits
        4. It leans on what the learner knows. Prefer words and patterns from the flashcards. Other words are welcome where the sentence needs them, as long as they are common, everyday words$newWords.
        5. No two sentences are the same or differ by a single word, and none repeats a flashcard.
    """.trimIndent() + notes.joinToString("") { "\n$it" }

    val number = if (anchors.isNotEmpty()) "[number] || " else ""
    val words =
        if (wordHelp) {
            " || [the $targetLanguage words that are not on the flashcards, each with its $nativeLanguage meaning, " +
                "as \"word = meaning; word = meaning\", or - if there are none]"
        } else ""
    val format = "Answer with exactly one line per ${if (anchors.isNotEmpty()) "number" else "sentence"} and nothing else, in this format:\n" +
        "$number[$targetLanguage sentence] || [$nativeLanguage translation]$words"

    return "$intro\n\n$flashcards\n\n$task$lines\n\n$rules\n\n$format"
}

/**
 * Parses a model's reply to [buildWorksheetPrompt]: one sentence per line, as `number || sentence ||
 * translation || new words`. The number says which of [anchors] the sentence was built from. A model that
 * drops the number, the new words or both is still read (`sentence || translation`), as are numbering with
 * a dot, bullets, markdown and the prompt's brackets. Lines that can't be a sentence pair are dropped: one
 * side missing, both sides the same, or the format line itself; so are repeats.
 */
fun parseWorksheetResponse(text: String, anchors: List<WorksheetAnchor> = emptyList()): List<WorksheetSentence> =
    coalesceWrappedWorksheetLines(text.replace("```", "").replace("**", "").lines())
        .filter { it.contains("||") }
        .mapNotNull { line -> parseWorksheetLine(line, anchors) }
        .distinctBy { normalizedWords(it.targetText) }

/**
 * Rejoins long hard-sheet lines that a model wrapped across a line break: a continuation line starting
 * with `||`, or a standalone line number (`12.` / `[12]`) followed on the next line by its sentence pair.
 */
private fun coalesceWrappedWorksheetLines(rawLines: List<String>): List<String> {
    val result = ArrayList<String>(rawLines.size)
    var pendingNumber: String? = null
    for (raw in rawLines) {
        val line = raw.trim()
        if (line.isEmpty()) {
            pendingNumber = null
            continue
        }
        if (line.startsWith("||") && result.isNotEmpty()) {
            result[result.lastIndex] = "${result.last()} $line"
            pendingNumber = null
            continue
        }
        if ("||" !in line) {
            val cleaned = unwrapped(line.replace(BULLET, "").trim())
            val num = NUMBER_ALONE.matchEntire(cleaned)?.groupValues?.get(1)
            if (num != null) {
                pendingNumber = num
                continue
            }
            result += if (pendingNumber != null) "$pendingNumber || $line" else line
            pendingNumber = null
            continue
        }
        val firstField = unwrapped(line.substringBefore("||").replace(BULLET, "").trim())
        val hasOwnNumber = NUMBER_ALONE.matches(firstField) || NUMBER_LEADING.matches(firstField)
        result += if (pendingNumber != null && !hasOwnNumber) "$pendingNumber || $line" else line
        pendingNumber = null
    }
    return result
}

private fun parseWorksheetLine(line: String, anchors: List<WorksheetAnchor>): WorksheetSentence? {
    var parts = line.split("||").map { unwrapped(it.trim()) }
    var number: Int? = null
    // A model that answers as a list puts a bullet before the number: "- 3 || sentence || translation".
    val first = unwrapped(parts[0].replace(BULLET, "").trim())
    val alone = NUMBER_ALONE.matchEntire(first)
    val leading = NUMBER_LEADING.matchEntire(first)
    when {
        // "3 || sentence || translation"
        alone != null -> {
            number = alone.groupValues[1].toIntOrNull()
            parts = parts.drop(1)
        }
        // "3. sentence || translation"
        leading != null -> {
            number = leading.groupValues[1].toIntOrNull()
            parts = listOf(unwrapped(leading.groupValues[2].trim())) + parts.drop(1)
        }
        else -> parts = listOf(first) + parts.drop(1)
    }
    val target = parts.getOrNull(0).orEmpty()
    var native = parts.getOrNull(1).orEmpty()
    if (target.isEmpty() || native.isEmpty()) return null
    // A hint for the learner at the end of their own language's side: "Where are you from? (formal)".
    val hinted = HINT.matchEntire(native)
    val hint = hinted?.groupValues?.get(2)?.trim()
    // A small model sets the hint off with a slash and quotes the sentence before it: "Where are you from?" / (formal).
    if (hinted != null) native = unwrapped(hinted.groupValues[1].trim().trimEnd('/').trim())
    if (native.isEmpty() || isBroken(target, native)) return null
    val card = number?.let { anchors.getOrNull(it - 1) }?.card
    return WorksheetSentence(target, native, card = card, newWords = parseWordHelp(parts.getOrNull(2).orEmpty()), hint = hint)
}

/**
 * Whether a parsed line can't be a sentence with its translation: the model sent the format line back, wrote
 * the same text on both sides (it didn't translate), or left the prompt's own marks in ("=" between two
 * sides belongs to the flashcards, not to a sentence).
 */
private fun isBroken(target: String, native: String): Boolean {
    val words = normalizedWords(target)
    if (words.size >= SAME_SIDES_WORDS && words == normalizedWords(native)) return true
    if (FORMAT_ECHO.containsMatchIn(target) || FORMAT_ECHO.containsMatchIn(native)) return true
    // The format line itself, its brackets gone: "number || German sentence || English translation".
    if (target.equals("number", ignoreCase = true)) return true
    if (target.endsWith(" sentence", ignoreCase = true) && native.endsWith(" translation", ignoreCase = true)) return true
    return " = " in target || " = " in native
}

/** "besuchen = to visit; bar = in cash" as word help; "-", "none" or anything without "=" is none. */
private fun parseWordHelp(text: String): List<WordHelp> =
    text.split(';').mapNotNull { entry ->
        val word = entry.substringBefore('=', "").trim().trim('"', '“', '”', '„')
        val meaning = entry.substringAfter('=', "").trim().trim('"', '“', '”', '„')
        if (word.isEmpty() || meaning.isEmpty() || word.length > WORD_HELP_LENGTH || meaning.length > WORD_HELP_LENGTH) null else WordHelp(word, meaning)
    }

/** "12", "[12]", "(12)", "#12", "12." or "12:" alone in the first field. */
private val NUMBER_ALONE = Regex("^[\\[(#]?\\s*(\\d{1,3})\\s*[\\])]?[.):]?$")

/** "12. Sentence" or "12) Sentence" in the first field. */
private val NUMBER_LEADING = Regex("^\\[?(\\d{1,3})\\]?[.)]\\s+(.+)$")

/** "-" or "*" at the start of a line. */
private val BULLET = Regex("^[-*•]\\s*")

/** A sentence followed by a short note in round brackets, or in the wide ones Chinese and Japanese are written with. */
private val HINT = Regex("^(.*\\S)\\s*[(（]([^()（）]{2,40})[)）]$")

/** The prompt's own placeholders, sent back as if they were a sentence. */
private val FORMAT_ECHO = Regex("^(?:sentence in|translation in|the (?:\\S+ )?words that are not)\\b", RegexOption.IGNORE_CASE)

/** From this many words on, two sides that read the same are a line the model didn't translate. */
private const val SAME_SIDES_WORDS = 3

/** A word or a meaning longer than this isn't one: the model wrote a sentence into the field. */
private const val WORD_HELP_LENGTH = 40

/** `[Hello]` or `"Hello"` -> `Hello`, in case the model copied the prompt's brackets or quoted the sentence. */
private fun unwrapped(value: String): String = when {
    value.length >= 2 && value.startsWith("[") && value.endsWith("]") && !value.substring(1, value.length - 1).contains("[") ->
        value.substring(1, value.length - 1).trim()
    value.length > 2 && value.first() in "\"“„«" && value.last() in "\"”“»" -> value.substring(1, value.length - 1).trim()
    else -> value
}

// ---------- Keeping to the coast's words ----------

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

/**
 * Deals out a coast's cards to the prompts of one sheet, [limit] cards and [characters] of card text per
 * prompt at most, in the coast's order.
 *
 * Every prompt is told which islands its sentences are to be about and how many each ([WorksheetDeal.quotas]):
 * the islands whose cards need practice most get the most, by the rules in `WorksheetFocus.kt`. And it is
 * told which card each sentence is built from ([WorksheetDeal.anchors]): of an island's cards, the studied
 * ones that need practice most come first, and no card is used twice on a sheet until its island has run
 * out of others. The islands take turns down the list, so the sheet is mixed.
 *
 * A coast that fits is shown whole every time. A larger one, be it tens of thousands of cards, is shown
 * island by island: a sentence is built from one island's cards, so a prompt gets up to [perIsland] cards of
 * an island (enough to build from) rather than a few of each. The islands the prompt's sentences are about
 * come first, and of an island the cards the learner has studied, those most that need practice ([now]
 * being when that is asked). Room that is left goes to other islands, for their everyday words, the ones
 * that haven't been shown for longest first, and then to more cards of the islands that are there.
 */
internal class WorksheetCardDealer(
    cards: List<WorksheetSourceCard>,
    private val limit: Int,
    private val perIsland: Int,
    private val characters: Int,
    now: Long,
    private val random: Random,
    private val recentAnchors: Set<String> = emptySet(),
    private val difficulty: WorksheetDifficulty = WorksheetDifficulty.Medium
) {
    internal class Snapshot(
        val prompts: Int,
        val lastShown: IntArray,
        val anchored: Set<Int>,
        val planner: DoubleArray
    )

    fun snapshot(): Snapshot = Snapshot(prompts, lastShown.copyOf(), HashSet(anchored), planner.snapshot())

    fun restore(saved: Snapshot) {
        prompts = saved.prompts
        saved.lastShown.copyInto(lastShown)
        anchored.clear()
        anchored.addAll(saved.anchored)
        planner.restore(saved.planner)
    }
    private val lines = cards.map { WorksheetCard(it.islandName, oneLine(it.targetText), oneLine(it.nativeText)) }
    private val notes = cards.map { it.note }
    private val sizes = IntArray(lines.size) { lines[it].targetText.length + lines[it].nativeText.length + SEPARATOR_LENGTH }
    private val shortest = sizes.minOrNull() ?: 0
    private val fitsWhole = lines.size <= limit && sizes.sumOf { it.toLong() } <= characters

    /** How much each card needs practice ([practiceNeed]), or [NOT_STUDIED] for one the learner has never studied. */
    private val needs = DoubleArray(cards.size) { if (isStudied(cards[it])) practiceNeed(cards[it], now) else NOT_STUDIED }

    /** The islands' cards (as indices), and the islands' names. */
    private val islands: List<List<Int>> = cards.indices.groupBy { cards[it].islandId }.values.toList()
    private val names = islands.map { cards[it.first()].islandName }
    private val planner = FocusPlanner(
        DoubleArray(islands.size) { island -> islandFocus(islands[island].size, islands[island].sumOf { needs[it].coerceAtLeast(0.0) }) },
        random
    )

    /** Where the cards of the island being dealt stand in a prompt's draw; see [inPickOrder]. */
    private val draw = DoubleArray(cards.size)

    /** The number of the prompt an island's cards were last in, 0 for none yet. */
    private val lastShown = IntArray(islands.size)
    private var prompts = 0

    /** The cards a sentence of this sheet has been built from already. */
    private val anchored = HashSet<Int>()

    /** How many islands the cards are on. */
    val islandCount: Int get() = islands.size

    /** The cards for the next prompt, which asks for [sentences] sentences, and the islands those are to be about. */
    fun next(sentences: Int): WorksheetDeal {
        prompts++
        val quotas = planner.plan(sentences, maxIslands = if (fitsWhole) islands.size else maxOf(1, limit / perIsland))
        val planned = islands.indices.filter { quotas[it] > 0 }
        // The prompt names the islands in the order it shows them, which is the coast's. Islands of one name are one topic to it.
        val named = LinkedHashMap<String, Int>()
        if (islands.size > 1) planned.sortedBy { islands[it].first() }.forEach { named[names[it]] = (named[names[it]] ?: 0) + quotas[it] }
        val islandQuotas = named.map { (island, count) -> IslandQuota(island, count) }
        // An island's cards in this prompt's order, drawn once: its anchors are the first of the cards it shows.
        val orders = HashMap<Int, List<Int>>()
        fun order(island: Int) = orders.getOrPut(island) { inPickOrder(islands[island]) }
        val inCoastOrder = planned.sortedBy { islands[it].first() }
        if (fitsWhole) return WorksheetDeal(lines, islandQuotas, anchorsFor(inCoastOrder, quotas, ::order, shown = null, sentences))

        val picked = ArrayList<Int>(limit)
        val passedOver = ArrayList<Int>()
        var room = characters
        fun full() = picked.size >= limit || room < shortest
        fun take(index: Int): Boolean {
            if (picked.size >= limit || sizes[index] > room) return false
            picked += index
            room -= sizes[index]
            return true
        }

        val others = islands.indices.filter { quotas[it] == 0 }.sortedWith(compareBy({ lastShown[it] }, { planner.due(it) }))
        for (island in planned.sortedByDescending { quotas[it] } + others) {
            if (full()) break
            var taken = 0
            for (index in order(island)) {
                if (taken < perIsland && take(index)) taken++ else passedOver += index
            }
            if (taken > 0) lastShown[island] = prompts
        }
        if (!full()) for (index in passedOver.shuffled(random)) if (full()) break else take(index)
        // Cards too long for any prompt: the shortest ones are still better than a prompt without cards.
        if (picked.isEmpty()) picked += sizes.indices.sortedBy { sizes[it] }.take(WORKSHEET_MIN_CARDS)
        val shown = picked.toHashSet()
        return WorksheetDeal(picked.sorted().map { lines[it] }, islandQuotas, anchorsFor(inCoastOrder, quotas, ::order, shown, sentences))
    }

    /**
     * The cards a prompt's [sentences] sentences are built from: each of the [planned] islands' share
     * ([quotas]), mixed. An island whose cards found no room in the prompt has none to give, so its
     * sentences go to the islands that are shown, one at a time in turn.
     */
    private fun anchorsFor(planned: List<Int>, quotas: IntArray, order: (Int) -> List<Int>, shown: Set<Int>?, sentences: Int): List<WorksheetAnchor> {
        val dealt = planned.map { anchorsOf(order(it), quotas[it], shown).toMutableList() }
        val withCards = dealt.indices.filter { dealt[it].isNotEmpty() }
        var turn = 0
        while (withCards.isNotEmpty() && dealt.sumOf { it.size } < sentences) {
            val at = withCards[turn++ % withCards.size]
            dealt[at] += anchorsOf(order(planned[at]), 1, shown, already = dealt[at])
        }
        return anchors(dealt)
    }

    /**
     * The [wanted] cards of one island that its sentences are built from, out of the island's cards in
     * [order] (those that need practice most first). Cards the prompt doesn't show ([shown], null for all of
     * them) and very long ones are passed over, and cards used on this sheet before come last: an island
     * with fewer cards than sentences has them come round again.
     *
     * @param already The island's cards this prompt has been given before this call.
     */
    private fun anchorsOf(order: List<Int>, wanted: Int, shown: Set<Int>?, already: List<Int> = emptyList()): List<Int> {
        val there = order.filter { shown == null || it in shown }
        val usable = there.filter { sizes[it] <= ANCHOR_CHARACTERS }.ifEmpty { there }
        if (usable.isEmpty()) return emptyList()
        val picked = ArrayList<Int>(wanted)
        val pickedSet = HashSet<Int>(wanted)
        fun takeMatching(predicate: (Int) -> Boolean) {
            for (index in usable) {
                if (picked.size >= wanted) return
                if (index !in anchored && index !in pickedSet && predicate(index)) {
                    picked += index
                    pickedSet += index
                }
            }
        }
        fun isRichForHard(index: Int): Boolean {
            if (difficulty != WorksheetDifficulty.Hard) return true
            val target = lines[index].targetText
            if (target.any { it in '\u0E00'..'\u0EFF' || it in '\u1000'..'\u109F' || it in '\u1780'..'\u17FF' || it in '\u3040'..'\u30FF' || it in '\u3400'..'\u4DBF' || it in '\u4E00'..'\u9FFF' }) {
                return target.length >= 2
            }
            val words = normalizedWords(target)
            return words.size >= 2 || (words.singleOrNull()?.length ?: 0) >= 5
        }
        // First the cards no sentence of this sheet was built from yet (preferring studied cards that weren't
        // anchored on a recent sheet, then studied cards that were, before unstudied cards; on Hard, bare short
        // interjections like "Hi" or "Ja" wait behind multi-word or content cards), then the ones this prompt
        // doesn't have yet; only an island with fewer cards than the prompt wants has a card twice.
        if (recentAnchors.isEmpty()) {
            if (difficulty == WorksheetDifficulty.Hard) takeMatching(::isRichForHard)
            takeMatching { true }
        } else {
            if (difficulty == WorksheetDifficulty.Hard) takeMatching { needs[it] != NOT_STUDIED && lines[it].historyKey() !in recentAnchors && isRichForHard(it) }
            takeMatching { needs[it] != NOT_STUDIED && lines[it].historyKey() !in recentAnchors }
            if (difficulty == WorksheetDifficulty.Hard) takeMatching { needs[it] != NOT_STUDIED && isRichForHard(it) }
            takeMatching { needs[it] != NOT_STUDIED }
            if (difficulty == WorksheetDifficulty.Hard) takeMatching { lines[it].historyKey() !in recentAnchors && isRichForHard(it) }
            takeMatching { lines[it].historyKey() !in recentAnchors }
            takeMatching { true }
        }
        val inPrompt = (already + picked).toHashSet()
        for (index in usable) if (picked.size >= wanted) break else if (inPrompt.add(index)) picked += index
        while (picked.size < wanted) picked += usable[(already.size + picked.size) % usable.size]
        anchored += picked
        return picked
    }

    /** The islands' anchors as the prompt lists them: the islands take turns, so the sheet is mixed. */
    private fun anchors(byIsland: List<List<Int>>): List<WorksheetAnchor> {
        val mixed = ArrayList<WorksheetAnchor>()
        var turn = 0
        while (true) {
            val before = mixed.size
            for (island in byIsland) island.getOrNull(turn)?.let { mixed += WorksheetAnchor(lines[it], notes[it]) }
            if (mixed.size == before) return mixed
            turn++
        }
    }

    /**
     * An island's [cards] in the order a prompt takes them: the studied ones first, drawn by their need (the
     * more, the earlier, most of the time), then the ones never studied, shuffled. A different order every
     * time, so the prompts of a sheet don't all show the same cards of a large island.
     */
    private fun inPickOrder(cards: List<Int>): List<Int> {
        // A weighted draw without putting back is a sort by -ln(u)/weight (Efraimidis & Spirakis, 2006).
        for (index in cards) {
            val need = needs[index]
            draw[index] = if (need == NOT_STUDIED) NEVER_STUDIED + random.nextDouble() else -ln(1 - random.nextDouble()) / (STUDIED_PICK + need)
        }
        return cards.sortedBy { draw[it] }
    }

    internal companion object {
        /** " = " and the line break around a card's two sides. */
        const val SEPARATOR_LENGTH = 4

        /** A card longer than this (both sides) is a paragraph, not something to build one sentence from. */
        const val ANCHOR_CHARACTERS = 160

        /**
         * A studied card's weight in a prompt's draw before its need is added: a card that needs all the
         * practice there is (1) is five times as likely to be drawn next as one that needs none.
         */
        const val STUDIED_PICK = 0.25

        /** Sorts the cards never studied behind every studied one, whose keys are far below it. */
        const val NEVER_STUDIED = 1e9

        /** Stands for a card's need where the card was never studied. */
        const val NOT_STUDIED = -1.0

        /**
         * A card's text on one line, as the prompt lists one card per line. Nearly every text is on one line
         * already and is used as it is, so a large coast's texts aren't all held twice.
         */
        fun oneLine(text: String): String {
            val oneLine = text.none { it == '\n' || it == '\r' } && text.firstOrNull()?.isWhitespace() != true && text.lastOrNull()?.isWhitespace() != true
            return if (oneLine) text else text.lines().joinToString(" ") { it.trim() }.trim()
        }
    }
}

/**
 * Estimates the learner's CEFR level ([Level.A1], [Level.A2], [Level.B1]) on custom or imported coasts
 * where built-in route islands account for fewer than half of the cards. Returns `null` until enough cards
 * have been studied and retained ([MIN_STUDIED_FOR_LEVEL], [MIN_MATURE_FOR_LEVEL]) so a freshly imported
 * deck never claims a level its owner hasn't earned yet.
 */
internal fun estimateWorksheetLevel(cards: List<WorksheetSourceCard>): Level? {
    val studied = cards.filter(::isStudied)
    if (studied.size < MIN_STUDIED_FOR_LEVEL) return null
    val mature = studied.filter { val s = it.stability; s != null && s.isFinite() && s >= MATURE_STABILITY_DAYS }
    if (mature.size < MIN_MATURE_FOR_LEVEL) return null
    val avgStability = mature.sumOf { it.stability ?: 0.0 } / mature.size
    val avgWords = mature.sumOf { card ->
        val unspaced = card.targetText.count {
            it in '\u0E00'..'\u0EFF' || it in '\u1000'..'\u109F' || it in '\u1780'..'\u17FF' ||
                it in '\u3040'..'\u30FF' || it in '\u3400'..'\u4DBF' || it in '\u4E00'..'\u9FFF'
        }
        if (unspaced > 0) unspaced / 2.5 else normalizedWords(card.targetText).size.toDouble()
    } / mature.size
    return when {
        (mature.size >= B1_MATURE_CARDS && avgStability >= B1_AVG_STABILITY_DAYS && avgWords >= B1_AVG_WORDS) ||
            (mature.size >= B1_BROAD_MATURE_CARDS && avgStability >= A2_AVG_STABILITY_DAYS) -> Level.B1
        (mature.size >= A2_MATURE_CARDS && avgStability >= A2_AVG_STABILITY_DAYS && avgWords >= A2_AVG_WORDS) ||
            (mature.size >= A2_BROAD_MATURE_CARDS && avgStability >= MATURE_STABILITY_DAYS) -> Level.A2
        else -> Level.A1
    }
}

private const val MIN_STUDIED_FOR_LEVEL = 15
private const val MIN_MATURE_FOR_LEVEL = 10
private const val MATURE_STABILITY_DAYS = 3.0
private const val A2_MATURE_CARDS = 50
private const val A2_BROAD_MATURE_CARDS = 80
private const val A2_AVG_STABILITY_DAYS = 7.0
private const val A2_AVG_WORDS = 4.0
private const val B1_MATURE_CARDS = 150
private const val B1_BROAD_MATURE_CARDS = 250
private const val B1_AVG_STABILITY_DAYS = 14.0
private const val B1_AVG_WORDS = 6.0

/** A practice sheet, or why there is none. */
sealed interface WorksheetResult {
    data class Ready(val worksheet: Worksheet) : WorksheetResult
    data class Failed(val message: String) : WorksheetResult
}

/**
 * Writes a coast's practice sheet with the AI provider picked in Settings, a batch of sentences per request
 * ([worksheetRequests]), each request being told what is on the sheet already. It asks for a few sentences
 * more than the sheet needs and leaves out the weakest ([pickWorksheetSentences]). A model that sends too
 * few is asked again, but not forever: a small coast only has so many sentences in it, so a long sheet can
 * come out shorter than asked for.
 *
 * What a sheet practises follows from how the coast's cards have been studied: every sentence is built from
 * one card, the islands whose cards are slipping or keep being forgotten get the most sentences
 * (`WorksheetFocus.kt`), and of an island's cards the ones that need practice most come first
 * ([WorksheetCardDealer]). The request also carries the level the learner has reached on the coast's route
 * and how they speak of themselves (as a woman, as a man), as the other writing requests do.
 *
 * A coast of any size works: a prompt never carries more than a fixed number of cards and characters, of a
 * large coast it shows the islands its sentences are about ([WorksheetCardDealer]), and a local model, which
 * reads a short prompt, gets a few islands at a time. A long sheet from a large coast is many large requests in a
 * row, which a provider's per-minute limit can turn away; the sheet then waits as long as the provider says
 * ([Completion.retryAfterSeconds]) and carries on, instead of coming out short.
 */
class WorksheetGenerator(
    private val translator: Translator,
    private val settings: SettingsRepository,
    private val flashcards: FlashcardRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
    /** Picks the cards a prompt shows when the coast has more than fit; tests fix it. */
    private val random: Random = Random.Default,
    /** Where the cards are sorted through: off the screen's thread, which thousands of cards would hold up. */
    private val background: CoroutineDispatcher = Dispatchers.Default
) {
    /**
     * A sheet for coast [coastId] as [options] ask.
     *
     * @param onProgress Called with how many sentences are written so far, after each answer.
     */
    suspend fun generate(coastId: Int, options: WorksheetOptions, onProgress: (Int) -> Unit = {}): WorksheetResult {
        val coast = flashcards.observeCoast(coastId).first() ?: return WorksheetResult.Failed("This coast is gone.")
        // This coast's cards only, and of a card only what a sheet is made from: there can be tens of thousands.
        // Of those, the cards of the islands the user picked: the sheet's words and topics come from nowhere else.
        val cards = flashcards.getWorksheetCards(coastId).filter { options.includes(it.islandId) }
        val cardCount = cards.size
        if (cardCount < WORKSHEET_MIN_CARDS) {
            return WorksheetResult.Failed(
                if (options.islandIds == null) "A practice sheet needs at least $WORKSHEET_MIN_CARDS cards on the coast to make new sentences from."
                else "A practice sheet needs at least $WORKSHEET_MIN_CARDS cards to make new sentences from. Pick more islands."
            )
        }
        val ai = when (val connection = AskAi(translator, settings).connect()) {
            is AiConnection.Unavailable -> return WorksheetResult.Failed(connection.message)
            is AiConnection.Ready -> connection
        }
        val provider = ai.provider

        val nativeLanguage = settings.nativeLanguage.first()
        val wording = settings.wording.first()
        val history = settings.worksheetHistory(coastId).first()
        // The route's level when the sheet's cards are mostly the route's; otherwise an empirical estimate once
        // enough custom/imported cards have been studied and retained (null on a freshly imported deck).
        val nowMillis = clock.now().toEpochMilliseconds()
        val route = observeRoute(flashcards, settings, coastId, nowMillis).first()
        val routeIslands = route.levels.flatMap { it.stops }.filter { it.isOnCoast }.mapTo(HashSet()) { it.name }
        val level = if (cards.count { it.islandName in routeIslands } * 2 >= cards.size) {
            route.current.level
        } else {
            estimateWorksheetLevel(cards)
        }
        val count = options.count.coerceIn(WORKSHEET_COUNT_RANGE)
        val isLocal = ai.isLocal
        val alternativesByCard = HashMap<WorksheetCard, List<String>>()
        val (known, vocabulary, dealer) = withContext(background) {
            val known = ArrayList<String>(cards.size)
            cards.forEach {
                known += it.targetText
                known += it.alternatives
                if (it.alternatives.isNotEmpty()) {
                    val key = WorksheetCard(it.islandName, WorksheetCardDealer.oneLine(it.targetText), WorksheetCardDealer.oneLine(it.nativeText))
                    alternativesByCard[key] = it.alternatives
                }
            }
            Triple(
                known,
                CoastVocabulary(known),
                WorksheetCardDealer(
                    cards = cards,
                    limit = if (isLocal) LOCAL_CARD_LIMIT else CARD_LIMIT,
                    perIsland = if (isLocal) LOCAL_ISLAND_CARDS else ISLAND_CARDS,
                    characters = if (isLocal) LOCAL_CHARACTER_LIMIT else CHARACTER_LIMIT,
                    now = nowMillis,
                    random = random,
                    recentAnchors = history.anchoredCards.toHashSet(),
                    difficulty = options.difficulty
                )
            )
        }
        val batch = WorksheetBatch.of(provider, options.difficulty)
        val wanted = worksheetSentencesWanted(count, batch)
        // Fewer after a request that was too much for the model (see below).
        var perRequest = batch.sentences

        val written = ArrayList<WorksheetSentence>()
        var lastError: String? = null
        var requests = 0
        // Requests in a row that added nothing: the provider is down, or has run out of new sentences.
        var barren = 0
        var planned = worksheetRequests(count, batch)
        // Until the sheet is full, and through the planned requests for the sentences to spare, unless only a
        // few of those are left: a request for one or two would carry every card once more for them.
        fun moreNeeded() = written.size < count || (requests < planned && wanted - written.size > perRequest / 4)
        // Extra requests scale with sheet size so a 30+ sentence hard sheet whose batches average slightly fewer
        // unique lines than asked still finishes all requested sentences while progress continues.
        fun maxRequests() = planned + maxOf(EXTRA_REQUESTS, (planned + 1) / 2) * maxOf(1, (batch.sentences + perRequest - 1) / perRequest)
        while (moreNeeded() && requests < maxRequests() && barren < MAX_BARREN_REQUESTS) {
            requests++
            // After a request that was too much, none is made larger than the size that went through.
            val left = wanted - written.size
            val asked = if (perRequest < batch.sentences) minOf(perRequest, left) else worksheetAsk(left, perRequest)
            val dealSnapshot = dealer.snapshot()
            val (prompt, anchors, baseSeen) = withContext(background) {
                val deal = dealer.next(asked)
                val avoidCap = if (isLocal) LOCAL_AVOID_LIMIT else AVOID_LIMIT
                val historyCap = if (isLocal) LOCAL_HISTORY_AVOID_LIMIT else HISTORY_AVOID_LIMIT
                val avoidSentences = (history.targetSentences.takeLast(historyCap) + written.map { it.targetText })
                    .distinct()
                    .takeLast(avoidCap)
                val builtPrompt = buildWorksheetPrompt(
                    cards = deal.cards,
                    targetLanguage = coast.language,
                    nativeLanguage = nativeLanguage,
                    count = asked,
                    difficulty = options.difficulty,
                    region = coast.region,
                    // Recent sheet sentences plus this sheet's latest ones, so consecutive sheets stay fresh
                    // without flooding a new sheet with 60 old sentences or evicting current-sheet lines.
                    avoid = avoidSentences,
                    quotas = deal.quotas,
                    anchors = deal.anchors,
                    level = level,
                    wording = wording,
                    direction = options.direction,
                    // A small local model keeps to three fields better than to four.
                    wordHelp = !isLocal,
                    goal = coast.goal
                )
                Triple(builtPrompt, deal.anchors, written.mapTo(HashSet()) { normalizedWords(it.targetText) })
            }
            var lastReported = minOf(written.size, count)
            fun reportPartial(partial: String) {
                val completeLines = partial.substringBeforeLast('\n', missingDelimiterValue = "")
                if (completeLines.isEmpty()) return
                val inBatch = parseWorksheetResponse(completeLines, anchors)
                    .count { normalizedWords(it.targetText) !in baseSeen }
                val nowWritten = minOf(written.size + inBatch, count)
                if (nowWritten > lastReported) {
                    lastReported = nowWritten
                    onProgress(nowWritten)
                }
            }
            // Turned away for now (the per-minute limit is used up, or the provider is overloaded): the same
            // request goes through after the wait it names.
            val reply = ai.ask(prompt, waitWhileBusy = true, onPartialText = ::reportPartial) { it }
            val text = reply.value
            if (text == null) {
                dealer.restore(dealSnapshot)
                lastError = reply.error
                // The sheet has its sentences: the request was for the ones to spare, which aren't worth another try.
                if (written.size >= count) break
                // The model ran out of time or of room over this many sentences (one that thinks first does,
                // over a batch of hard ones): half as many can go through, in more requests than planned. Half
                // of what was asked for, which is fewer than a batch on a short sheet and at the end of a long one.
                if (reply.isTooLarge && asked > SMALLEST_BATCH) {
                    perRequest = maxOf(SMALLEST_BATCH, minOf(perRequest, asked) / 2)
                    planned = requests + (wanted - written.size + perRequest - 1) / perRequest
                    continue
                }
                barren++
                // A provider that can't be reached for the first request won't answer the next one either.
                if (written.isEmpty()) break else continue
            }
            val fresh = withContext(background) {
                val seen = HashSet(baseSeen)
                parseWorksheetResponse(text, anchors)
                    .filter { seen.add(normalizedWords(it.targetText)) }
                    .map { it.copy(newWords = vocabulary.wordHelp(it.targetText, it.newWords, maxWords = options.difficulty.wordHelpLimit)) }
            }
            if (fresh.isEmpty() && written.isEmpty()) lastError = "${provider.label}'s answer couldn't be read. Try again."
            barren = if (fresh.isEmpty()) barren + 1 else 0
            written += fresh
            onProgress(minOf(written.size, count))
        }

        val sentences = withContext(background) {
            pickWorksheetSentences(
                candidates = written,
                vocabulary = vocabulary,
                known = known,
                count = count,
                difficulty = options.difficulty,
                cardAlternatives = alternativesByCard,
                direction = options.direction,
                expectsWordHelp = !isLocal,
                recentSentences = history.targetSentences
            )
        }
        if (sentences.isEmpty()) return WorksheetResult.Failed(lastError ?: "${provider.label} sent no sentences. Try again.")
        settings.recordWorksheet(
            coastId = coastId,
            anchoredCards = sentences.mapNotNull { it.card?.historyKey() },
            targetSentences = sentences.map { WorksheetCardDealer.oneLine(it.targetText) }
        )
        return WorksheetResult.Ready(
            Worksheet(
                coastName = coast.displayName,
                targetLanguage = coast.language,
                nativeLanguage = nativeLanguage,
                options = options.copy(count = count),
                sentences = sentences,
                date = clock.todayIn(timeZone),
                providerLabel = provider.label,
                cardCount = cardCount,
                islandCount = dealer.islandCount
            )
        )
    }

    private companion object {
        /** Cards a prompt shows at most; beyond that it mostly costs tokens. A larger coast is dealt out over the prompts. */
        const val CARD_LIMIT = 400

        /** Cards of one island in a prompt, while other islands wait for room: plenty to build sentences from. */
        const val ISLAND_CARDS = 40

        /**
         * Characters of card text a prompt carries at most (about 100 a card): cards with long texts, as an
         * imported deck can have, must not make a prompt many times the size of an ordinary one.
         */
        const val CHARACTER_LIMIT = 40_000

        /** A local model's prompt must stay short: fewer cards, of fewer islands, and less text. */
        const val LOCAL_CARD_LIMIT = 60
        const val LOCAL_ISLAND_CARDS = 20
        const val LOCAL_CHARACTER_LIMIT = 4_000

        /** A request lists this many of the sentences already written; beyond that it mostly costs tokens. */
        const val AVOID_LIMIT = 60
        const val LOCAL_AVOID_LIMIT = 15

        /** Past-sheet sentences carried into a prompt's avoid list; full history still informs spare selection. */
        const val HISTORY_AVOID_LIMIT = 15
        const val LOCAL_HISTORY_AVOID_LIMIT = 5

        /** How many requests more than a sheet needs are made when answers come back short. */
        const val EXTRA_REQUESTS = 2

        /** After this many requests in a row that added nothing, the sheet is made from what there is. */
        const val MAX_BARREN_REQUESTS = 2
    }
}
