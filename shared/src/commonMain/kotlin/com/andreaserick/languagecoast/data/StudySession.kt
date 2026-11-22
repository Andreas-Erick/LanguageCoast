package com.andreaserick.languagecoast.data

import kotlinx.datetime.LocalDate
import kotlin.math.min

/*
 * What a study session holds: which cards it opens with, in which order, and for a daily session which new
 * cards and sentences follow. Every place that shows or starts "today's work" asks this file, so the rule for
 * what is due and how much a day takes on is written once: the study screen, the Today card (through
 * [dailyPlan]), the widget and the reminder ([DueCounts.forToday]).
 */

/** What a study session covers. The keys are what a study route carries. */
enum class StudyScope(val key: String) {
    Island("island"),
    Coast("coast"),
    /** Today's due cards on every coast, with the coasts taking turns. */
    Today("today"),
    /** The cards the user keeps forgetting (see [isLeaky]), due or not. */
    Leaky("leaky"),
    /** One coast's daily session: a few due cards, then a few new cards and sentences (see [dailyPlan]). */
    Daily("daily");

    companion object {
        fun fromKey(key: String?): StudyScope = entries.firstOrNull { it.key == key } ?: Island
    }
}

/**
 * What a daily session is sized by besides its cards.
 *
 * @property suggestions Sentences the app prepared for the coast, in the order they are offered.
 * @property lastStudy The day of the last finished session, for the welcome-back size.
 * @property bonusNew Above 0 for a bonus round: no reviews, and up to this many new items (see [dailyPlan]).
 */
data class DailyContext(val suggestions: List<SuggestedSentence>, val lastStudy: LocalDate?, val pace: Pace, val bonusNew: Int = 0)

/**
 * How a session opens.
 *
 * @property opening The cards it reviews, in order.
 * @property intro Cards never studied that a daily session introduces after the reviews.
 * @property learn Prepared sentences a daily session offers last.
 * @property plan The daily plan the session was cut to; null for the other scopes.
 */
data class SessionStart(
    val opening: List<PlacedCard>,
    val intro: List<PlacedCard> = emptyList(),
    val learn: List<SuggestedSentence> = emptyList(),
    val plan: DailyPlan? = null
)

/**
 * The session [scope] opens with over [cards] at [now] (epoch millis) on [today]:
 * - an island reviews everything due, the cards never studied included, since opening an island is how it is learned;
 * - a coast reviews its studied cards that are due, most overdue first;
 * - every coast does the same with the coasts taking turns;
 * - leaky words are the most forgotten cards, due or not;
 * - a daily session ([daily] is required) reviews due cards up to its plan, then introduces new cards in
 *   learn order (islands taking turns), then offers prepared sentences.
 */
fun startSession(scope: StudyScope, cards: List<PlacedCard>, now: Long, today: LocalDate, daily: DailyContext? = null): SessionStart {
    val studied = cards.filter { it.card.due != null }
    return when (scope) {
        StudyScope.Island -> SessionStart(dueInStudyOrder(cards, now))
        StudyScope.Coast -> SessionStart(dueInStudyOrder(studied, now))
        StudyScope.Today -> SessionStart(interleaveByCoast(dueInStudyOrder(studied, now)))
        StudyScope.Leaky -> SessionStart(leakyInStudyOrder(cards))
        StudyScope.Daily -> {
            val context = requireNotNull(daily) { "A daily session needs its DailyContext" }
            val due = dueInStudyOrder(studied, now)
            val waiting = newCardsInLearnOrder(cards)
            val plan = dailyPlan(due.size, waiting.size, context.suggestions.size, context.lastStudy, today, context.pace, context.bonusNew)
            SessionStart(
                opening = due.take(plan.reviewCount),
                intro = waiting.take(plan.newCardCount),
                learn = context.suggestions.take(plan.newSentenceCount),
                plan = plan
            )
        }
    }
}

/**
 * Every card of a session's scope in the order "practise all" goes through them, due or not: for leaky words the
 * leaky ones (or all of [cards] if none is leaky any more), otherwise all of them with the coasts taking turns.
 */
fun practiceAllOrder(scope: StudyScope, cards: List<PlacedCard>): List<PlacedCard> =
    if (scope == StudyScope.Leaky) leakyInStudyOrder(cards).ifEmpty { cards } else interleaveByCoast(cards)

/** The due cards of [cards] at [now]: reviewed cards first, most overdue first, then new cards in the order they were added. */
private fun dueInStudyOrder(cards: List<PlacedCard>, now: Long): List<PlacedCard> {
    val (new, reviewed) = cards.filter { isDue(it.card, now) }.partition { it.card.due == null }
    return reviewed.sortedBy { it.card.due } + new.sortedBy { it.card.cardId }
}

/** The leaky cards of [cards] (see [isLeaky]), the most forgotten first, with the coasts taking turns. */
private fun leakyInStudyOrder(cards: List<PlacedCard>): List<PlacedCard> =
    interleaveByCoast(
        cards.filter { isLeaky(it.card) }
            .sortedWith(compareByDescending<PlacedCard> { it.card.lapses }.thenByDescending { it.card.difficulty ?: 0.0 })
    )

/**
 * Cards across every coast at some moment.
 *
 * @property reviews Studied cards that are due.
 * @property newCards Cards never studied.
 */
data class DueCounts(val reviews: Int, val newCards: Int) {
    /** What today holds at [pace]: every due review, and new cards only as far as a day takes them on. */
    fun forToday(pace: Pace): Int = reviews + min(newCards, pace.newPerDay)
}
