package com.andreaserick.languagecoast

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.andreaserick.languagecoast.data.AndroidSettingsSnapshot
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.notifications.ReminderScheduler
import com.andreaserick.languagecoast.ui.theme.OceanGround
import com.andreaserick.languagecoast.ui.theme.ThemeMode
import com.andreaserick.languagecoast.ui.theme.isDarkPalette
import com.andreaserick.languagecoast.widget.StudyWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.toKotlinLocalDate
import org.koin.android.ext.android.inject
import java.io.File

/** The app's only activity: shows the splash screen, then the shared Compose UI. */
class MainActivity : ComponentActivity() {

    private val reminderScheduler: ReminderScheduler by inject()
    private val settings: SettingsRepository by inject()
    private val settingsSnapshot: AndroidSettingsSnapshot by inject()

    /** Whether the stored theme is the one composed, so that a frame may be drawn; see [onCreate]. */
    private var themeIsShown = false

    /**
     * Marks that the system's question about notifications was put once. It lies outside the phone's backup,
     * so a new phone, which knows nothing of the answer, asks again.
     */
    private val notificationsAsked: File get() = File(noBackupFilesDir, "notifications-asked")

    /** Holds the theme the system was last told a splash screen for (see [matchSplashToTheme]); outside the backup for the same reason. */
    private val splashThemeNote: File get() = File(noBackupFilesDir, "splash-theme")

    /** Sets up the splash screen and edge-to-edge drawing, then shows the app. */
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        // The theme is read from storage a moment after the first composition, which is in the dark one. No
        // frame is drawn until the stored theme is the one composed, so the light theme never starts with a dark
        // frame: at a cold start the splash stays those few frames longer, after a rotation the window does.
        splash.setKeepOnScreenCondition { !themeIsShown }
        // Whatever happens to that read, the app shows.
        window.decorView.postDelayed({ themeIsShown = true }, THEME_WAIT_MILLIS)
        // After a rotation the window is seen before the first frame; the theme in use is still known then.
        window.setBackgroundDrawable(ColorDrawable(OceanGround.toArgb()))
        // WorkManager needs no permission, so the reminder is queued whether or not notifications are allowed yet.
        lifecycleScope.launch { reminderScheduler.ensureScheduled() }

        setContent {
            val storedTheme by settings.themeMode.collectAsState(initial = null)
            val systemDark = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            // System bar icons must stay readable over the chosen theme: light icons on the dark coast, dark on the light.
            val dark = isDarkPalette
            val ground = OceanGround
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
                window.setBackgroundDrawable(ColorDrawable(ground.toArgb()))
                val mode = storedTheme
                if (mode != null && dark == ThemeMode.fromKey(mode).isDarkWhen(systemDark)) themeIsShown = true
            }
            LaunchedEffect(storedTheme) { storedTheme?.let { matchSplashToTheme(it) } }

            val askForNotifications = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                // The reminder is queued either way, and Settings shows whether it can appear.
                onResult = {}
            )
            // Notifications are asked for once, when a session has just been finished for the first time on this
            // phone: by then it is clear what a reminder would be for. Never at the start, and never again after
            // an answer; Settings asks when the reminder is switched on there.
            LaunchedEffect(Unit) {
                if (!mayAskForNotifications()) return@LaunchedEffect
                val before = settings.studyDays.first()
                // Only a session recorded today counts: a restored backup brings study days along without one.
                settings.studyDays.first { days ->
                    val today = java.time.LocalDate.now().toKotlinLocalDate()
                    today in days && today !in before
                }
                // The end of the session is seen first.
                delay(ASK_DELAY_MILLIS)
                // The reminder is the only notification: with it switched off there is nothing to ask for.
                if (mayAskForNotifications() && settings.reminderSettings.first().enabled) {
                    runCatching { notificationsAsked.createNewFile() }
                    askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            LanguageCoastApp()
        }
    }

    /**
     * Whether the system may be asked for the notification permission now: on Android 13 and later, while it is
     * not granted, was not asked for by this activity before, and was not refused before (after a refusal the
     * system wants a reason given first, and Settings is where the app gives it).
     */
    private fun mayAskForNotifications(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS) &&
            !notificationsAsked.exists()

    /**
     * Tells the system which splash screen goes with the theme [mode], for the starts to come. The system
     * draws the splash before the app runs, so it can't follow a setting; it keeps what it is told here
     * (Android 12 and later; before that the splash is always the manifest's, the dark coast).
     */
    private fun matchSplashToTheme(mode: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (mode == splashThemeMode) return
        splashThemeMode = mode
        val theme = ThemeMode.fromKey(mode)
        // What the system was told last is noted in a file, so it is only told again when the theme changes.
        // No file means it was never told anything, and shows the manifest's splash: the dark one.
        val told = runCatching { splashThemeNote.readText() }.getOrNull() ?: ThemeMode.Dark.key
        if (told == theme.key) return
        splashScreen.setSplashScreenTheme(
            when (theme) {
                // The manifest's own.
                ThemeMode.Dark -> Resources.ID_NULL
                ThemeMode.Light -> R.style.Theme_LanguageCoast_Starting_Light
                ThemeMode.System -> R.style.Theme_LanguageCoast_Starting_System
            }
        )
        runCatching { if (theme == ThemeMode.Dark) splashThemeNote.delete() else splashThemeNote.writeText(theme.key) }
    }

    /**
     * Refreshes the home-screen widget, since studying or adding cards may have changed its counts, and the
     * copy of the settings that the phone's own backup takes along.
     */
    override fun onStop() {
        super.onStop()
        StudyWidgetProvider.requestUpdate(this)
        settingsSnapshot.saveSoon()
    }

    companion object {
        /** The theme is stored locally and read within a few frames; after this long the app shows regardless. */
        private const val THEME_WAIT_MILLIS = 1_000L

        /** How long the end of a session is left alone before the system asks about notifications. */
        private const val ASK_DELAY_MILLIS = 1_500L

        /** The theme whose splash was last seen to in this run of the app, so a rotation doesn't look again. */
        private var splashThemeMode: String? = null

        /**
         * The intent that brings the app to the front as it was left, or starts it: the one the launcher icon
         * sends. The widget and the reminder open the app with it, so a session in progress stays as it is.
         */
        fun resumeIntent(context: Context): Intent =
            (context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClass(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                // The launcher's intent names no package. Up to Android 10 one that does counts as a different
                // intent, and a second copy of the app would be put on top of the first.
                .setPackage(null)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

/**
 * Whether this theme is dark on a phone that is itself set dark or not ([systemDark]). It must say what
 * LanguageCoastTheme decides, or the first frame waits for [MainActivity]'s time limit.
 */
private fun ThemeMode.isDarkWhen(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.System -> systemDark
    ThemeMode.Dark -> true
    ThemeMode.Light -> false
}
