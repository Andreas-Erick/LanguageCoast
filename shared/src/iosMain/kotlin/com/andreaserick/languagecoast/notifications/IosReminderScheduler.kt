package com.andreaserick.languagecoast.notifications

import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.logError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Clock

/**
 * [ReminderScheduler] for iOS. iOS can't run the app's code when a reminder is due, so a week of reminders
 * is queued ahead, one a day at the fixed time or at a random one. The week is queued again whenever the app
 * is opened or comes back to the front, and after a finished session, so it keeps rolling for as long as the
 * app is used and stops a week after it was last opened. A random time already queued for later today is
 * kept then; only a change in Settings draws it again. The text can't include the number of due cards,
 * which is only known then.
 *
 * Nothing is asked here: reminders are only queued once the user has allowed notifications
 * (see [requestNotificationPermission]).
 */
class IosReminderScheduler(
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val timeZone: TimeZone
) : ReminderScheduler {

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    /** Queuing is asked for from several places (app start, resume, Settings); one runs at a time. */
    private val mutex = Mutex()

    override suspend fun ensureScheduled() {
        mutex.withLock { queueWeek(keepToday = true) }
    }

    override suspend fun reschedule() {
        mutex.withLock { queueWeek(keepToday = false) }
    }

    private suspend fun queueWeek(keepToday: Boolean) {
        val reminder = settings.reminderSettings.first()
        val now = clock.now().toLocalDateTime(timeZone)
        // A surprise time already queued for later today stays: drawing again on every return to the app
        // would move it, and from 18:00 on would drop it.
        val kept = if (keepToday && reminder.time == null) pendingLaterToday(now) else null
        center.removePendingNotificationRequestsWithIdentifiers(IDENTIFIERS)
        if (!reminder.enabled) return
        if (notificationPermission() != NotificationPermission.Allowed) return

        // One reminder per day of the coming week. Today's is left out once today's session is done.
        val studiedToday = settings.lastStudyDate.first() == now.date
        var next = kept?.takeUnless { studiedToday } ?: nextReminderTime(reminder.time, now, allowToday = !studiedToday)
        IDENTIFIERS.forEach { id ->
            // Worded by its day, so a day keeps its text when the week is queued again and the next day differs.
            add(id, next.toComponents(), scheduledReminderMessage(next.date.dayOfYear))
            next = nextReminderTime(reminder.time, next, allowToday = false)
        }
    }

    /** The time of the reminder still queued for later today, or null if there is none. */
    private suspend fun pendingLaterToday(now: LocalDateTime): LocalDateTime? = suspendCoroutine<LocalDateTime?> { continuation ->
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            val time = requests.orEmpty().firstNotNullOfOrNull { request ->
                val components = ((request as? UNNotificationRequest)
                    ?.takeIf { it.identifier in IDENTIFIERS }
                    ?.trigger as? UNCalendarNotificationTrigger)?.dateComponents
                // A component that is not set makes the constructor throw, and nothing may be thrown from here.
                runCatching {
                    components?.let {
                        LocalDateTime(it.year.toInt(), it.month.toInt(), it.day.toInt(), it.hour.toInt(), it.minute.toInt())
                    }
                }.getOrNull()?.takeIf { it.date == now.date && it > now }
            }
            continuation.resume(time)
        }
    }

    private fun add(id: String, components: NSDateComponents, message: ReminderMessage) {
        val content = UNMutableNotificationContent().apply {
            setTitle(message.title)
            setBody(message.text)
            setSound(UNNotificationSound.defaultSound)
        }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
        center.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(id, content, trigger),
            withCompletionHandler = { error ->
                if (error != null) logError(TAG, "A reminder could not be queued: ${error.localizedDescription}")
            }
        )
    }

    private companion object {
        const val TAG = "IosReminderScheduler"

        /** One per day of the coming week. */
        val IDENTIFIERS = (0 until 7).map { "study-reminder-$it" }
    }
}

private fun LocalDateTime.toComponents() = NSDateComponents().also {
    // The date is a Gregorian one. Without this, an iPhone set to the Buddhist, Japanese or Persian calendar
    // would read the year in its own count and the reminder would never come.
    it.calendar = NSCalendar.calendarWithIdentifier(NSCalendarIdentifierGregorian)
    it.year = year.toLong()
    it.month = month.ordinal.toLong() + 1
    it.day = day.toLong()
    it.hour = hour.toLong()
    it.minute = minute.toLong()
}

/** What iOS says about showing the app's notifications. */
internal enum class NotificationPermission {
    /** The user has not been asked yet; [requestNotificationPermission] shows iOS's question. */
    NotAsked,

    /** The user said no. iOS does not ask again; it can only be changed in the iPhone's Settings. */
    Denied,

    Allowed
}

private val allowedState = MutableStateFlow(false)

/** Whether iOS lets the app show notifications, as last read by [notificationPermission]; false until then. */
internal val iosNotificationsAllowed: StateFlow<Boolean> = allowedState

/** Reads whether the app may show notifications, without asking the user anything. */
internal suspend fun notificationPermission(): NotificationPermission {
    val permission = suspendCoroutine<NotificationPermission> { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            continuation.resume(
                when (settings?.authorizationStatus) {
                    UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional -> NotificationPermission.Allowed
                    UNAuthorizationStatusDenied -> NotificationPermission.Denied
                    else -> NotificationPermission.NotAsked
                }
            )
        }
    }
    allowedState.value = permission == NotificationPermission.Allowed
    return permission
}

/**
 * Asks whether the app may show notifications; true if it may. iOS shows its question the first time only
 * and answers by itself after that, so this is never called at app start (see [NotificationPermission]).
 */
suspend fun requestNotificationPermission(): Boolean {
    val granted = suspendCoroutine<Boolean> { continuation ->
        UNUserNotificationCenter.currentNotificationCenter()
            .requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { answer, _ ->
                continuation.resume(answer)
            }
    }
    allowedState.value = granted
    return granted
}
