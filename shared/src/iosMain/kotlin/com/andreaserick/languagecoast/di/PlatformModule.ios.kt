package com.andreaserick.languagecoast.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.andreaserick.languagecoast.data.AppDatabase
import com.andreaserick.languagecoast.data.buildAppDatabase
import com.andreaserick.languagecoast.data.createSettingsDataStore
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual val platformModule = module {

    /** The database file in the app's Documents folder, opened with the SQLite bundled into the app. */
    single {
        val builder = Room.databaseBuilder<AppDatabase>(name = "${documentsPath()}/${AppDatabase.NAME}").setDriver(BundledSQLiteDriver())
        buildAppDatabase(builder, settings = get())
    }

    single { createSettingsDataStore("${documentsPath()}/settings.preferences_pb") }
}

/** The app's Documents folder, which iOS keeps across updates and includes in backups. */
@OptIn(ExperimentalForeignApi::class)
private fun documentsPath(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, false, null)
    return requireNotNull(url?.path) { "No Documents folder" }
}
