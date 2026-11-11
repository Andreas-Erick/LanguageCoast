package com.andreaserick.languagecoast.data

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorksheetFocusTest {

    private val day = 86_400_000L
    private val now = 1_000 * day

    /** A card studied [daysAgo] days ago that FSRS expects to last [stability] days. */
    private fun studied(daysAgo: Int, stability: Double = 10.0, lapses: Int = 0, difficulty: Double = 5.0) = WorksheetSourceCard(
        islandId = 1, islandName = "Food", targetText = "Brot", nativeText = "bread",
        stability = stability, difficulty = difficulty, lastReviewed = now - daysAgo * day, lapses = lapses
    )

    private val neverStudied = WorksheetSourceCard(islandId = 1, islandName = "Food", targetText = "Brot", nativeText = "bread")

    private fun assertNear(expected: Double, actual: Double) = assertTrue(abs(expected - actual) < 0.005, "expected $expected, was $actual")

    @Test
    fun aCardNeedsPracticeAsItSlipsAndWhenItKeepsBeingForgotten() {
        assertFalse(isStudied(neverStudied))
        assertTrue(isStudied(studied(daysAgo = 0)))
        assertEquals(0.0, practiceNeed(neverStudied, now))
        // Fresh in memory: nothing. On the day it comes due (recall at 90%): a third of the slipping share.
        assertNear(0.0, practiceNeed(studied(daysAgo = 0), now))
        assertNear(2.0 / 9, practiceNeed(studied(daysAgo = 10), now))
        // Long overdue: all of the slipping share, however long.
        assertNear(2.0 / 3, practiceNeed(studied(daysAgo = 100), now))
        assertNear(2.0 / 3, practiceNeed(studied(daysAgo = 900), now))
        // A card that keeps being forgotten needs practice even right after a review.
        assertNear(1.0 / 6, practiceNeed(studied(daysAgo = 0, lapses = 2), now))
        assertNear(1.0 / 3, practiceNeed(studied(daysAgo = 0, lapses = 9), now))
        assertNear(1.0 / 6, practiceNeed(studied(daysAgo = 0, difficulty = 7.75), now))
        // Lapses and difficulty say the same thing twice; the one that says more counts.
        assertNear(1.0 / 3, practiceNeed(studied(daysAgo = 0, lapses = 4, difficulty = 7.75), now))
        assertNear(1.0, practiceNeed(studied(daysAgo = 100, lapses = 4), now))
        // A review dated in the future (a clock that was set back) is a fresh one.
        assertNear(0.0, practiceNeed(studied(daysAgo = -5), now))
    }

    @Test
    fun anIslandsFocusGrowsWithItsCardsNeedButLessThanInStep() {
        val known = islandFocus(cardCount = 30, need = 0.0)
        val slipping = islandFocus(cardCount = 30, need = 21.0)
        assertTrue(slipping > 2.5 * known)
        // Four times the need is about twice the focus, so a sheet stays mixed.
        assertTrue(islandFocus(40, 400.0) / islandFocus(40, 100.0) in 1.9..2.0)
        // Without need an island counts by what there is to build from, up to what a prompt shows of it:
        // a deck of 40 000 cards nobody has studied is no more than an island of 40.
        assertEquals(islandFocus(40, 0.0), islandFocus(40_000, 0.0))
        assertTrue(islandFocus(3, 0.0) < islandFocus(30, 0.0))
        assertTrue(islandFocus(40_000, 0.0) < slipping)
        // A few cards that slip count for more than many that don't.
        assertTrue(islandFocus(5, 3.5) > known)
    }

    @Test
    fun sentencesAreDealtInProportionAndEvenly() {
        val planner = FocusPlanner(doubleArrayOf(3.0, 1.0, 1.0), Random(1))
        val total = IntArray(3)
        repeat(50) {
            val quotas = planner.plan(sentences = 10, maxIslands = 3)
            assertEquals(10, quotas.sum())
            // Every ten sentences have the mix of the whole sheet, to within a sentence.
            assertTrue(quotas[0] in 5..7 && quotas[1] in 1..3 && quotas[2] in 1..3, quotas.toList().toString())
            quotas.forEachIndexed { island, count -> total[island] += count }
        }
        assertTrue(abs(total[0] - 300) <= 1 && abs(total[1] - 100) <= 1 && abs(total[2] - 100) <= 1, total.toList().toString())
    }

    @Test
    fun anIslandWithASmallShareIsOnSomeShortSheetsRatherThanOnNone() {
        // A tenth of the sentences on a sheet of five: on about half of the sheets.
        val sheetsWithIt = (1..200).count { seed -> FocusPlanner(doubleArrayOf(9.0, 1.0), Random(seed)).plan(5, 2)[1] > 0 }
        assertTrue(sheetsWithIt in 70..130, "$sheetsWithIt")
    }

    @Test
    fun aRequestWithRoomForFewIslandsLeavesTheOthersFirstInLineForTheNext() {
        val planner = FocusPlanner(DoubleArray(120) { 1.0 }, Random(1))
        val seen = HashSet<Int>()
        repeat(12) {
            val quotas = planner.plan(sentences = 25, maxIslands = 10)
            assertEquals(25, quotas.sum())
            val chosen = quotas.indices.filter { quotas[it] > 0 }
            assertEquals(10, chosen.size)
            // Nobody comes up twice before everybody has come up once.
            assertTrue(chosen.none { it in seen })
            seen += chosen
        }
        assertEquals(120, seen.size)
    }

    @Test
    fun weightsThatAreNoWeightsStillGiveAPlan() {
        val quotas = FocusPlanner(doubleArrayOf(0.0, Double.NaN, 1.0, -3.0), Random(1)).plan(sentences = 20, maxIslands = 4)
        assertEquals(20, quotas.sum())
        assertTrue(quotas[2] >= 19)
        assertEquals(0, FocusPlanner(DoubleArray(0), Random(1)).plan(5, 3).size)
        assertEquals(listOf(5), FocusPlanner(doubleArrayOf(2.0), Random(1)).plan(5, 0).toList())
    }

    @Test
    fun thePromptSaysHowManySentencesEachIslandGets() {
        val cards = listOf(
            WorksheetCard("Travel", "Wo ist der Bahnhof?", "Where is the station?"),
            WorksheetCard("Food", "Das Brot ist gut.", "The bread is good."),
            WorksheetCard("Home", "Das Haus ist alt.", "The house is old.")
        )
        fun prompt(vararg quotas: IslandQuota) = buildWorksheetPrompt(cards, "German", "English", count = 5, difficulty = WorksheetDifficulty.Medium, quotas = quotas.toList())

        val focused = prompt(IslandQuota("Travel", 1), IslandQuota("Food", 4))
        assertTrue("just to make a sentence new. Of the sentences, write 1 about Travel and 4 about Food. The other topics get no sentence of their own this time." in focused)
        assertFalse("Spread the sentences" in focused)
        // Every island has a share: there are no other topics to speak of.
        val all = prompt(IslandQuota("Travel", 1), IslandQuota("Food", 3), IslandQuota("Home", 1))
        assertTrue(all.lines().single { it.startsWith("2. ") }.endsWith("Of the sentences, write 1 about Travel, 3 about Food and 1 about Home."))
        assertTrue("Of the sentences, write 5 about Food. The other topics" in prompt(IslandQuota("Food", 5)))
        assertTrue("Spread the sentences over the topics." in prompt())
        assertTrue(focused.lines().none { it.startsWith(" ") })
    }
}
