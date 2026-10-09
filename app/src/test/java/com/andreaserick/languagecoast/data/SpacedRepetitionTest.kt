package com.andreaserick.languagecoast.data

import com.andreaserick.languagecoast.ui.study.intervalLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp

class SpacedRepetitionTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-10-09T10:00:00Z")
    private val newCard = Flashcard(cardId = 1, islandId = 1, nativeText = "Hello", targetText = "Hallo")

    private fun reviewed(stability: Double, difficulty: Double, daysAgo: Long) = newCard.copy(
        stability = stability,
        difficulty = difficulty,
        lastReviewed = now.minus(Duration.ofDays(daysAgo)).toEpochMilli(),
        due = now.toEpochMilli()
    )

    private fun startOfDay(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun newCardStartsWithTheDefaultStabilityAndDifficultyOfItsGrade() {
        val outcomes = reviewOutcomes(newCard, now, zone)

        assertEquals(listOf(0.40255, 1.18385, 3.173, 15.69105), Grade.entries.map { outcomes.getValue(it).card.stability })
        // D0(G) = w4 - e^(w5 * (G - 1)) + 1
        assertEquals(7.1949 - exp(0.5345 * 2) + 1, outcomes.getValue(Grade.Good).card.difficulty!!, 1e-9)
        assertEquals(mapOf(Grade.Again to 1, Grade.Hard to 1, Grade.Good to 3, Grade.Easy to 16), outcomes.mapValues { it.value.intervalDays })
    }

    @Test
    fun cardIsDueAtTheStartOfItsDayInTheUsersTimeZone() {
        val outcome = reviewOutcomes(newCard, now, zone).getValue(Grade.Good)

        assertEquals(startOfDay("2026-10-12"), outcome.card.due)
        assertEquals(now.toEpochMilli(), outcome.card.lastReviewed)
    }

    @Test
    fun recallIsNinetyPercentAfterAsManyDaysAsTheStability() {
        assertEquals(0.9, Fsrs.retrievability(elapsedDays = 10.0, stability = 10.0), 1e-9)
        assertTrue(Fsrs.retrievability(elapsedDays = 20.0, stability = 10.0) < 0.9)
    }

    @Test
    fun rememberingOnTimeGrowsStabilityAndForgettingShrinksIt() {
        val card = reviewed(stability = 10.0, difficulty = 5.0, daysAgo = 10)
        val outcomes = reviewOutcomes(card, now, zone)

        assertTrue(outcomes.getValue(Grade.Again).card.stability!! < 10.0)
        assertTrue(outcomes.getValue(Grade.Good).card.stability!! > 10.0)
        // S'f = w11 * D^-w12 * ((S + 1)^w13 - 1) * e^(w14 * (1 - R)) ≈ 2.1 days: a well-known card is relearned faster.
        assertEquals(2, outcomes.getValue(Grade.Again).intervalDays)
        val intervals = Grade.entries.map { outcomes.getValue(it).intervalDays }
        assertEquals(intervals.sorted(), intervals)
        assertTrue(outcomes.getValue(Grade.Easy).intervalDays > outcomes.getValue(Grade.Good).intervalDays)
    }

    @Test
    fun harderGradesRaiseDifficultyAndEasierOnesLowerIt() {
        val card = reviewed(stability = 10.0, difficulty = 5.0, daysAgo = 10)
        val outcomes = reviewOutcomes(card, now, zone)

        assertTrue(outcomes.getValue(Grade.Again).card.difficulty!! > 5.0)
        assertTrue(outcomes.getValue(Grade.Easy).card.difficulty!! < 5.0)
    }

    @Test
    fun difficultyStaysBetweenOneAndTen() {
        var card = newCard
        repeat(30) { day ->
            card = reviewOutcomes(card, now.plus(Duration.ofDays(day.toLong())), zone).getValue(Grade.Again).card
        }
        assertTrue(card.difficulty!! in 1.0..10.0)
        assertTrue(card.stability!! > 0.0)
    }

    @Test
    fun secondReviewOnTheSameDayUsesTheShortTermFormula() {
        val card = reviewOutcomes(newCard, now, zone).getValue(Grade.Again).card
        val later = now.plus(Duration.ofHours(1))

        val good = reviewOutcomes(card, later, zone).getValue(Grade.Good).card

        // S' = S * e^(w17 * (G - 3 + w18))
        assertEquals(0.40255 * exp(0.51655 * 0.6621), good.stability!!, 1e-9)
    }

    @Test
    fun newAndOverdueCardsAreDueButFutureOnesAreNot() {
        assertTrue(isDue(newCard, now.toEpochMilli()))
        assertTrue(isDue(newCard.copy(due = now.toEpochMilli()), now.toEpochMilli()))
        assertFalse(isDue(newCard.copy(due = now.toEpochMilli() + 1), now.toEpochMilli()))
    }

    @Test
    fun intervalLabelsAreShort() {
        assertEquals("1d", intervalLabel(1))
        assertEquals("29d", intervalLabel(29))
        assertEquals("2mo", intervalLabel(61))
        assertEquals("1y", intervalLabel(365))
        assertEquals("1.5y", intervalLabel(548))
    }
}
