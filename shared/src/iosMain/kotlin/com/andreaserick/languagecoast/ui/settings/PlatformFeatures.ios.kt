package com.andreaserick.languagecoast.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.andreaserick.languagecoast.data.logError
import com.andreaserick.languagecoast.notifications.NotificationPermission
import com.andreaserick.languagecoast.notifications.ReminderScheduler
import com.andreaserick.languagecoast.notifications.iosNotificationsAllowed
import com.andreaserick.languagecoast.notifications.notificationPermission
import com.andreaserick.languagecoast.notifications.requestNotificationPermission
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import kotlin.coroutines.cancellation.CancellationException

actual val fileTransferAvailable = false

actual val homeScreenWidgetAvailable = false

/**
 * iOS answers the question about notifications asynchronously, so [NotificationAccess.allowed] is what was
 * last read; it is read again whenever the app comes back to the front, which is also when the user returns
 * from iOS's own question or from the iPhone's Settings.
 */
@Composable
actual fun rememberNotificationAccess(): NotificationAccess {
    val scheduler = koinInject<ReminderScheduler>()
    val scope = rememberCoroutineScope()
    val allowed by iosNotificationsAllowed.collectAsState()

    LifecycleResumeEffect(Unit) {
        scope.launch { notificationPermission() }
        onPauseOrDispose {}
    }

    return remember(allowed, scheduler, scope) {
        NotificationAccess(
            allowed = allowed,
            request = {
                scope.launch {
                    try {
                        when (notificationPermission()) {
                            NotificationPermission.NotAsked -> requestNotificationPermission()
                            // iOS asks once. After a refusal the only way is the iPhone's Settings.
                            NotificationPermission.Denied -> openNotificationSettingsOfIphone()
                            NotificationPermission.Allowed -> Unit
                        }
                        // Queues the reminders if they are allowed now, and does nothing otherwise.
                        scheduler.reschedule()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logError("NotificationAccess", "Notifications could not be asked for", e)
                    }
                }
            }
        )
    }
}

/** Opens the app's notifications page in the iPhone's Settings, where they can be switched on. */
private fun openNotificationSettingsOfIphone() {
    val url = NSURL.URLWithString(UIApplicationOpenNotificationSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(url = url, options = emptyMap<Any?, Any>(), completionHandler = null)
}
