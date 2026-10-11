package com.andreaserick.languagecoast

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.andreaserick.languagecoast.notifications.ReminderScheduler
import com.andreaserick.languagecoast.widget.StudyWidgetProvider
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/** The app's only activity: shows the splash screen, then the shared Compose UI. */
class MainActivity : ComponentActivity() {

    private val reminderScheduler: ReminderScheduler by inject()

    /** Sets up the splash screen and edge-to-edge drawing, then shows the app. */
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // The app is always dark, so use light system bar icons over a transparent background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val scheduleReminders = { lifecycleScope.launch { reminderScheduler.ensureScheduled() } }
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { isGranted -> if (isGranted) scheduleReminders() }
            )

            // Reminders are scheduled once notifications may be shown.
            LaunchedEffect(Unit) {
                val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

                if (needsPermission) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    scheduleReminders()
                }
            }

            LanguageCoastApp()
        }
    }

    /** Refreshes the home-screen widget, since studying or adding cards may have changed its counts. */
    override fun onStop() {
        super.onStop()
        StudyWidgetProvider.requestUpdate(this)
    }
}
