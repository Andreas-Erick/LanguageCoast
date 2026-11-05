package com.andreaserick.languagecoast.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.random.Random
import kotlin.time.Clock

/*
 * The practice sheet: new sentences to translate, written by the AI provider from the cards of one coast
 * and laid out as a PDF (see [WorksheetPrinter]). A sentence is to make sense first: something a person
 * would say in the situation of one island, built from that island's cards, with at most
 * [WORKSHEET_NEW_WORDS] word the coast doesn't have where it needs one. The prompt carries the cards and
 * says so, and of the sentences that come back the ones that stray further from the cards' words are the
 * first to be left out (see [CoastVocabulary]).
 */

/** How hard a practice sheet's sentences are. */
enum class WorksheetDifficulty(val label: String, val hint: String) {
    Easy("Easy", "Short sentences that stay close to your cards."),
    Medium("Medium", "An island's words put together into new everyday sentences."),
    Hard("Hard", "Longer sentences that say two things about one situation.")
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

/**
 * Words a sheet's sentence may use that are on none of the coast's cards. Without any, a small coast only
 * has odd sentences in it; with one, a sentence can be made to mean something. [buildWorksheetPrompt] says
 * the number in words.
 */
const val WORKSHEET_NEW_WORDS = 1

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
        "Level: easy. Short sentences of 3 to 6 words with one simple clause. Stay close to the flashcards: take one and change a word or two for other words of its topic."
    WorksheetDifficulty.Medium ->
        "Level: medium. Sentences of 5 to 10 words that put words from several flashcards of one topic together. Mix statements, questions and negations."
    WorksheetDifficulty.Hard ->
        "Level: hard. Longer sentences of 9 to 16 words that say two things about one situation, in two clauses where the flashcards have a word to join them. " +
            "Mix statements, questions and negations."
}

/**
 * The prompt that asks a model for [count] new sentences in [targetLanguage] built from [cards], each with
 * its translation into [nativeLanguage]. Its rules come in the order they matter: a sentence must make
 * sense, keep to one island's topic and use the cards' words, with [WORKSHEET_NEW_WORDS] new word allowed
 * so that the first rule never loses to the third.
 *
 * @param cards The coast's cards (or some of its islands, when there are too many), island by island.
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
    val avoidRule = if (avoid.isEmpty()) "" else "\n        8. Do not repeat any of these, which are already on the sheet: " + avoid.joinToString(" | ")
    val sentences = if (count == 1) "1 new $targetLanguage sentence" else "$count new $targetLanguage sentences"
    // The flashcards are joined on afterwards: trimIndent would trip over their unindented lines.
    val intro = """
        You are a $targetLanguage teacher writing a translation exercise for one learner, whose native language is $nativeLanguage.

        The flashcards below are what the learner knows of $targetLanguage so far. They are grouped by topic, one per line, as "$targetLanguage = $nativeLanguage":
    """.trimIndent()
    val task = """
        Write $sentences for the learner to translate, each with its $nativeLanguage translation.

        Rules:
        1. Every sentence must make sense: something a real person would say or hear in an everyday situation. If you can't picture who would say it and when, write a different sentence.
        2. Every sentence belongs to one topic and fits that topic's situation. Build it from that topic's flashcards; small everyday words (pronouns, articles, "and", "not", "very") may come from any flashcard. Never put words from unrelated topics together just to make a sentence new. Spread the sentences over the topics.
        3. Build the sentences from the $targetLanguage words on the flashcards. A word may take another form of itself (a verb for another person, a noun in the plural or in another case, an adjective that agrees), and names and numbers are allowed. Where a sentence needs it to make sense or to sound natural, it may also use one common, simple word that is not on the flashcards; never more than one, and never a rare word.
        4. Use only grammar the flashcards show: their tenses and their constructions.
        5. Every sentence must be new: different from every flashcard and from the other sentences. Never copy a flashcard.
        6. ${levelRule(difficulty)}
        7. Every sentence must be correct, natural $targetLanguage that a native speaker$where would say, and its $nativeLanguage translation must be exact and natural.$avoidRule

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

    /**
     * Whether [sentence] keeps to the coast's words, the [WORKSHEET_NEW_WORDS] new word a sheet's sentence may
     * bring aside. In a script without spaces a new word shows as its characters, mostly two of them.
     */
    fun keepsToCoast(sentence: String): Boolean {
        val unknown = unknownWords(sentence)
        val perWord = if (unknown.all { it.length == 1 && writtenWithoutSpaces(it[0]) }) CHARACTERS_PER_NEW_WORD else 1
        return unknown.size <= WORKSHEET_NEW_WORDS * perWord
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

        /** How many unknown characters a new word may be in a script without spaces. */
        const val CHARACTERS_PER_NEW_WORD = 2

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
 * sentences with the largest share of words the coast doesn't have. The one new word a sentence may bring
 * ([CoastVocabulary.keepsToCoast]) doesn't count against it: a sentence that needed one to make sense is
 * as good as one that needed none.
 */
fun pickWorksheetSentences(
    candidates: List<WorksheetSentence>,
    vocabulary: CoastVocabulary,
    known: List<String>,
    count: Int
): List<WorksheetSentence> {
    if (candidates.size <= count) return candidates
    val knownWords = known.mapTo(HashSet(), ::normalizedWords)
    // Worked out once per sentence, not once per comparison: a long sheet from a large coast has hundreds.
    class Ranked(val index: Int, val isCopy: Boolean, val strays: Double)
    val best = candidates
        .mapIndexed { index, sentence ->
            val text = sentence.targetText
            Ranked(index, normalizedWords(text) in knownWords, if (vocabulary.keepsToCoast(text)) 0.0 else vocabulary.unknownShare(text))
        }
        .sortedWith(compareBy({ it.isCopy }, { it.strays }, { it.index }))
        .take(count)
    return best.map { it.index }.sorted().map { candidates[it] }
}

/**
 * Deals out a coast's cards to the prompts of one sheet, [limit] cards and [characters] of card text per
 * prompt at most, in the coast's order.
 *
 * A coast that fits is shown whole every time. A larger one, be it thousands of cards, is shown island by
 * island: a sentence is built from one island's cards, so a prompt gets up to [perIsland] cards of an island
 * (enough to build from) rather than a few of each. The islands take turns from one prompt to the next, so
 * a long sheet gets around the whole coast, and a huge island can't crowd the others out. Room that is left
 * once every island had its turn goes to more of their cards.
 */
internal class WorksheetCardDealer(
    cards: List<PlacedCard>,
    private val limit: Int,
    private val perIsland: Int,
    private val characters: Int,
    private val random: Random
) {
    private val lines = cards.map { WorksheetCard(it.islandName, oneLine(it.card.targetText), oneLine(it.card.nativeText)) }
    private val sizes = lines.map { it.targetText.length + it.nativeText.length + SEPARATOR_LENGTH }
    private val shortest = sizes.minOrNull() ?: 0
    private val fitsWhole = lines.size <= limit && sizes.sum() <= characters

    /** The islands' cards (as indices), in the order the islands take their turns. */
    private val islands = cards.indices.groupBy { cards[it].card.islandId }.values.shuffled(random)
    private var turn = 0

    /** The cards for the next prompt. */
    fun next(): List<WorksheetCard> {
        if (fitsWhole) return lines
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

        var visited = 0
        while (!full() && visited < islands.size) {
            var taken = 0
            for (index in islands[(turn + visited) % islands.size].shuffled(random)) {
                if (taken < perIsland && take(index)) taken++ else passedOver += index
            }
            visited++
        }
        turn = (turn + visited) % islands.size
        if (!full()) for (index in passedOver.shuffled(random)) if (full()) break else take(index)
        // Cards too long for any prompt: the shortest ones are still better than a prompt without cards.
        if (picked.isEmpty()) picked += sizes.indices.sortedBy { sizes[it] }.take(WORKSHEET_MIN_CARDS)
        return picked.sorted().map { lines[it] }
    }

    private companion object {
        /** " = " and the line break around a card's two sides. */
        const val SEPARATOR_LENGTH = 4

        /** A card's text on one line, as the prompt lists one card per line. */
        fun oneLine(text: String): String = text.lines().joinToString(" ") { it.trim() }.trim()
    }
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
 * so a long sheet can come out shorter than asked for.
 *
 * A coast of any size works: a prompt never carries more than a fixed number of cards and characters, a
 * large coast's islands take turns in the prompts ([WorksheetCardDealer]), and a local model, which reads a
 * short prompt, gets a few islands at a time. A long sheet from a large coast is many large requests in a
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
        val (vocabulary, dealer) = withContext(background) {
            CoastVocabulary(known) to WorksheetCardDealer(
                cards = placed,
                limit = if (isLocal) LOCAL_CARD_LIMIT else CARD_LIMIT,
                perIsland = if (isLocal) LOCAL_ISLAND_CARDS else ISLAND_CARDS,
                characters = if (isLocal) LOCAL_CHARACTER_LIMIT else CHARACTER_LIMIT,
                random = random
            )
        }
        val wanted = worksheetSentencesWanted(count, isLocal)
        val perRequest = if (isLocal) LOCAL_BATCH else CLOUD_BATCH

        val written = ArrayList<WorksheetSentence>()
        var lastError: String? = null
        var requests = 0
        // Requests in a row that added nothing: the provider is down, or has run out of new sentences.
        var barren = 0
        // Times in a row the provider said "not now": see MAX_WAITS.
        var waits = 0
        val planned = worksheetRequests(count, isLocal)
        // Until the sheet is full, and through the planned requests for the sentences to spare.
        fun moreNeeded() = written.size < count || (requests < planned && written.size < wanted)
        while (moreNeeded() && requests < planned + EXTRA_REQUESTS && barren < MAX_BARREN_REQUESTS) {
            requests++
            val prompt = withContext(background) {
                buildWorksheetPrompt(
                    cards = dealer.next(),
                    targetLanguage = coast.language,
                    nativeLanguage = nativeLanguage,
                    count = minOf(perRequest, wanted - written.size),
                    difficulty = options.difficulty,
                    region = coast.region,
                    // Every repeat is dropped below anyway; the prompt names the latest ones, which a model repeats most readily.
                    avoid = written.takeLast(AVOID_LIMIT).map { it.targetText }
                )
            }
            var completion = translator.complete(engine, prompt)
            // Turned away for now (the per-minute limit is used up, or the provider is overloaded): the same
            // request goes through after the wait it names.
            while (completion.text == null && waits < MAX_WAITS) {
                val seconds = completion.retryAfterSeconds ?: break
                waits++
                delay(seconds.coerceIn(1, MAX_WAIT_SECONDS) * 1_000L)
                completion = translator.complete(engine, prompt)
            }
            val text = completion.text
            if (text == null) {
                lastError = completion.errorMessage
                barren++
                // A provider that can't be reached for the first request won't answer the next one either.
                if (written.isEmpty()) break else continue
            }
            waits = 0
            val fresh = withContext(background) {
                val seen = written.mapTo(HashSet()) { normalizedWords(it.targetText) }
                parseWorksheetResponse(text).filter { seen.add(normalizedWords(it.targetText)) }
            }
            if (fresh.isEmpty() && written.isEmpty()) lastError = "${provider.label}'s answer couldn't be read. Try again."
            barren = if (fresh.isEmpty()) barren + 1 else 0
            written += fresh
            onProgress(minOf(written.size, count))
        }

        val sentences = withContext(background) { pickWorksheetSentences(written, vocabulary, known, count) }
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

        /** How many requests more than a sheet needs are made when answers come back short. */
        const val EXTRA_REQUESTS = 2

        /** After this many requests in a row that added nothing, the sheet is made from what there is. */
        const val MAX_BARREN_REQUESTS = 2

        /**
         * How often in a row a sheet waits for a provider that turns it away for now, and how long at most each
         * time. A per-minute limit is over within the minute; a limit that isn't (a day's quota) ends the sheet
         * with what it has.
         */
        const val MAX_WAITS = 4
        const val MAX_WAIT_SECONDS = 60
    }
}
