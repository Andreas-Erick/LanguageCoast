package com.andreaserick.languagecoast

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.ComposeUIViewController
import com.andreaserick.languagecoast.di.platformModule
import com.andreaserick.languagecoast.di.sharedModule
import com.andreaserick.languagecoast.di.viewModelModule
import com.andreaserick.languagecoast.notifications.ReminderScheduler
import com.andreaserick.languagecoast.notifications.requestNotificationPermission
import org.koin.compose.koinInject
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

/** Starts dependency injection; the iOS app calls this once at launch. */
fun initKoin() {
    startKoin { modules(sharedModule, platformModule, viewModelModule) }
}

/** The app's UI for the iOS window. Reminders are scheduled once notifications may be shown, as on Android. */
@Suppress("FunctionName") // Named like the view controller it returns, as Swift sees it.
fun MainViewController(): UIViewController = ComposeUIViewController {
    val reminderScheduler = koinInject<ReminderScheduler>()
    LaunchedEffect(Unit) {
        if (requestNotificationPermission()) reminderScheduler.ensureScheduled()
    }
    LanguageCoastApp()
}
