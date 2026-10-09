package com.andreaserick.languagecoast.data

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

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
fun reviewOutcomes(card: Flashcard, now: Instant, zone: ZoneId): Map<Grade, ReviewOutcome> {
    val today = now.atZone(zone).toLocalDate()
    val previous = card.lastReviewed?.let { Instant.ofEpochMilli(it) }
    val memories = Grade.entries.associateWith { grade ->
        when {
            card.stability == null || card.difficulty == null || previous == null -> Fsrs.initial(grade)
            previous.atZone(zone).toLocalDate() == today -> Fsrs.sameDay(card.stability, card.difficulty, grade)
            else -> {
                val elapsedDays = max(0.0, (now.toEpochMilli() - previous.toEpochMilli()) / MILLIS_PER_DAY)
                Fsrs.review(card.stability, card.difficulty, elapsedDays, grade)
            }
        }
    }

    // Keep the intervals in grade order, as Anki does: Hard ≤ Good < Easy.
    val raw = memories.mapValues { (_, memory) -> intervalFor(memory.stability) }
    val hard = min(raw.getValue(Grade.Hard), raw.getValue(Grade.Good))
    val good = max(raw.getValue(Grade.Good), hard + 1)
    val easy = max(raw.getValue(Grade.Easy), good + 1)
    val intervals = mapOf(Grade.Again to raw.getValue(Grade.Again), Grade.Hard to hard, Grade.Good to good, Grade.Easy to easy)

    return memories.mapValues { (grade, memory) ->
        val interval = intervals.getValue(grade)
        val due = today.plusDays(interval.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        ReviewOutcome(
            card = card.copy(
                stability = memory.stability,
                difficulty = memory.difficulty,
                lastReviewed = now.toEpochMilli(),
                due = due
            ),
            intervalDays = interval
        )
    }
}

/** At 90% desired retention the FSRS interval equals the stability, so only rounding and limits remain. */
private fun intervalFor(stability: Double): Int = stability.roundToInt().coerceIn(1, MAX_INTERVAL_DAYS)

private const val MAX_INTERVAL_DAYS = 36_500
private val MILLIS_PER_DAY = ChronoUnit.DAYS.duration.toMillis().toDouble()

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
        val r = retrievability(elapsedDays, stability)
        val newStability = if (grade == Grade.Again) {
            min(stability, forgetStability(stability, difficulty, r))
        } else {
            recallStability(stability, difficulty, r, grade)
        }
        return Memory(max(MIN_STABILITY, newStability), nextDifficulty(difficulty, grade))
    }

    /** Memory after another review on the same day, which the long-term formulas do not model. */
    fun sameDay(stability: Double, difficulty: Double, grade: Grade): Memory = Memory(
        max(MIN_STABILITY, stability * exp(w[17] * (grade.value - 3 + w[18]))),
        nextDifficulty(difficulty, grade)
    )

    /** Probability of recalling a card [elapsedDays] after its last review. */
    fun retrievability(elapsedDays: Double, stability: Double): Double =
        (1 + FACTOR * elapsedDays / stability).pow(DECAY)

    private fun initialDifficulty(grade: Int): Double = w[4] - exp(w[5] * (grade - 1)) + 1

    private fun nextDifficulty(difficulty: Double, grade: Grade): Double {
        val delta = -w[6] * (grade.value - 3)
        val damped = difficulty + delta * (10 - difficulty) / 9
        // Mean reversion towards the initial difficulty of an "Easy" card keeps difficulty from drifting forever.
        return (w[7] * initialDifficulty(4) + (1 - w[7]) * damped).clampDifficulty()
    }

    private fun recallStability(s: Double, d: Double, r: Double, grade: Grade): Double {
        val hardPenalty = if (grade == Grade.Hard) w[15] else 1.0
        val easyBonus = if (grade == Grade.Easy) w[16] else 1.0
        return s * (exp(w[8]) * (11 - d) * s.pow(-w[9]) * (exp(w[10] * (1 - r)) - 1) * hardPenalty * easyBonus + 1)
    }

    private fun forgetStability(s: Double, d: Double, r: Double): Double =
        w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp(w[14] * (1 - r))

    private fun Double.clampDifficulty() = coerceIn(1.0, 10.0)
}
