package com.andreaserick.languagecoast.ui.components

import com.andreaserick.languagecoast.data.Flashcard
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class DueLabelTest {

    private val now = Instant.parse("2026-10-09T12:00:00Z").toEpochMilli()

    private fun label(due: String?): String {
        val card = Flashcard(islandId = 1, nativeText = "n", targetText = "t", due = due?.let { Instant.parse(it).toEpochMilli() })
        return dueLabel(card, now, ZoneOffset.UTC)
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
}
