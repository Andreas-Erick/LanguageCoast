package com.andreaserick.languagecoast

import android.app.Application
import com.andreaserick.languagecoast.data.AndroidSettingsSnapshot
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.di.appModule
import com.andreaserick.languagecoast.di.dataModule
import com.andreaserick.languagecoast.di.platformModule
import com.andreaserick.languagecoast.di.sharedModule
import com.andreaserick.languagecoast.di.viewModelModule
import com.andreaserick.languagecoast.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LanguageCoastApplication : Application() {

    /** Starts dependency injection before any activity, widget or worker asks for a dependency. */
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@LanguageCoastApplication)
            modules(sharedModule, platformModule, appModule, dataModule, viewModelModule)
        }
        // So the phone's notification settings list "Study reminders" from the first day, not from the first reminder.
        NotificationHelper.createNotificationChannel(this)
        restoreSettingsAfterDeviceRestore()
        // Move any legacy plaintext API keys out of DataStore into hardware-backed Android KeyStore off-main.
        val migrationJob = SupervisorJob()
        CoroutineScope(migrationJob + Dispatchers.IO).launch {
            try {
                get<SettingsRepository>().migrateLegacySecrets()
            } finally {
                migrationJob.cancel()
            }
        }
    }

    /**
     * The phone's own backup brings the cards back but not the settings file, which holds the API keys. On the
     * first start after such a restore the settings come back from their copy without keys, and that is waited
     * for, so that the first screen already knows the native language and no translation starts from the wrong
     * one. It happens once and reads one small file. On every other start nothing is waited for.
     */
    private fun restoreSettingsAfterDeviceRestore() {
        val snapshot = get<AndroidSettingsSnapshot>()
        if (snapshot.restoreIsDue()) runBlocking { snapshot.restoreIfEmpty() } else snapshot.restoreIfEmptySoon()
    }
}
