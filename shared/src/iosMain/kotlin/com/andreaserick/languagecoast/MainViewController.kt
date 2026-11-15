package com.andreaserick.languagecoast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.andreaserick.languagecoast.data.IosSettingsSnapshot
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.logError
import com.andreaserick.languagecoast.di.platformModule
import com.andreaserick.languagecoast.di.settingsFileExists
import com.andreaserick.languagecoast.di.sharedModule
import com.andreaserick.languagecoast.di.viewModelModule
import com.andreaserick.languagecoast.notifications.NotificationPermission
import com.andreaserick.languagecoast.notifications.ReminderScheduler
import com.andreaserick.languagecoast.notifications.notificationPermission
import com.andreaserick.languagecoast.notifications.requestNotificationPermission
import com.andreaserick.languagecoast.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController
import kotlin.coroutines.cancellation.CancellationException

/**
 * Starts dependency injection; the iOS app calls this once at launch. Settings lost in a restore of the
 * iPhone are brought back here, before any screen reads them.
 */
fun initKoin() {
    val koin = startKoin { modules(sharedModule, platformModule, viewModelModule) }.koin
    IosSettingsSnapshot.restoreIfSettingsAreGone(koin.get(), settingsFileIsThere = settingsFileExists())
}

/** The app's UI for the iOS window. */
@Suppress("FunctionName") // Named like the view controller it returns, as Swift sees it.
fun MainViewController(): UIViewController {
    val controller = ComposeUIViewController { IosAppRoot() }
    // The coast is dark until the stored theme is read, as in LanguageCoastApp. Set before the view is shown,
    // so that it waits for its first frame in black, not in white on an iPhone in light appearance.
    applyInterfaceStyle(ThemeMode.fromKey(SettingsDefaults.THEME_MODE), controller)
    return controller
}

/**
 * The app, with what only iOS needs around it: the system's appearance follows the app's theme, the week of
 * reminders is queued again whenever the app comes to the front, and a copy of the settings is saved when it
 * is left.
 */
@Composable
private fun IosAppRoot() {
    val settings = koinInject<SettingsRepository>()
    val reminderScheduler = koinInject<ReminderScheduler>()
    val controller = LocalUIViewController.current
    val scope = rememberCoroutineScope()
    val themeMode by settings.themeMode.collectAsState(initial = SettingsDefaults.THEME_MODE)

    LaunchedEffect(themeMode) { applyInterfaceStyle(ThemeMode.fromKey(themeMode), controller) }

    LifecycleResumeEffect(Unit) {
        // Again on every return to the front: the window may not have been there at the very first call.
        applyInterfaceStyle(ThemeMode.fromKey(themeMode), controller)
        scope.launch { queueReminders(reminderScheduler, settings, mayAsk = false) }
        onPauseOrDispose { IosSettingsSnapshot.save(settings) }
    }

    // A finished session is the moment a reminder makes sense to the user, so iOS's question about
    // notifications comes then and never at app start. It also takes today's reminder out of the queue.
    LaunchedEffect(Unit) {
        settings.lastStudyDate.distinctUntilChanged().drop(1).collect {
            queueReminders(reminderScheduler, settings, mayAsk = true)
        }
    }

    LanguageCoastApp()
}

/** How long the end of a session is left alone before iOS asks about notifications; the same as on Android. */
private const val ASK_DELAY_MILLIS = 1_500L

/**
 * Queues the coming week of reminders, if notifications are allowed. With [mayAsk], iOS's question about
 * notifications is shown first when the user has never answered it and reminders are switched on; iOS shows
 * it only once. A failure costs the reminders, never the app.
 */
private suspend fun queueReminders(scheduler: ReminderScheduler, settings: SettingsRepository, mayAsk: Boolean) {
    try {
        if (mayAsk && settings.reminderSettings.first().enabled && notificationPermission() == NotificationPermission.NotAsked) {
            // The end of the session is seen first.
            delay(ASK_DELAY_MILLIS)
            requestNotificationPermission()
        }
        scheduler.ensureScheduled()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logError("Reminders", "The reminders could not be queued", e)
    }
}
