package com.andreaserick.languagecoast.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.andreaserick.languagecoast.MainActivity
import com.andreaserick.languagecoast.R

/** Creates the notification channel and shows the daily study reminder. */
object NotificationHelper {
    private const val CHANNEL_ID = "study_reminder_channel"
    private const val NOTIFICATION_ID = 1001
    private const val LARGE_ICON_PX = 256

    // The app's colours (ui/theme/Color.kt): the accent, the raised surface and the "Again" coral.
    private const val SUNLIT = 0xFFF3C969.toInt()
    private const val SURFACE_RAISED = 0xFF1F4560.toInt()
    private const val CORAL = 0xFFF5A28C.toInt()

    /** Creates the channel study reminders are posted to (Android 8 and later); creating it again changes nothing. */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Study Reminders"
            val descriptionText = "Daily notifications to remind you to practice"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Shows [message] as the study reminder. Each [ReminderKind] has its own look: the everyday reminder
     * carries the app's mark, the others a picture of what they're about (a flame for a streak at risk,
     * the island's emoji for a quoted card), and a streak at risk is tinted coral instead of gold.
     */
    fun showStudyNotification(context: Context, message: ReminderMessage) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val largeIcon = when (message.kind) {
            // The launcher icon is an adaptive icon (XML), so it's drawn rather than decoded.
            ReminderKind.Due -> ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(LARGE_ICON_PX, LARGE_ICON_PX)
            else -> emojiTile(message.emoji)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setColor(if (message.kind == ReminderKind.StreakAtRisk) CORAL else SUNLIT)
            .setContentTitle(message.title)
            .setContentText(message.text)
            // A quoted sentence can be long; expanded, the whole of it shows.
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // A quoted card can be answered from the notification: reveal it, then say whether it was known.
        val teaser = message.teaser
        if (message.kind == ReminderKind.Teaser && teaser != null && teaser.cardId != 0) {
            builder.addAction(0, "Reveal", ReminderActionReceiver.pendingIntent(context, ReminderActionReceiver.ACTION_REVEAL, teaser))
        }

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    /** Replaces the teaser reminder with [teaser]'s answer and the buttons that grade it. */
    fun showRevealedTeaser(context: Context, teaser: ReminderCard) {
        createNotificationChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val open = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = "${teaser.targetText}\n“${teaser.nativeText}” · ${teaser.emoji} ${teaser.islandName}"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(emojiTile(teaser.emoji))
            .setColor(SUNLIT)
            .setContentTitle("Did you know it?")
            .setContentText(teaser.targetText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Got it", ReminderActionReceiver.pendingIntent(context, ReminderActionReceiver.ACTION_GOOD, teaser))
            .addAction(0, "Forgot", ReminderActionReceiver.pendingIntent(context, ReminderActionReceiver.ACTION_AGAIN, teaser))
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    /** Takes the reminder down, e.g. once its card was graded. */
    fun dismiss(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
    }

    /** [emoji] on a rounded tile in the app's raised surface colour, like the icon tiles in the app. */
    private fun emojiTile(emoji: String): Bitmap {
        val size = LARGE_ICON_PX
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val tile = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SURFACE_RAISED }
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.28f, size * 0.28f, tile)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * 0.56f
            textAlign = Paint.Align.CENTER
        }
        // Centre the glyph vertically on its own bounds, not the font's.
        val baseline = size / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(emoji, size / 2f, baseline, text)
        return bitmap
    }
}
