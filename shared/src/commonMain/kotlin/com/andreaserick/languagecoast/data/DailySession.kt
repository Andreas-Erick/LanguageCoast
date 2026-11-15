package com.andreaserick.languagecoast.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/*
 * The daily session: one short sit-down a day on the focus coast, sized so it is always finishable.
 * It reviews a handful of due cards, then offers a few new sentences the app prepared for the coast's
 * goal (see [SentenceSuggester]). After days away it shrinks to a welcome-back size instead of greeting
 * the user with a backlog.
 */

/** Due cards a daily session reviews at most. */
const val DAILY_REVIEW_CAP = 12

/** New sentences a daily session offers at most. */
const val DAILY_NEW_SENTENCES = 3

/** Due cards a session reviews at most after days away; see [WELCOME_BACK_AFTER_DAYS]. */
const val WELCOME_BACK_REVIEW_CAP = 5

/** New sentences a session offers after days away. */
const val WELCOME_BACK_NEW_SENTENCES = 2

/** After this many days without a session the next one is a smaller welcome-back session. */
const val WELCOME_BACK_AFTER_DAYS = 3

/** How many sentences are asked for at a time when the pool runs low. */
const val SUGGESTION_BATCH = 8

/**
 * The basics come first: until this many starter sentences ([PhraseBank]) were offered on a coast, the daily
 * session's new sentences come from the bank (greetings, getting by, small talk, ...), not from the AI.
 * At [DAILY_NEW_SENTENCES] a day that is about six weeks.
 */
const val BASICS_FROM_BANK = 120

/**
 * How fast the user wants to go: how much new a daily session brings, and how many reviews it takes on
 * to keep up with that. Steady is the default; the faster ones are for someone racing to the next level,
 * and are a choice, since the reviews of twenty new cards a day add up.
 *
 * @property newPerDay New cards and sentences a session introduces at most.
 * @property reviewCap Due cards a session reviews at most.
 */
enum class Pace(val key: String, val label: String, val newPerDay: Int, val reviewCap: Int) {
    Steady("steady", "Steady", DAILY_NEW_SENTENCES, DAILY_REVIEW_CAP),
    Fast("fast", "Fast", 10, 40),
    Sprint("sprint", "Sprint", 20, 80);

    companion object {
        fun fromKey(key: String?): Pace = entries.firstOrNull { it.key == key } ?: Steady
    }
}

/** Days a coast's remaining new cards take at [pace], rounded up; 0 when there are none. */
fun daysToLearn(newCards: Int, pace: Pace): Int = (newCards + pace.newPerDay - 1) / pace.newPerDay

/**
 * What today's session on a coast holds.
 *
 * @property reviewCount Due cards it reviews (capped).
 * @property newCount Everything new it brings: [newCardCount] cards waiting on the coast, and sentences the app suggests.
 * @property newCardCount Cards on the coast that were never studied, which it introduces.
 * @property isWelcomeBack Whether it was shrunk because the user was away (see [WELCOME_BACK_AFTER_DAYS]).
 * @property dueOnCoast All studied cards due on the coast, which [reviewCount] was cut from.
 * @property newOnCoast All cards on the coast that were never studied, which [newCardCount] was cut from.
 */
data class DailyPlan(
    val reviewCount: Int,
    val newCount: Int,
    val isWelcomeBack: Boolean,
    val dueOnCoast: Int,
    val newCardCount: Int = 0,
    val newOnCoast: Int = 0
) {
    val isEmpty: Boolean get() = reviewCount == 0 && newCount == 0

    /** New sentences the app suggests, on top of the cards waiting on the coast. */
    val newSentenceCount: Int get() = newCount - newCardCount

    /** New cards on the coast the session leaves for the days after. */
    val newLeftOver: Int get() = newOnCoast - newCardCount

    /** How many due cards the session leaves for another day. */
    val leftOver: Int get() = dueOnCoast - reviewCount

    /** A rough length: a card or a new sentence takes about twenty seconds. */
    val minutes: Int get() = ((reviewCount + newCount * 2) * 20 + 59) / 60
}

/** Whether the last session was long enough ago ([WELCOME_BACK_AFTER_DAYS]) that the next one should be gentle. */
fun isWelcomeBack(lastStudy: LocalDate?, today: LocalDate): Boolean =
    lastStudy != null && lastStudy <= today.minus(WELCOME_BACK_AFTER_DAYS, DateTimeUnit.DAY)

/**
 * Today's session for a coast with [dueOnCoast] studied cards due, [newOnCoast] cards never studied and
 * [readySuggestions] prepared sentences, given when the user last studied and their [pace]. The cards
 * already on the coast come before suggested sentences: what the user put there is what they asked for.
 * After days away the session is a third of the usual size (never under the welcome-back minimum).
 */
fun dailyPlan(dueOnCoast: Int, newOnCoast: Int, readySuggestions: Int, lastStudy: LocalDate?, today: LocalDate, pace: Pace = Pace.Steady): DailyPlan {
    val welcomeBack = isWelcomeBack(lastStudy, today)
    val reviewCap = if (welcomeBack) maxOf(WELCOME_BACK_REVIEW_CAP, pace.reviewCap / 3) else pace.reviewCap
    val newCap = if (welcomeBack) maxOf(WELCOME_BACK_NEW_SENTENCES, pace.newPerDay / 3) else pace.newPerDay
    val newCards = minOf(newOnCoast, newCap)
    return DailyPlan(
        reviewCount = minOf(dueOnCoast, reviewCap),
        newCount = newCards + minOf(readySuggestions, newCap - newCards),
        isWelcomeBack = welcomeBack,
        dueOnCoast = dueOnCoast,
        newCardCount = newCards,
        newOnCoast = newOnCoast
    )
}

/**
 * The never-studied cards of [cards] in the order a daily session introduces them: the islands take turns,
 * those with the fewest waiting first, so a card the user just added by hand isn't stuck behind a whole
 * island added in one go.
 */
fun newCardsInLearnOrder(cards: List<PlacedCard>): List<PlacedCard> {
    val queues = cards.filter { it.card.due == null }
        .groupBy { it.card.islandId }.values
        .sortedWith(compareBy<List<PlacedCard>> { it.size }.thenBy { queue -> queue.minOf { it.card.cardId } })
        .map { queue -> ArrayDeque(queue.sortedBy { it.card.cardId }) }
    val ordered = ArrayList<PlacedCard>()
    while (queues.any { it.isNotEmpty() }) queues.forEach { queue -> queue.removeFirstOrNull()?.let { ordered += it } }
    return ordered
}

// ---------- Asking the AI for sentences ----------

/** A sentence an AI model or the phrase bank proposed, before it is stored as a [SuggestedSentence]. */
data class ProposedSentence(
    val nativeText: String,
    val targetText: String,
    val category: String,
    val emoji: String? = null,
    /** Another way to say it: the first translation, when the natural-speech check rewrote it. */
    val alternatives: List<String> = emptyList(),
    /** Why it was rewritten, if it was. */
    val note: String? = null
)

/** Cards the user has start as "beginner" prompts; past this many the model may go a little further. */
const val BEGINNER_CARD_COUNT = 40

/**
 * The prompt that asks a model for [count] sentences worth learning on a coast, fitted to [goal].
 *
 * @param avoid Sentences (in [nativeLanguage]) the user already has or has skipped.
 * @param existingCategories Islands on the coast, to file the sentences under where one fits.
 * @param cardCount How many cards the coast has, as a rough level.
 * @param region Where the language will be used, so the sentences use that place's words and forms; null for no preference.
 */
fun buildSuggestionPrompt(
    targetLanguage: String,
    nativeLanguage: String,
    goal: String?,
    count: Int,
    avoid: List<String>,
    existingCategories: List<String>,
    cardCount: Int,
    region: String? = null,
    wording: Wording = Wording.Unspecified
): String {
    val where = region?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { " They will use it in $it: use the words, forms, spelling and register of $it." } ?: ""
    val speaker = speakerRule(wording, targetLanguage).removePrefix("- ").let { if (it.isEmpty()) "" else "\n        $it" }
    val why = goal?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { "Why they are learning it: \"$it\". Pick sentences they would want to say or understand for exactly that." }
        ?: "They haven't said why they are learning it, so pick sentences for everyday conversation."
    val level = if (cardCount < BEGINNER_CARD_COUNT) "They are a beginner: keep the sentences short and common."
    else "They know the basics (about $cardCount cards so far): the sentences can be a little more natural and varied."
    val avoidText = if (avoid.isEmpty()) "" else "\nDo not suggest any of these, which they already have: " + avoid.joinToString(" | ")
    val categoriesText = if (existingCategories.isEmpty()) "Give each sentence a short category name in English (for example Small Talk, Travel, Restaurant)."
    else "File each sentence under one of these existing categories where one fits: ${existingCategories.joinToString(", ")}. Only invent a new short English category name if none fits."

    return """
        You are helping someone learn $targetLanguage. Their native language is $nativeLanguage.$where
        $why
        $level$speaker

        Suggest $count sentences in $nativeLanguage that are genuinely useful to them, each 3 to 10 words, with its natural $targetLanguage translation. Mix questions and statements, and avoid stiff textbook phrasing. Every sentence must be different from the others.$avoidText
        $categoriesText
        Also give one emoji that pictures the category.

        Write EXACTLY one line per sentence and nothing else, in this format:
        [sentence in $nativeLanguage] || [translation in $targetLanguage] || [category] || [emoji]
    """.trimIndent()
}

/**
 * Parses a model's reply to [buildSuggestionPrompt]: one sentence per line, the parts separated by `||`.
 * Numbering, bullets, markdown and the prompt's brackets are tolerated; lines that don't have at least a
 * sentence, a translation and a category are dropped, as are repeats.
 */
fun parseSuggestionResponse(text: String): List<ProposedSentence> {
    val cleaned = text.replace("```", "").replace("**", "")
    return cleaned.lines()
        .map { it.trim().replace(Regex("^(?:[-*•]|\\d+[.)])\\s*"), "") }
        .filter { it.contains("||") }
        .mapNotNull { line ->
            val parts = line.split("||").map { unbracket(it.trim()) }
            if (parts.size < 3) return@mapNotNull null
            val native = parts[0]
            val target = parts[1]
            val category = parts[2].trim('"', '\'', '.', ' ')
            if (native.isEmpty() || target.isEmpty() || category.isEmpty()) return@mapNotNull null
            val emoji = parts.getOrNull(3)?.takeIf(::looksLikeEmoji)
            ProposedSentence(native, target, category, emoji)
        }
        .distinctBy { normalizedWords(it.nativeText) }
}

/** `[Hello]` -> `Hello`, in case the model copied the prompt's placeholder brackets. */
private fun unbracket(value: String): String =
    if (value.length >= 2 && value.startsWith("[") && value.endsWith("]")) value.substring(1, value.length - 1).trim() else value
