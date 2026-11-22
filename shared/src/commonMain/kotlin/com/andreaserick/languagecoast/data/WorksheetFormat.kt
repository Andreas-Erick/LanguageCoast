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

/* The prompt that asks a model for a practice sheet's sentences, and reading its answer. */


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
internal fun shortened(text: String, limit: Int): String {
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
