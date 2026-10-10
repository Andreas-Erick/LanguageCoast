package com.andreaserick.languagecoast.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.ReminderSettings
import com.andreaserick.languagecoast.data.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** Schedules the daily study reminder according to the user's [ReminderSettings]. */
interface ReminderScheduler {
    /** Called on app launch: schedules a reminder unless one is already pending, or cancels if reminders are off. */
    suspend fun ensureScheduled()

    /** Called after the reminder settings change: replaces any pending reminder. */
    suspend fun reschedule()
}

/** [ReminderScheduler] that queues the reminder as one-time WorkManager work, replaced each time it runs. */
@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
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

        val delay = delayUntilNextReminder(reminder.time, LocalDateTime.now(clock), allowToday)
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

/** Random reminders are sent between these hours (the end hour may include its minutes). */
internal const val RANDOM_WINDOW_START_HOUR = 9
internal const val RANDOM_WINDOW_END_HOUR = 20

/** A random reminder can still fire on the same day if the app is opened before this hour. */
internal const val RANDOM_SAME_DAY_CUTOFF_HOUR = 18

/**
 * Time from [now] until the next reminder: at [time] if set, otherwise at a random time in the day window.
 * With [allowToday] the reminder may still fire later today; otherwise it is always tomorrow.
 */
internal fun delayUntilNextReminder(
    time: LocalTime?,
    now: LocalDateTime,
    allowToday: Boolean,
    random: Random = Random.Default
): Duration {
    val target = if (time != null) {
        val today = now.toLocalDate().atTime(time)
        if (allowToday && today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(time)
    } else if (allowToday && now.hour < RANDOM_SAME_DAY_CUTOFF_HOUR) {
        val startHour = maxOf(now.hour + 1, RANDOM_WINDOW_START_HOUR)
        now.toLocalDate().atTime(random.nextInt(startHour, RANDOM_WINDOW_END_HOUR + 1), random.nextInt(60))
    } else {
        now.toLocalDate().plusDays(1).atTime(random.nextInt(RANDOM_WINDOW_START_HOUR, RANDOM_WINDOW_END_HOUR + 1), random.nextInt(60))
    }
    return Duration.between(now, target).coerceAtLeast(Duration.ZERO)
}

/** This duration, or [min] if it is shorter. */
private fun Duration.coerceAtLeast(min: Duration): Duration = if (this < min) min else this

/** Shows the study reminder, then schedules the next one. */
class StudyReminderWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    /** Gives the worker, which Hilt doesn't construct, access to the singletons it needs. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SchedulerEntryPoint {
        fun reminderScheduler(): WorkManagerReminderScheduler
        fun flashcardRepository(): FlashcardRepository
        fun clock(): Clock
    }

    /** Shows the reminder with the number of due cards, then queues the next one. */
    override suspend fun doWork(): Result {
        Log.d("StudyReminderWorker", "Showing study reminder")
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, SchedulerEntryPoint::class.java)
        val dueCount = entryPoint.flashcardRepository().countDueCards(entryPoint.clock().millis())
        NotificationHelper.showStudyNotification(applicationContext, dueCount)
        entryPoint.reminderScheduler().scheduleNext()
        return Result.success()
    }
}
