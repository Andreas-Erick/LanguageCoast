package com.andreaserick.languagecoast.notifications

import com.andreaserick.languagecoast.data.ReminderSettings

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
