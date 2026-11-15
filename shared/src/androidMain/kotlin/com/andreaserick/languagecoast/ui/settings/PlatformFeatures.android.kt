package com.andreaserick.languagecoast.ui.settings

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.andreaserick.languagecoast.notifications.ReminderScheduler
import org.koin.compose.koinInject

actual val fileTransferAvailable = true

actual val homeScreenWidgetAvailable = true

/** A permission request the system answers faster than this was answered without showing its dialog. */
private const val NO_DIALOG_MILLIS = 600L

/**
 * Notifications are allowed when the user hasn't blocked them for the app, which from Android 13 on also
 * means the permission was given. Asking shows the system's dialog; once the system has stopped showing it
 * (after "Don't allow" twice), or on older versions that have no dialog, it opens the app's notification
 * settings instead.
 */
@Composable
actual fun rememberNotificationAccess(): NotificationAccess {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scheduler = koinInject<ReminderScheduler>()
    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }
    // The user may have switched notifications on or off in the system's settings meanwhile.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = notificationsAllowed(context) }
    // A reminder that is already waiting is kept, so this only matters once notifications become allowed.
    LaunchedEffect(allowed) { if (allowed) scheduler.ensureScheduled() }

    // Both are kept across a rotation while the system's dialog is open.
    var askedAt by rememberSaveable { mutableStateOf(0L) }
    var explainedBefore by rememberSaveable { mutableStateOf(false) }
    fun wouldExplain() =
        activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = notificationsAllowed(context)
        // The system keeps no "never ask again" flag an app can read. A refusal that came at once, with no
        // rationale due before or after, means no dialog was shown: only the settings screen can allow it now.
        val answeredAtOnce = SystemClock.elapsedRealtime() - askedAt < NO_DIALOG_MILLIS
        if (!granted && !explainedBefore && !wouldExplain() && answeredAtOnce) openNotificationSettings(context, activity)
    }

    return NotificationAccess(
        allowed = allowed,
        request = {
            val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (notificationsAllowed(context)) {
                allowed = true
            } else if (canAsk) {
                explainedBefore = wouldExplain()
                askedAt = SystemClock.elapsedRealtime()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openNotificationSettings(context, activity)
            }
        }
    )
}

/** Whether the system would show a notification from this app right now. */
private fun notificationsAllowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

/** Opens the system's notification settings for this app, or the app's details where that screen is missing. */
private fun openNotificationSettings(context: Context, activity: Activity?) {
    // From the activity the settings open on top of the app, so Back returns to it.
    val from = activity ?: context
    fun Intent.forStarting() = apply { if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    try {
        from.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).forStarting()
        )
    } catch (e: ActivityNotFoundException) {
        try {
            from.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).forStarting()
            )
        } catch (missing: ActivityNotFoundException) {
            // No settings app answers; there is nothing more the app can do.
        }
    }
}
