package com.andreaserick.languagecoast.notifications

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReminderMessagesTest {

    private val monday = LocalDate(2026, 11, 2)
    private val teaser = ReminderCard("Where is the train station?", "German", "Travel", "✈️")

    private fun week(dueCount: Int = 12, streak: Int = 5, studiedToday: Boolean = false, teaser: ReminderCard? = this.teaser) =
        (0 until 7).map { reminderMessage(dueCount, streak, studiedToday, teaser, monday.plus(it, DateTimeUnit.DAY)) }

    @Test
    fun nothingDueIsCaughtUpWhateverTheStreak() {
        week(dueCount = 0, streak = 40).forEach { assertEquals(ReminderKind.CaughtUp, it.kind) }
        assertEquals("🏝️", week(dueCount = 0).first().emoji)
    }

    @Test
    fun twoDaysInARowNeverReadTheSame() {
        val messages = week()
        messages.zipWithNext().forEach { (a, b) -> assertNotEquals(a.title to a.text, b.title to b.text) }
    }

    @Test
    fun aWeekRotatesThroughEveryApplicableDesign() {
        assertEquals(setOf(ReminderKind.Due, ReminderKind.Teaser, ReminderKind.StreakAtRisk), week().map { it.kind }.toSet())
        // No streak worth saving and nothing to quote: only the everyday reminder is left.
        assertEquals(setOf(ReminderKind.Due), week(streak = 0, teaser = null).map { it.kind }.toSet())
    }

    @Test
    fun teaserQuotesTheCardAndWhereItLives() {
        val message = week().first { it.kind == ReminderKind.Teaser }
        assertEquals("“Where is the train station?” in German · ✈️ Travel", message.text)
        assertEquals("✈️", message.emoji)
    }

    @Test
    fun streakAtRiskOnlyBeforeStudyingToday() {
        assertTrue(week(studiedToday = true).none { it.kind == ReminderKind.StreakAtRisk })
        val atRisk = week().first { it.kind == ReminderKind.StreakAtRisk }
        assertTrue("5" in atRisk.title || "chain" in atRisk.title, atRisk.title)
        assertTrue("12 cards" in atRisk.text)
    }

    @Test
    fun milestoneWinsWhenTodayWouldReachOne() {
        val message = reminderMessage(dueCount = 3, streak = 6, studiedToday = false, teaser = teaser, day = monday)
        assertEquals(ReminderKind.Milestone, message.kind)
        assertEquals("Day 7 is today", message.title)
        assertTrue("3 cards" in message.text)
        // Already studied: the milestone is reached, so it's an ordinary day again.
        assertNotEquals(ReminderKind.Milestone, reminderMessage(3, 6, true, teaser, monday).kind)
    }

    @Test
    fun singleCardIsNotPlural() {
        assertTrue(week(dueCount = 1, streak = 0, teaser = null).all { "1 card " in it.text || "1 card" in it.title })
    }

    @Test
    fun scheduledWeekHasSevenDifferentReminders() {
        val titles = (0 until 7).map { scheduledReminderMessage(it).title }
        assertEquals(7, titles.toSet().size)
        assertEquals(titles[0], scheduledReminderMessage(7).title)
    }
}
