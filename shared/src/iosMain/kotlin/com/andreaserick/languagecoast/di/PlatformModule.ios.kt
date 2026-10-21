package com.andreaserick.languagecoast.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.andreaserick.languagecoast.data.AppDatabase
import com.andreaserick.languagecoast.data.buildAppDatabase
import com.andreaserick.languagecoast.data.createSettingsDataStore
import com.andreaserick.languagecoast.notifications.IosReminderScheduler
import com.andreaserick.languagecoast.notifications.ReminderScheduler
import com.andreaserick.languagecoast.speech.IosSpeaker
import com.andreaserick.languagecoast.speech.Speaker
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

actual val platformModule = module {

    /** The database file in the app's Documents folder, opened with the SQLite bundled into the app. */
    single {
        val builder = Room.databaseBuilder<AppDatabase>(name = "${documentsPath()}/${AppDatabase.NAME}").setDriver(BundledSQLiteDriver())
        buildAppDatabase(builder, settings = get())
    }

    single { createSettingsDataStore(settingsPath()) }

    /** Text is read aloud with iOS's speech synthesizer. */
    singleOf(::IosSpeaker) { bind<Speaker>() }

    /** Reminders are queued with iOS's notification center. (Android binds its WorkManager scheduler in the app.) */
    singleOf(::IosReminderScheduler) { bind<ReminderScheduler>() }
}

/** The app's Documents folder, which iOS keeps across updates and includes in backups. */
@OptIn(ExperimentalForeignApi::class)
private fun documentsPath(): String = requireNotNull(documentsUrl().path) { "No Documents folder" }

@OptIn(ExperimentalForeignApi::class)
private fun documentsUrl(): NSURL =
    requireNotNull(NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, false, null)) {
        "No Documents folder"
    }

/**
 * The settings file, which holds the user's Gemini and OpenRouter API keys. It lives in a folder in
 * Application Support that is excluded from iCloud backup, so keys never leave the device (Android
 * excludes it the same way in its backup rules). The flag is set on the folder because DataStore
 * replaces the file on every write. Earlier versions kept the file in Documents; it is moved over once.
 */
@OptIn(ExperimentalForeignApi::class)
private fun settingsPath(): String {
    val fileManager = NSFileManager.defaultManager
    val supportUrl = requireNotNull(
        fileManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
    ) { "No Application Support folder" }
    val folder = requireNotNull(supportUrl.URLByAppendingPathComponent("datastore")) { "No settings folder" }
    fileManager.createDirectoryAtURL(folder, withIntermediateDirectories = true, attributes = null, error = null)
    folder.setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)

    val file = requireNotNull(folder.URLByAppendingPathComponent(SETTINGS_FILE)) { "No settings file" }
    val legacyFile = documentsUrl().URLByAppendingPathComponent(SETTINGS_FILE)
    if (legacyFile != null && fileManager.fileExistsAtPath(legacyFile.path!!) && !fileManager.fileExistsAtPath(file.path!!)) {
        fileManager.moveItemAtURL(legacyFile, toURL = file, error = null)
    }
    return requireNotNull(file.path) { "No settings file" }
}

private const val SETTINGS_FILE = "settings.preferences_pb"
