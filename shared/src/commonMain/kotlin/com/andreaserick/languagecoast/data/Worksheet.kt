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

/** What a practice sheet trains: sentences to translate, or whatever the learner's brief asks for. */
enum class SheetKind(val label: String) {
    Translation("Translation"),
    Custom("Custom")
}

/**
 * How a custom sheet's exercise is answered.
 *
 * @property token The word that opens an exercise's line in the model's reply ("short || ..."): one plain
 *     word, which even a small model copies faithfully.
 */
enum class ExerciseForm(val label: String, internal val token: String) {
    GapFill("Gap fill", "gap"),
    MultipleChoice("Multiple choice", "choice"),
    ShortAnswer("Short answer", "short")
}

/**
 * One exercise of a custom sheet: a task for the learner and its answer.
 *
 * @property instruction What to do, in the learner's own language ("Write the plural."); printed once above a
 *     run of exercises that share it. Null when the model gave none.
 * @property task What the learner sees, in the coast's language; it never shows the [answer].
 * @property options The three choices of a multiple-choice exercise, one of them the [answer]; empty otherwise.
 * @property wordHelp Words of the task that are on none of the learner's cards, printed under it.
 */
data class Exercise(
    val form: ExerciseForm,
    val instruction: String?,
    val task: String,
    val answer: String,
    val options: List<String> = emptyList(),
    val wordHelp: List<WordHelp> = emptyList()
)

/**
 * What the user picks for a practice sheet.
 *
 * @property islandIds The islands whose cards the sheet is made from, or null for every island of the coast.
 * @property direction Which way a translation sheet goes; a custom sheet has none.
 * @property brief For a [SheetKind.Custom] sheet, the learner's own words for what to practise.
 * @property forms The exercise forms a custom sheet may use; none (or all of them) leaves the mix to the model.
 */
data class WorksheetOptions(
    val count: Int = WORKSHEET_DEFAULT_COUNT,
    val difficulty: WorksheetDifficulty = WorksheetDifficulty.Medium,
    val direction: WorksheetDirection = WorksheetDirection.ToTarget,
    val islandIds: Set<Int>? = null,
    val kind: SheetKind = SheetKind.Translation,
    val brief: String = "",
    val forms: Set<ExerciseForm> = emptySet()
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
 * weakest can be left out ([hasSpares]). Which one a provider gets is up to its [ProviderCapacity].
 */
enum class WorksheetBatch(val sentences: Int, val seconds: Int, val hasSpares: Boolean) {
    Local(5, 40, false),
    Thinking(12, 90, true),
    HardThinking(8, 75, true),
    Cloud(25, 10, true),
    HardCloud(12, 35, true);

    companion object {
        fun of(provider: TranslationProvider, difficulty: WorksheetDifficulty = WorksheetDifficulty.Medium): WorksheetBatch =
            ProviderCapacity.of(provider).sheetBatch(difficulty)
    }
}

/** A request that was too much for the model is tried again with half as many sentences, down to this many. */
internal const val SMALLEST_BATCH = 3

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
 * @property exercises A custom sheet's exercises; a translation sheet has [sentences] instead.
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
    val islandCount: Int,
    val exercises: List<Exercise> = emptyList()
) {
    /** Whether this is a custom sheet, made from a brief. */
    val isCustom: Boolean get() = options.kind == SheetKind.Custom

    /** How many numbered exercises the sheet has, of either kind. */
    val exerciseCount: Int get() = if (isCustom) exercises.size else sentences.size

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

    /** A file name such as "german-coast-practice-2026-11-05.pdf", or "german-coast-custom-practice-2026-11-05.pdf". */
    val fileName: String
        get() {
            val coast = coastName.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").replace(Regex("-+"), "-").trim('-')
            return "${coast.ifEmpty { "coast" }}-${if (isCustom) "custom-" else ""}practice-$date.pdf"
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
