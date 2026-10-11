package com.andreaserick.languagecoast.notifications

import com.andreaserick.languagecoast.data.ReminderSettings
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.random.Random

/** Schedules the daily study reminder according to the user's [ReminderSettings]. */
interface ReminderScheduler {
    /** Called on app launch: schedules a reminder unless one is already pending, or cancels if reminders are off. */
    suspend fun ensureScheduled()

    /** Called after the reminder settings change: replaces any pending reminder. */
    suspend fun reschedule()
}

/** Random reminders are sent between these hours (the end hour may include its minutes). */
const val RANDOM_WINDOW_START_HOUR = 9
const val RANDOM_WINDOW_END_HOUR = 20

/** A random reminder can still fire on the same day if the app is opened before this hour. */
const val RANDOM_SAME_DAY_CUTOFF_HOUR = 18

/**
 * When the next reminder is due after [now]: at [time] if set, otherwise at a random time in the day window.
 * With [allowToday] it may still be later today; otherwise it is always the next day.
 */
fun nextReminderTime(time: LocalTime?, now: LocalDateTime, allowToday: Boolean, random: Random = Random.Default): LocalDateTime {
    val tomorrow = now.date.plus(1, DateTimeUnit.DAY)
    return if (time != null) {
        val today = LocalDateTime(now.date, time)
        if (allowToday && today > now) today else LocalDateTime(tomorrow, time)
    } else if (allowToday && now.hour < RANDOM_SAME_DAY_CUTOFF_HOUR) {
        val startHour = maxOf(now.hour + 1, RANDOM_WINDOW_START_HOUR)
        LocalDateTime(now.date, LocalTime(random.nextInt(startHour, RANDOM_WINDOW_END_HOUR + 1), random.nextInt(60)))
    } else {
        LocalDateTime(tomorrow, LocalTime(random.nextInt(RANDOM_WINDOW_START_HOUR, RANDOM_WINDOW_END_HOUR + 1), random.nextInt(60)))
    }
}
