package com.andreaserick.languagecoast.notifications

import com.andreaserick.languagecoast.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Clock

/**
 * [ReminderScheduler] for iOS. iOS can't run the app's code when a reminder is due, so reminders are
 * queued ahead: one repeating notification for a fixed time, or a week of random times, renewed
 * whenever the app is opened. The text can't include the number of due cards, which is only known then.
 */
class IosReminderScheduler(
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val timeZone: TimeZone
) : ReminderScheduler {

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun ensureScheduled() = reschedule()

    override suspend fun reschedule() {
        center.removePendingNotificationRequestsWithIdentifiers(IDENTIFIERS)
        val reminder = settings.reminderSettings.first()
        if (!reminder.enabled) return

        val time = reminder.time
        if (time != null) {
            val daily = NSDateComponents().apply {
                hour = time.hour.toLong()
                minute = time.minute.toLong()
            }
            add(IDENTIFIERS.first(), daily, repeats = true)
        } else {
            var next = nextReminderTime(null, clock.now().toLocalDateTime(timeZone), allowToday = true)
            IDENTIFIERS.forEach { id ->
                add(id, next.toComponents(), repeats = false)
                next = nextReminderTime(null, next, allowToday = false)
            }
        }
    }

    private fun add(id: String, components: NSDateComponents, repeats: Boolean) {
        val content = UNMutableNotificationContent().apply {
            setTitle("Time to study!")
            setBody("Keep your Language Coast growing. Practice today!")
            setSound(UNNotificationSound.defaultSound)
        }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats)
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(id, content, trigger), withCompletionHandler = null)
    }

    private companion object {
        /** One per day for random times; a fixed time only uses the first. */
        val IDENTIFIERS = (0 until 7).map { "study-reminder-$it" }
    }
}

private fun LocalDateTime.toComponents() = NSDateComponents().also {
    it.year = year.toLong()
    it.month = month.ordinal.toLong() + 1
    it.day = day.toLong()
    it.hour = hour.toLong()
    it.minute = minute.toLong()
}

/** Asks once whether the app may show notifications; true if it may. */
suspend fun requestNotificationPermission(): Boolean = suspendCoroutine { continuation ->
    UNUserNotificationCenter.currentNotificationCenter()
        .requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
            continuation.resume(granted)
        }
}
