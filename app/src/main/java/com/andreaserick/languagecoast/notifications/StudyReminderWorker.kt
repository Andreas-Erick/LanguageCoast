package com.andreaserick.languagecoast.notifications

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Shows a daily study reminder at a random time of day, then schedules the next one.
 */
class StudyReminderWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    override fun doWork(): Result {
        Log.d(TAG, "Showing study reminder")
        NotificationHelper.showStudyNotification(applicationContext)

        // This worker is still RUNNING under WORK_NAME, so KEEP would silently drop the
        // follow-up request. APPEND_OR_REPLACE queues it to run after this one finishes.
        scheduleNextReminder(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "StudyReminderWork"
        private const val TAG = "StudyReminderWorker"

        /** Reminders are only sent between these hours (inclusive start, end hour may include its minutes). */
        private const val WINDOW_START_HOUR = 9
        private const val WINDOW_END_HOUR = 20

        /** If the app is opened before this hour, the first reminder can still fire today. */
        private const val SAME_DAY_CUTOFF_HOUR = 18

        /**
         * Schedules the first reminder. Called on every app launch; an already pending
         * reminder is kept. Before [SAME_DAY_CUTOFF_HOUR] the reminder may fire later today.
         */
        fun scheduleInitialReminder(context: Context) {
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (currentHour < SAME_DAY_CUTOFF_HOUR) {
                val startHour = maxOf(currentHour + 1, WINDOW_START_HOUR)
                enqueue(context, delayUntilRandomTime(daysFromToday = 0, startHour), ExistingWorkPolicy.KEEP)
            } else {
                scheduleNextReminder(context, ExistingWorkPolicy.KEEP)
            }
        }

        /** Schedules a reminder at a random time tomorrow. */
        fun scheduleNextReminder(context: Context, policy: ExistingWorkPolicy) {
            enqueue(context, delayUntilRandomTime(daysFromToday = 1, WINDOW_START_HOUR), policy)
        }

        private fun enqueue(context: Context, delayMillis: Long, policy: ExistingWorkPolicy) {
            val workRequest = OneTimeWorkRequestBuilder<StudyReminderWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, workRequest)
            Log.d(TAG, "Reminder scheduled in ${TimeUnit.MILLISECONDS.toMinutes(delayMillis)} minutes")
        }

        /** Milliseconds from now until a random time between [startHour] and [WINDOW_END_HOUR] on the given day. */
        private fun delayUntilRandomTime(daysFromToday: Int, startHour: Int): Long {
            val now = System.currentTimeMillis()
            val target = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, daysFromToday)
                set(Calendar.HOUR_OF_DAY, Random.nextInt(startHour, WINDOW_END_HOUR + 1))
                set(Calendar.MINUTE, Random.nextInt(60))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return (target.timeInMillis - now).coerceAtLeast(0)
        }
    }
}
