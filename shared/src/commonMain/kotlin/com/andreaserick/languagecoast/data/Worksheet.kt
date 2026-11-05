package com.andreaserick.languagecoast.data

import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.random.Random
import kotlin.time.Clock

/*
 * The practice sheet: new sentences to translate, written by the AI provider from the cards of one coast
 * and laid out as a PDF (see [WorksheetPrinter]). The sentences may only use what the coast's islands
 * teach: the prompt carries the cards and says so, and of the sentences that come back the ones that stay
 * closest to the cards' words are kept (see [CoastVocabulary]).
 */

/** How hard a practice sheet's sentences are. */
enum class WorksheetDifficulty(val label: String, val hint: String) {
    Easy("Easy", "Short sentences that stay close to your cards."),
    Medium("Medium", "Your words put together into new everyday sentences."),
    Hard("Hard", "Longer sentences that mix words from several islands.")
}

/** Which way a practice sheet is translated. */
enum class WorksheetDirection {
    /** The sheet shows sentences in the user's language, to write in the coast's language. */
    ToTarget,

    /** The sheet shows sentences in the coast's language, to write in the user's language. */
    ToNative
}

/** What the user picks for a practice sheet. */
data class WorksheetOptions(
    val count: Int = WORKSHEET_DEFAULT_COUNT,
    val difficulty: WorksheetDifficulty = WorksheetDifficulty.Medium,
    val direction: WorksheetDirection = WorksheetDirection.ToTarget
)

/** Sentences on a practice sheet unless the user picks another number. */
const val WORKSHEET_DEFAULT_COUNT = 10

/** The fewest and the most sentences a practice sheet can have. */
val WORKSHEET_COUNT_RANGE = 3..500

/**
 * Sentences asked for per request. A cloud model's answer holds a few dozen sentence pairs before it is cut
 * off or times out; a local model's holds a handful.
 */
private const val CLOUD_BATCH = 25
private const val LOCAL_BATCH = 5

/**
 * How many sentences a sheet of [count] asks a model for in all. A cloud model is asked for a few to spare,
 * so the ones that stray furthest from the cards can be left out; a local model is too slow for that.
 */
internal fun worksheetSentencesWanted(count: Int, isLocal: Boolean): Int = if (isLocal) count else count + (count / 4).coerceIn(2, 10)

/** How many requests a sheet of [count] sentences takes if every answer comes back complete. */
fun worksheetRequests(count: Int, isLocal: Boolean): Int {
    val batch = if (isLocal) LOCAL_BATCH else CLOUD_BATCH
    return (worksheetSentencesWanted(count, isLocal) + batch - 1) / batch
}

/** A coast needs this many cards before a sheet can be made from it: fewer leave nothing to recombine. */
const val WORKSHEET_MIN_CARDS = 5

/** One sentence of a practice sheet, in the coast's language and in the user's. */
data class WorksheetSentence(val targetText: String, val nativeText: String)

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

    /** What sentence [sentence] shows on the sheet, and what the answer key gives for it. */
    fun question(sentence: WorksheetSentence): String = if (options.direction == WorksheetDirection.ToTarget) sentence.nativeText else sentence.targetText
    fun answer(sentence: WorksheetSentence): String = if (options.direction == WorksheetDirection.ToTarget) sentence.targetText else sentence.nativeText

    /** A file name such as "german-coast-practice-2026-11-05.pdf". */
    val fileName: String
        get() {
            val coast = coastName.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").replace(Regex("-+"), "-").trim('-')
            return "${coast.ifEmpty { "coast" }}-practice-$date.pdf"
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
data class WorksheetCard(val island: String, val targetText: String, val nativeText: String)

private fun levelRule(difficulty: WorksheetDifficulty): String = when (difficulty) {
    WorksheetDifficulty.Easy ->
        "Level: easy. Short sentences of 3 to 6 words with one simple clause. Stay close to the flashcards: take one and change a word or two for other flashcard words."
    WorksheetDifficulty.Medium ->
        "Level: medium. Sentences of 5 to 10 words that put words from different flashcards together. Mix statements, questions and negations."
    WorksheetDifficulty.Hard ->
        "Level: hard. Longer sentences of 9 to 16 words that bring together words from several topics, with two clauses where the flashcards have a word to join them. " +
            "Mix statements, questions and negations."
}

/**
 * The prompt that asks a model for [count] new sentences in [targetLanguage] built only from [cards], each
 * with its translation into [nativeLanguage].
 *
 * @param cards The coast's cards (or a sample of them, for a small model), island by island.
 * @param region Where the language will be used, so the sentences use that place's words; null for no preference.
 * @param avoid Sentences in [targetLanguage] already written for this sheet, when asking for more.
 */
fun buildWorksheetPrompt(
    cards: List<WorksheetCard>,
    targetLanguage: String,
    nativeLanguage: String,
    count: Int,
    difficulty: WorksheetDifficulty,
    region: String? = null,
    avoid: List<String> = emptyList()
): String {
    val flashcards = cards.groupBy { it.island }.entries.joinToString("\n\n") { (island, group) ->
        "[$island]\n" + group.joinToString("\n") { "${it.targetText} = ${it.nativeText}" }
    }
    val where = region?.trim()?.takeIf { it.isNotEmpty() }?.let { " from $it" } ?: ""
    // Raw strings don't process escapes, so the rule's line break (with the raw string's indent) is built outside it.
    val avoidRule = if (avoid.isEmpty()) "" else "\n        6. Do not repeat any of these, which are already on the sheet: " + avoid.joinToString(" | ")
    val sentences = if (count == 1) "1 new $targetLanguage sentence" else "$count new $targetLanguage sentences"
    // The flashcards are joined on afterwards: trimIndent would trip over their unindented lines.
    val intro = """
        You are a $targetLanguage teacher writing a translation exercise for one learner, whose native language is $nativeLanguage.

        The flashcards below are everything the learner knows of $targetLanguage so far. They are grouped by topic, one per line, as "$targetLanguage = $nativeLanguage":
    """.trimIndent()
    val task = """
        Write $sentences for the learner to translate, each with its $nativeLanguage translation.

        Rules:
        1. Use ONLY $targetLanguage words that are on the flashcards. A word may take another form of itself (a verb for another person, a noun in the plural or in another case, an adjective that agrees), and names and numbers are allowed. No other words: if a sentence needs a word that is not on the flashcards, write a different sentence.
        2. Use only grammar the flashcards show: their tenses and their constructions.
        3. Every sentence must be new. Put the flashcards' words together in a way no flashcard does, and make every sentence different from the others. Never copy a flashcard.
        4. ${levelRule(difficulty)}
        5. Every sentence must be correct, natural $targetLanguage that a native speaker$where would say, and its $nativeLanguage translation must be exact and natural.$avoidRule

        Write EXACTLY one line per sentence and nothing else, in this format:
        [sentence in $targetLanguage] || [translation in $nativeLanguage]
    """.trimIndent()
    return "$intro\n\n$flashcards\n\n$task"
}

/**
 * Parses a model's reply to [buildWorksheetPrompt]: one sentence per line, the coast's language first, then
 * `||` and the translation. Numbering, bullets, markdown and the prompt's brackets are tolerated; lines
 * without both parts are dropped, as are repeats.
 */
fun parseWorksheetResponse(text: String): List<WorksheetSentence> =
    text.replace("```", "").replace("**", "").lines()
        .map { it.trim().replace(LINE_MARKER, "") }
        .filter { it.contains("||") }
        .mapNotNull { line ->
            val parts = line.split("||").map { unwrapped(it.trim()) }
            val target = parts[0]
            val native = parts.getOrNull(1).orEmpty()
            if (target.isEmpty() || native.isEmpty()) null else WorksheetSentence(target, native)
        }
        .distinctBy { normalizedWords(it.targetText) }

/** "1." or "-" at the start of a line. */
private val LINE_MARKER = Regex("^(?:[-*•]|\\d+[.)])\\s*")

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
    private val words = HashSet<String>()
    private val stems = HashSet<String>()
    private val characters = HashSet<Char>()

    init {
        texts.flatMap(::normalizedWords).forEach { word ->
            words += word
            for (length in stemLength(word.length, STEM_SHARE)..word.length) stems += word.substring(0, length)
            word.forEach { if (writtenWithoutSpaces(it)) characters += it }
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

    private fun isKnown(word: String): Boolean {
        if (word in words || word.all { it.isDigit() }) return true
        if (word.length < MIN_STEM) return false
        return (stemLength(word.length, FORM_SHARE)..word.length).any { word.substring(0, it) in stems }
    }

    private companion object {
        /** Stems shorter than this say too little: "der" would vouch for "derzeit". */
        const val MIN_STEM = 3

        /** How much of a card's word must match for it to vouch for another form of itself. */
        const val STEM_SHARE = 0.6

        /** How much of a sentence's word must be such a stem: half lets an ending as long as the stem pass ("búð", "búðina"). */
        const val FORM_SHARE = 0.5

        fun stemLength(length: Int, share: Double): Int = maxOf(MIN_STEM, (length * share + 0.999).toInt()).coerceAtMost(length)

        /** Chinese, Japanese, Thai, Lao, Khmer and Burmese write sentences without spaces between words. */
        fun writtenWithoutSpaces(char: Char): Boolean =
            char in '\u0E00'..'\u0EFF' || char in '\u1000'..'\u109F' || char in '\u1780'..'\u17FF' ||
                char in '\u3040'..'\u30FF' || char in '\u3400'..'\u4DBF' || char in '\u4E00'..'\u9FFF'
    }
}

/**
 * The [count] sentences of [candidates] that keep closest to the coast's cards, in the order the model
 * wrote them. Copies of a card ([known], the cards' translations) are the last resort, then come the
 * sentences with the largest share of words the coast doesn't have.
 */
fun pickWorksheetSentences(
    candidates: List<WorksheetSentence>,
    vocabulary: CoastVocabulary,
    known: List<String>,
    count: Int
): List<WorksheetSentence> {
    if (candidates.size <= count) return candidates
    val knownWords = known.mapTo(HashSet(), ::normalizedWords)
    val best = candidates.withIndex()
        .sortedWith(compareBy({ normalizedWords(it.value.targetText) in knownWords }, { vocabulary.unknownShare(it.value.targetText) }, { it.index }))
        .take(count)
    return best.sortedBy { it.index }.map { it.value }
}

/** A practice sheet, or why there is none. */
sealed interface WorksheetResult {
    data class Ready(val worksheet: Worksheet) : WorksheetResult
    data class Failed(val message: String) : WorksheetResult
}

/**
 * Writes a coast's practice sheet with the AI provider picked in Settings, a batch of sentences per request
 * ([worksheetRequests]), each request being told what is on the sheet already. It asks for a few sentences
 * more than the sheet needs and keeps the ones closest to the coast's words ([pickWorksheetSentences]). A
 * model that sends too few is asked again, but not forever: a small coast only has so many sentences in it,
 * so a long sheet can come out shorter than asked for. A local model reads a short prompt, so it gets a
 * sample of the cards.
 */
class WorksheetGenerator(
    private val translator: Translator,
    private val settings: SettingsRepository,
    private val flashcards: FlashcardRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
    /** Picks the cards a prompt shows when the coast has more than fit; tests fix it. */
    private val random: Random = Random.Default
) {
    /**
     * A sheet for coast [coastId] as [options] ask.
     *
     * @param onProgress Called with how many sentences are written so far, after each answer.
     */
    suspend fun generate(coastId: Int, options: WorksheetOptions, onProgress: (Int) -> Unit = {}): WorksheetResult {
        val coast = flashcards.observeCoast(coastId).first() ?: return WorksheetResult.Failed("This coast is gone.")
        val placed = flashcards.observeAllCards().first().filter { it.coastId == coastId }
        if (placed.size < WORKSHEET_MIN_CARDS) {
            return WorksheetResult.Failed("A practice sheet needs at least $WORKSHEET_MIN_CARDS cards on the coast to make new sentences from.")
        }
        val provider = settings.translationProvider.first()
        val engine = when (val choice = settings.currentEngine(provider)) {
            is EngineChoice.Missing -> return WorksheetResult.Failed(choice.message)
            is EngineChoice.Ready -> choice.engine
        }
        if (engine == TranslationEngine.OnDevice) {
            return WorksheetResult.Failed("On-device translation can't write sentences. Pick an AI provider in Settings.")
        }

        val nativeLanguage = settings.nativeLanguage.first()
        val count = options.count.coerceIn(WORKSHEET_COUNT_RANGE)
        val isLocal = engine is TranslationEngine.LocalAi
        val known = placed.flatMap { listOf(it.card.targetText) + it.card.alternatives }
        val vocabulary = CoastVocabulary(known)
        val wanted = worksheetSentencesWanted(count, isLocal)
        val perRequest = if (isLocal) LOCAL_BATCH else CLOUD_BATCH

        val written = ArrayList<WorksheetSentence>()
        var lastError: String? = null
        var requests = 0
        // Requests in a row that added nothing: the provider is down, or has run out of new sentences.
        var barren = 0
        val planned = worksheetRequests(count, isLocal)
        // Until the sheet is full, and through the planned requests for the sentences to spare.
        fun moreNeeded() = written.size < count || (requests < planned && written.size < wanted)
        while (moreNeeded() && requests < planned + EXTRA_REQUESTS && barren < MAX_BARREN_REQUESTS) {
            requests++
            val prompt = buildWorksheetPrompt(
                cards = sample(placed, if (isLocal) LOCAL_CARD_LIMIT else CARD_LIMIT),
                targetLanguage = coast.language,
                nativeLanguage = nativeLanguage,
                count = minOf(perRequest, wanted - written.size),
                difficulty = options.difficulty,
                region = coast.region,
                // Every repeat is dropped below anyway; the prompt names the latest ones, which a model repeats most readily.
                avoid = written.takeLast(AVOID_LIMIT).map { it.targetText }
            )
            val completion = translator.complete(engine, prompt)
            val text = completion.text
            if (text == null) {
                lastError = completion.errorMessage
                barren++
                // A provider that can't be reached for the first request won't answer the next one either.
                if (written.isEmpty()) break else continue
            }
            val seen = written.mapTo(HashSet()) { normalizedWords(it.targetText) }
            val fresh = parseWorksheetResponse(text).filter { seen.add(normalizedWords(it.targetText)) }
            if (fresh.isEmpty() && written.isEmpty()) lastError = "${provider.label}'s answer couldn't be read. Try again."
            barren = if (fresh.isEmpty()) barren + 1 else 0
            written += fresh
            onProgress(minOf(written.size, count))
        }

        val sentences = pickWorksheetSentences(written, vocabulary, known, count)
        if (sentences.isEmpty()) return WorksheetResult.Failed(lastError ?: "${provider.label} sent no sentences. Try again.")
        return WorksheetResult.Ready(
            Worksheet(
                coastName = coast.displayName,
                targetLanguage = coast.language,
                nativeLanguage = nativeLanguage,
                options = options.copy(count = count),
                sentences = sentences,
                date = clock.todayIn(timeZone),
                providerLabel = provider.label,
                cardCount = placed.size,
                islandCount = placed.distinctBy { it.card.islandId }.size
            )
        )
    }

    /** At most [limit] of [cards] for a prompt, picked at random when there are more, in the coast's order. */
    private fun sample(cards: List<PlacedCard>, limit: Int): List<WorksheetCard> {
        val shown = if (cards.size <= limit) cards else cards.indices.shuffled(random).take(limit).sorted().map { cards[it] }
        return shown.map { WorksheetCard(it.islandName, oneLine(it.card.targetText), oneLine(it.card.nativeText)) }
    }

    /** A card's text on one line, as the prompt lists one card per line. */
    private fun oneLine(text: String): String = text.lines().joinToString(" ") { it.trim() }.trim()

    private companion object {
        /** Cards a prompt shows at most; beyond that it mostly costs tokens. Each request draws its own sample. */
        const val CARD_LIMIT = 400

        /** A local model's prompt must stay short. */
        const val LOCAL_CARD_LIMIT = 60

        /** A request lists this many of the sentences already written; beyond that it mostly costs tokens. */
        const val AVOID_LIMIT = 60

        /** How many requests more than a sheet needs are made when answers come back short. */
        const val EXTRA_REQUESTS = 2

        /** After this many requests in a row that added nothing, the sheet is made from what there is. */
        const val MAX_BARREN_REQUESTS = 2
    }
}
