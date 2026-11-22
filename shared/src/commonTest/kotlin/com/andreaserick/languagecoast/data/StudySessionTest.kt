package com.andreaserick.languagecoast.data

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudySessionTest {

    private val today = LocalDate(2026, 10, 9)
    private val now = 1_000L

    private fun card(id: Int, island: Int = 1, coast: Int = 1, due: Long? = null, lapses: Int = 0, difficulty: Double? = null) =
        PlacedCard(
            Flashcard(cardId = id, islandId = island, nativeText = "n$id", targetText = "t$id", due = due, lapses = lapses, difficulty = difficulty),
            "Island $island", null, coast, "German"
        )

    private fun suggestion(id: Int) = SuggestedSentence(suggestionId = id, coastId = 1, nativeText = "s$id", targetText = "z$id", category = "Talk")

    private fun ids(cards: List<PlacedCard>) = cards.map { it.card.cardId }

    @Test
    fun anIslandReviewsDueCardsMostOverdueFirstThenItsNewCards() {
        val cards = listOf(card(1), card(2, due = 900), card(3, due = 500), card(4, due = 5_000), card(5))

        val start = startSession(StudyScope.Island, cards, now, today)

        assertEquals(listOf(3, 2, 1, 5), ids(start.opening))
        assertNull(start.plan)
    }

    @Test
    fun aCoastReviewsOnlyStudiedCards() {
        val cards = listOf(card(1), card(2, due = 900), card(3, due = 5_000))

        assertEquals(listOf(2), ids(startSession(StudyScope.Coast, cards, now, today).opening))
    }

    @Test
    fun todayLetsTheCoastsTakeTurns() {
        val cards = listOf(card(1, coast = 1, due = 100), card(2, coast = 1, due = 200), card(3, coast = 2, due = 300), card(4, coast = 1, due = 400))

        assertEquals(listOf(1, 3, 2, 4), ids(startSession(StudyScope.Today, cards, now, today).opening))
    }

    @Test
    fun leakyWordsAreTheMostForgottenFirstDueOrNot() {
        val cards = listOf(card(1, due = 9_999, lapses = 2), card(2, lapses = 5), card(3, due = 100), card(4, difficulty = 8.0))

        assertEquals(listOf(2, 1, 4), ids(startSession(StudyScope.Leaky, cards, now, today).opening))
    }

    @Test
    fun aDailySessionIsCutToItsPlanReviewsThenNewCardsThenSentences() {
        val due = (1..20).map { card(it, due = it.toLong()) }
        val waiting = listOf(card(30, island = 2), card(31, island = 3), card(32, island = 3))
        val context = DailyContext((1..8).map(::suggestion), lastStudy = today, pace = Pace.Steady)

        val start = startSession(StudyScope.Daily, due + waiting, now, today, context)

        assertEquals((1..DAILY_REVIEW_CAP).toList(), ids(start.opening))
        // Steady brings three new things: the coast's own cards come before suggestions, islands taking turns.
        assertEquals(listOf(30, 31, 32), ids(start.intro))
        assertTrue(start.learn.isEmpty())
        assertEquals(DAILY_REVIEW_CAP, start.plan?.reviewCount)
    }

    @Test
    fun aDailySessionFillsUpWithSentencesAndFollowsThePace() {
        val context = DailyContext((1..30).map(::suggestion), lastStudy = today, pace = Pace.Fast)

        val start = startSession(StudyScope.Daily, listOf(card(1)), now, today, context)

        assertEquals(listOf(1), ids(start.intro))
        assertEquals(Pace.Fast.newPerDay - 1, start.learn.size)
    }

    @Test
    fun aDailySessionNeedsItsContext() {
        assertFailsWith<IllegalArgumentException> { startSession(StudyScope.Daily, emptyList(), now, today) }
    }

    @Test
    fun practiceAllTakesEveryCardAndFallsBackWhenNothingIsLeaky() {
        val cards = listOf(card(1, coast = 1), card(2, coast = 1), card(3, coast = 2))

        assertEquals(listOf(1, 3, 2), ids(practiceAllOrder(StudyScope.Coast, cards)))
        assertEquals(listOf(1, 2, 3), ids(practiceAllOrder(StudyScope.Leaky, cards)))
    }

    @Test
    fun todaysCountTakesNewCardsOnlyAsFarAsThePace() {
        val counts = DueCounts(reviews = 7, newCards = 50)

        assertEquals(7 + Pace.Steady.newPerDay, counts.forToday(Pace.Steady))
        assertEquals(7 + Pace.Sprint.newPerDay, counts.forToday(Pace.Sprint))
        assertEquals(9, DueCounts(reviews = 7, newCards = 2).forToday(Pace.Sprint))
    }
}
