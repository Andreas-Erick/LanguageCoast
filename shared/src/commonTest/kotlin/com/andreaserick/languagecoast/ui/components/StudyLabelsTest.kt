package com.andreaserick.languagecoast.ui.components

import com.andreaserick.languagecoast.data.Flashcard
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.DateTimeUnit
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.time.Instant

class StudyLabelsTest {

    private val now = Instant.parse("2026-10-09T12:00:00Z").toEpochMilliseconds()

    private fun label(due: String?): String {
        val card = Flashcard(islandId = 1, nativeText = "n", targetText = "t", due = due?.let { Instant.parse(it).toEpochMilliseconds() })
        return dueLabel(card, now, TimeZone.UTC)
    }

    @Test
    fun newAndOverdueCards() {
        assertEquals("New", label(null))
        assertEquals("Due now", label("2026-10-09T00:00:00Z"))
        assertEquals("Due now", label("2026-09-01T00:00:00Z"))
    }

    @Test
    fun upcomingCardsCountCalendarDays() {
        assertEquals("Due later today", label("2026-10-09T18:00:00Z"))
        assertEquals("Due tomorrow", label("2026-10-10T00:00:00Z"))
        assertEquals("Due in 5 days", label("2026-10-14T00:00:00Z"))
    }

    @Test
    fun lastStudiedLabels() {
        val today = LocalDate(2026, 10, 9)
        fun at(date: LocalDate) = date.atTime(15, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()

        assertEquals("Not studied yet", lastStudiedLabel(null, today, TimeZone.UTC))
        assertEquals("Studied today", lastStudiedLabel(at(today), today, TimeZone.UTC))
        assertEquals("Studied yesterday", lastStudiedLabel(at(today.minus(1, DateTimeUnit.DAY)), today, TimeZone.UTC))
        assertEquals("Last studied 5 days ago", lastStudiedLabel(at(today.minus(5, DateTimeUnit.DAY)), today, TimeZone.UTC))
    }
}
