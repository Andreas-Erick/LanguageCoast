package com.andreaserick.languagecoast.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class StreakTest {

    private val today = LocalDate(2026, 10, 8)

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
        assertEquals(5, nextStreak(lastStudy = today.minus(1, DateTimeUnit.DAY), currentStreak = 4, today = today))
    }

    @Test
    fun sessionAfterMissedDayRestartsAtOne() {
        assertEquals(1, nextStreak(lastStudy = today.minus(2, DateTimeUnit.DAY), currentStreak = 4, today = today))
    }

    @Test
    fun streakIncrementsAcrossMonthBoundary() {
        val firstOfMonth = LocalDate(2026, 11, 1)
        assertEquals(3, nextStreak(lastStudy = LocalDate(2026, 10, 31), currentStreak = 2, today = firstOfMonth))
    }

    @Test
    fun streakIsKeptUntilAFullDayIsMissed() {
        assertFalse(isStreakBroken(lastStudy = today, today = today))
        assertFalse(isStreakBroken(lastStudy = today.minus(1, DateTimeUnit.DAY), today = today))
        assertTrue(isStreakBroken(lastStudy = today.minus(2, DateTimeUnit.DAY), today = today))
    }
}
