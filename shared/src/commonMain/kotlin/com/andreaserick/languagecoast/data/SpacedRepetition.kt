package com.andreaserick.languagecoast.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** How well the user remembered a card. */
enum class Grade(val value: Int) { Again(1), Hard(2), Good(3), Easy(4) }

/**
 * A card after one grade: its updated scheduling fields, and the interval until it is due again.
 *
 * @property intervalDays Days from today until [card] is due again (at least 1).
 */
data class ReviewOutcome(val card: Flashcard, val intervalDays: Int)

/** Whether [card] should be studied at [now]: new cards always are, reviewed ones once their due day has started. */
fun isDue(card: Flashcard, now: Long): Boolean = card.due == null || card.due <= now

/**
 * The outcome of every grade for [card] if it is reviewed at [now], using FSRS-5
 * (https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm) with its default parameters.
 * Cards are scheduled to the start of a day in [zone], so they become due when that day begins.
 */
fun reviewOutcomes(card: Flashcard, now: Instant, zone: TimeZone): Map<Grade, ReviewOutcome> {
    val today = now.toLocalDateTime(zone).date
    val previous = card.lastReviewed?.let { Instant.fromEpochMilliseconds(it) }
    val stability = card.stability?.takeIf { it.isFinite() && it > 0.0 }
    val difficulty = card.difficulty?.takeIf { it.isFinite() }?.coerceIn(1.0, 10.0)
    val memories = Grade.entries.associateWith { grade ->
        when {
            stability == null || difficulty == null || previous == null -> Fsrs.initial(grade)
            previous.toLocalDateTime(zone).date == today -> Fsrs.sameDay(stability, difficulty, grade)
            else -> {
                val elapsedDays = max(0.0, (now - previous) / 1.days)
                Fsrs.review(stability, difficulty, elapsedDays, grade)
            }
        }
    }

    // Keep the intervals in grade order, as Anki does: Hard ≤ Good ≤ Easy, never exceeding MAX_INTERVAL_DAYS.
    val raw = memories.mapValues { (_, memory) -> intervalFor(memory.stability) }
    val hard = min(raw.getValue(Grade.Hard), raw.getValue(Grade.Good)).coerceIn(1, MAX_INTERVAL_DAYS)
    val good = max(raw.getValue(Grade.Good), hard + 1).coerceIn(1, MAX_INTERVAL_DAYS)
    val easy = max(raw.getValue(Grade.Easy), good + 1).coerceIn(1, MAX_INTERVAL_DAYS)
    val intervals = mapOf(Grade.Again to raw.getValue(Grade.Again), Grade.Hard to hard, Grade.Good to good, Grade.Easy to easy)

    return memories.mapValues { (grade, memory) ->
        val interval = intervals.getValue(grade)
        val due = today.plus(interval, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
        ReviewOutcome(
            card = card.copy(
                stability = memory.stability,
                difficulty = memory.difficulty,
                lastReviewed = now.toEpochMilliseconds(),
                due = due,
                lapses = if (grade == Grade.Again) card.lapses + 1 else card.lapses
            ),
            intervalDays = interval
        )
    }
}

/** At 90% desired retention the FSRS interval equals the stability, so only rounding and limits remain. */
private fun intervalFor(stability: Double): Int {
    if (!stability.isFinite() || stability <= 0.0) return 1
    return stability.coerceAtMost(MAX_INTERVAL_DAYS.toDouble()).roundToInt().coerceIn(1, MAX_INTERVAL_DAYS)
}

private const val MAX_INTERVAL_DAYS = 36_500

/** Stability (days until recall drops to 90%) and difficulty (1–10) of a card's memory. */
internal data class Memory(val stability: Double, val difficulty: Double)

/** The FSRS-5 formulas with the default parameters. */
internal object Fsrs {
    private val w = doubleArrayOf(
        0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
        1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621
    )
    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0
    private const val MIN_STABILITY = 0.01

    /** Memory after the first review of a new card. */
    fun initial(grade: Grade) = Memory(w[grade.value - 1], initialDifficulty(grade.value).clampDifficulty())

    /** Memory after a review [elapsedDays] after the previous one. */
    fun review(stability: Double, difficulty: Double, elapsedDays: Double, grade: Grade): Memory {
        val safeStability = if (stability.isFinite() && stability > 0.0) stability.coerceAtLeast(MIN_STABILITY) else MIN_STABILITY
        val safeDifficulty = if (difficulty.isFinite()) difficulty.clampDifficulty() else initialDifficulty(grade.value).clampDifficulty()
        val safeElapsed = if (elapsedDays.isFinite() && elapsedDays >= 0.0) elapsedDays else 0.0
        val r = retrievability(safeElapsed, safeStability)
        val newStability = if (grade == Grade.Again) {
            min(safeStability, forgetStability(safeStability, safeDifficulty, r))
        } else {
            recallStability(safeStability, safeDifficulty, r, grade)
        }
        val clampedStability = if (newStability.isFinite()) newStability.coerceIn(MIN_STABILITY, MAX_STABILITY) else MIN_STABILITY
        return Memory(clampedStability, nextDifficulty(safeDifficulty, grade))
    }

    /** Memory after another review on the same day, which the long-term formulas do not model. */
    fun sameDay(stability: Double, difficulty: Double, grade: Grade): Memory {
        val safeStability = if (stability.isFinite() && stability > 0.0) stability.coerceAtLeast(MIN_STABILITY) else MIN_STABILITY
        val safeDifficulty = if (difficulty.isFinite()) difficulty.clampDifficulty() else initialDifficulty(grade.value).clampDifficulty()
        val updated = safeStability * exp(w[17] * (grade.value - 3 + w[18]))
        val clamped = if (updated.isFinite()) updated.coerceIn(MIN_STABILITY, MAX_STABILITY) else MIN_STABILITY
        return Memory(clamped, nextDifficulty(safeDifficulty, grade))
    }

    /** Probability of recalling a card [elapsedDays] after its last review. */
    fun retrievability(elapsedDays: Double, stability: Double): Double {
        if (!elapsedDays.isFinite() || !stability.isFinite() || stability <= 0.0) return 0.0
        return (1 + FACTOR * max(0.0, elapsedDays) / stability).pow(DECAY).coerceIn(0.0, 1.0)
    }

    private const val MAX_STABILITY = 36_500.0

    /** Difficulty of a new card after its first grade ([grade] from 1 to 4). */
    private fun initialDifficulty(grade: Int): Double = w[4] - exp(w[5] * (grade - 1)) + 1

    /** Difficulty after a review graded [grade]: lower grades make the card harder, changing less as it nears 10. */
    private fun nextDifficulty(difficulty: Double, grade: Grade): Double {
        val delta = -w[6] * (grade.value - 3)
        val damped = difficulty + delta * (10 - difficulty) / 9
        // Mean reversion towards the initial difficulty of an "Easy" card keeps difficulty from drifting forever.
        return (w[7] * initialDifficulty(4) + (1 - w[7]) * damped).clampDifficulty()
    }

    /** Stability after a successful review ([grade] Hard or better), given stability [s], difficulty [d] and recall probability [r]. */
    private fun recallStability(s: Double, d: Double, r: Double, grade: Grade): Double {
        val hardPenalty = if (grade == Grade.Hard) w[15] else 1.0
        val easyBonus = if (grade == Grade.Easy) w[16] else 1.0
        return s * (exp(w[8]) * (11 - d) * s.pow(-w[9]) * (exp(w[10] * (1 - r)) - 1) * hardPenalty * easyBonus + 1)
    }

    /** Stability after a lapse (graded Again), given stability [s], difficulty [d] and recall probability [r]. */
    private fun forgetStability(s: Double, d: Double, r: Double): Double =
        w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp(w[14] * (1 - r))

    /** Keeps a difficulty within FSRS's range of 1 to 10. */
    private fun Double.clampDifficulty() = coerceIn(1.0, 10.0)
}
