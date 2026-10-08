package com.andreaserick.languagecoast.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StreakTest {

    private val today = LocalDate.of(2026, 10, 8)

    @Test
    fun firstEverSessionStartsStreakAtOne() {
        assertEquals(1, nextStreak(lastStudy = null, currentStreak = 0, today = today))
    }

    @Test
    fun secondSessionOnSameDayDoesNotIncrement() {
        assertEquals(4, nextStreak(lastStudy = today, currentStreak = 4, today = today))
    }

    @Test
    fun sessionDayAfterLastIncrements() {
        assertEquals(5, nextStreak(lastStudy = today.minusDays(1), currentStreak = 4, today = today))
    }

    @Test
    fun sessionAfterMissedDayRestartsAtOne() {
        assertEquals(1, nextStreak(lastStudy = today.minusDays(2), currentStreak = 4, today = today))
    }

    @Test
    fun streakIncrementsAcrossMonthBoundary() {
        val firstOfMonth = LocalDate.of(2026, 11, 1)
        assertEquals(3, nextStreak(lastStudy = LocalDate.of(2026, 10, 31), currentStreak = 2, today = firstOfMonth))
    }

    @Test
    fun streakIsKeptUntilAFullDayIsMissed() {
        assertFalse(isStreakBroken(lastStudy = today, today = today))
        assertFalse(isStreakBroken(lastStudy = today.minusDays(1), today = today))
        assertTrue(isStreakBroken(lastStudy = today.minusDays(2), today = today))
    }
}
