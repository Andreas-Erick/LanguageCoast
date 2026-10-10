package com.andreaserick.languagecoast.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.andreaserick.languagecoast.MainActivity
import com.andreaserick.languagecoast.R

/** Creates the notification channel and shows the daily study reminder. */
object NotificationHelper {
    private const val CHANNEL_ID = "study_reminder_channel"
    private const val NOTIFICATION_ID = 1001

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

    /** Shows the study reminder; [dueCount] cards due across all coasts are mentioned when there are any. */
    fun showStudyNotification(context: Context, dueCount: Int) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val largeIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.logo_android_language_coast)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Using standard foreground icon
            .setLargeIcon(largeIcon)
            .setContentTitle("Time to study!")
            .setContentText(
                when (dueCount) {
                    0 -> "Keep your Language Coast growing. Practice today!"
                    1 -> "1 card is due for review today."
                    else -> "$dueCount cards are due for review today."
                }
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }
}
