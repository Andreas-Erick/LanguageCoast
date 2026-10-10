package com.andreaserick.languagecoast.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.andreaserick.languagecoast.MainActivity
import com.andreaserick.languagecoast.R
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** What the home-screen widget shows; see [widgetContent]. */
data class WidgetContent(
    val streak: String,
    val due: String,
    val dueLabel: String,
    val footer: String
)

/**
 * The widget's texts for [dueCount] cards due on all coasts and a streak of [streak] days.
 * [studiedToday] says whether today already counts towards the streak.
 */
fun widgetContent(dueCount: Int, streak: Int, studiedToday: Boolean): WidgetContent = WidgetContent(
    streak = if (streak > 0) "🔥 $streak" else "⚓ 0",
    due = if (dueCount > 0) dueCount.toString() else "✓",
    dueLabel = when (dueCount) {
        0 -> "all caught up"
        1 -> "card due"
        else -> "cards due"
    },
    footer = when {
        studiedToday -> "Studied today. Nice work!"
        streak > 0 -> "Study today to keep your streak"
        dueCount > 0 -> "Finish an island to start a streak"
        else -> "Add cards to start studying"
    }
)

/**
 * The home-screen widget: cards due today and the study streak, a gentler nudge than a notification.
 * Tapping it opens the app. It refreshes every hour (see study_widget_info.xml) and whenever the app
 * is left (see [requestUpdate]).
 */
@AndroidEntryPoint
class StudyWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var flashcards: FlashcardRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var clock: Clock

    /** Reads the counts off the main thread, then redraws every placed widget. */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // A missed day must show as a lost streak, as it would on opening the app.
                settings.refreshStreak()
                val content = widgetContent(
                    dueCount = flashcards.countDueCards(clock.millis()),
                    streak = settings.streakCount.first(),
                    studiedToday = LocalDate.now(clock) in settings.studyDays.first()
                )
                val views = remoteViews(context, content)
                appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't update the widget", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** The widget's views filled with [content]; tapping anywhere opens the app. */
    private fun remoteViews(context: Context, content: WidgetContent): RemoteViews {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteViews(context.packageName, R.layout.widget_study).apply {
            setTextViewText(R.id.widget_streak, content.streak)
            setTextViewText(R.id.widget_due, content.due)
            setTextViewText(R.id.widget_due_label, content.dueLabel)
            setTextViewText(R.id.widget_footer, content.footer)
            setOnClickPendingIntent(R.id.widget_root, openApp)
        }
    }

    companion object {
        private const val TAG = "StudyWidget"

        /** Redraws any placed widgets, e.g. after studying changed the counts. Does nothing if none are placed. */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, StudyWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, StudyWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
    }
}
