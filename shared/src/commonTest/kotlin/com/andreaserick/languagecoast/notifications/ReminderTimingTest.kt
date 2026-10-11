package com.andreaserick.languagecoast.notifications

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderTimingTest {

    private val morning = LocalDateTime(2026, 10, 9, 8, 30)
    private val evening = LocalDateTime(2026, 10, 9, 19, 0)

    @Test
    fun fixedTimeIsTodayIfStillAheadOtherwiseTomorrow() {
        assertEquals(LocalDateTime(2026, 10, 9, 18, 0), nextReminderTime(LocalTime(18, 0), morning, allowToday = true))
        assertEquals(LocalDateTime(2026, 10, 10, 18, 0), nextReminderTime(LocalTime(18, 0), evening, allowToday = true))
        assertEquals(LocalDateTime(2026, 10, 10, 18, 0), nextReminderTime(LocalTime(18, 0), morning, allowToday = false))
    }

    @Test
    fun randomTimeIsLaterInTheDayWindow() {
        repeat(50) { seed ->
            for ((now, allowToday) in listOf(morning to true, evening to true, morning to false)) {
                val next = nextReminderTime(null, now, allowToday, Random(seed))
                assertTrue(next > now, "$next")
                assertTrue(next.hour in RANDOM_WINDOW_START_HOUR..RANDOM_WINDOW_END_HOUR, "$next")
                if (!allowToday || now == evening) assertEquals(now.date.day + 1, next.date.day, "$next")
            }
        }
    }
}
