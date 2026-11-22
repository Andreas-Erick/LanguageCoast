package com.andreaserick.languagecoast.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.PersonalIslands
import com.andreaserick.languagecoast.data.PlacedCard
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.isDue
import com.andreaserick.languagecoast.data.logError
import com.andreaserick.languagecoast.widget.StudyWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.datetime.toKotlinLocalTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
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
            return
        }

        val delay = delayUntilNextReminder(reminder.time?.toJavaLocalTime(), LocalDateTime.now(clock), allowToday)
        val request = OneTimeWorkRequestBuilder<StudyReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
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

/** The names of the islands written from what the user told the app about themselves. */
private val ISLANDS_ABOUT_USER: Set<String> = PersonalIslands.ALL.filter { it.isAboutUser }.mapTo(HashSet()) { it.name }

/**
 * The card a reminder may quote on [day], a different one each day (from the same seed as the message), or
 * null if there is none. It is a card the user has studied before that is due again at [now]: never one they
 * haven't seen yet, and never one from an island about themselves (their health, their family), whose
 * sentences don't belong on a lock screen.
 */
internal fun teaserCard(cards: List<PlacedCard>, now: Long, day: kotlinx.datetime.LocalDate): PlacedCard? {
    val quotable = cards.filter { it.card.due != null && isDue(it.card, now) && it.islandName !in ISLANDS_ABOUT_USER }
    return quotable.getOrNull((day.year * 366 + day.dayOfYear).mod(quotable.size.coerceAtLeast(1)))
}

/** Shows the study reminder, then schedules the next one. */
class StudyReminderWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams), KoinComponent {

    private val reminderScheduler: WorkManagerReminderScheduler by inject()
    private val flashcards: FlashcardRepository by inject()
    private val settings: SettingsRepository by inject()
    private val clock: Clock by inject()

    /**
     * Shows the reminder that fits today, then queues the next one. A run that goes wrong still queues it and
     * still counts as done: a failed worker would take the reminder queued behind it down with it, and nothing
     * would remind again until the app is next opened.
     */
    override suspend fun doWork(): Result {
        try {
            showReminder()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(TAG, "Couldn't show the study reminder", e)
        }
        reminderScheduler.scheduleNext()
        return Result.success()
    }

    /** Shows today's reminder (see [reminderMessage]) unless today has been studied already, and refreshes the widget. */
    private suspend fun showReminder() {
        val now = clock.millis()
        val today = LocalDate.now(clock).toKotlinLocalDate()
        // A missed day must count as a lost streak, as it would on opening the app.
        settings.refreshStreak()
        // Someone who has studied today is not asked to again.
        if (today !in settings.studyDays.first()) {
            val teaser = teaserCard(flashcards.observeAllCards().first(), now, today)
            val message = reminderMessage(
                // The count the widget shows: new cards count only as far as a day takes them on.
                dueCount = flashcards.countDue(now).forToday(settings.pace.first()),
                streak = settings.streakCount.first(),
                studiedToday = false,
                teaser = teaser?.let { ReminderCard(it.card.nativeText, it.language, it.islandName, it.emoji, it.card.cardId, it.card.targetText) },
                day = today
            )
            NotificationHelper.showStudyNotification(applicationContext, message)
        }
        // The widget's streak and count follow the day as well.
        StudyWidgetProvider.requestUpdate(applicationContext)
    }

    private companion object {
        const val TAG = "StudyReminderWorker"
    }
}
