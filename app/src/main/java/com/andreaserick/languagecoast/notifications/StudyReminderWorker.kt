package com.andreaserick.languagecoast.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.widget.StudyWidgetProvider
import kotlinx.coroutines.flow.first
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.datetime.toKotlinLocalTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** [ReminderScheduler] that queues the reminder as one-time WorkManager work, replaced each time it runs. */
class WorkManagerReminderScheduler(
    private val context: Context,
    private val settings: SettingsRepository,
    private val clock: Clock
) : ReminderScheduler {

    override suspend fun ensureScheduled() = schedule(ExistingWorkPolicy.KEEP, allowToday = true)

    override suspend fun reschedule() = schedule(ExistingWorkPolicy.REPLACE, allowToday = true)

    /** Called by the worker after showing a reminder, to queue tomorrow's. */
    suspend fun scheduleNext() {
        // The worker is still RUNNING under WORK_NAME, so KEEP would silently drop the
        // follow-up request. APPEND_OR_REPLACE queues it to run after this one finishes.
        schedule(ExistingWorkPolicy.APPEND_OR_REPLACE, allowToday = false)
    }

    /**
     * Enqueues the next reminder under [WORK_NAME] using [policy], or cancels it if reminders are off.
     * With [allowToday] it may fire later today; see [delayUntilNextReminder].
     */
    private suspend fun schedule(policy: ExistingWorkPolicy, allowToday: Boolean) {
        val reminder = settings.reminderSettings.first()
        val workManager = WorkManager.getInstance(context)
        if (!reminder.enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Reminders are off")
            return
        }

        val delay = delayUntilNextReminder(reminder.time?.toJavaLocalTime(), LocalDateTime.now(clock), allowToday)
        val request = OneTimeWorkRequestBuilder<StudyReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
        Log.d(TAG, "Reminder scheduled in ${delay.toMinutes()} minutes")
    }

    private companion object {
        const val WORK_NAME = "StudyReminderWork"
        const val TAG = "StudyReminderWorker"
    }
}

/**
 * Time from [now] until the next reminder (see [nextReminderTime]): at [time] if set, otherwise at a random
 * time in the day window. With [allowToday] the reminder may still fire later today; otherwise it is always tomorrow.
 */
internal fun delayUntilNextReminder(
    time: LocalTime?,
    now: LocalDateTime,
    allowToday: Boolean,
    random: Random = Random.Default
): Duration {
    val target = nextReminderTime(time?.toKotlinLocalTime(), now.toKotlinLocalDateTime(), allowToday, random).toJavaLocalDateTime()
    return Duration.between(now, target).coerceAtLeast(Duration.ZERO)
}

/** This duration, or [min] if it is shorter. */
private fun Duration.coerceAtLeast(min: Duration): Duration = if (this < min) min else this

/** Shows the study reminder, then schedules the next one. */
class StudyReminderWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams), KoinComponent {

    private val reminderScheduler: WorkManagerReminderScheduler by inject()
    private val flashcards: FlashcardRepository by inject()
    private val clock: Clock by inject()

    /** Shows the reminder with the number of due cards, then queues the next one. */
    override suspend fun doWork(): Result {
        Log.d("StudyReminderWorker", "Showing study reminder")
        val dueCount = flashcards.countDueCards(clock.millis())
        NotificationHelper.showStudyNotification(applicationContext, dueCount)
        // The widget should show the same count as the reminder.
        StudyWidgetProvider.requestUpdate(applicationContext)
        reminderScheduler.scheduleNext()
        return Result.success()
    }
}
