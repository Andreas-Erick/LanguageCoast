package com.andreaserick.languagecoast.di

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.andreaserick.languagecoast.data.AppDatabase
import com.andreaserick.languagecoast.data.buildAppDatabase
import com.andreaserick.languagecoast.data.createSettingsDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {

    /** The database file in the app's databases folder, opened with the phone's own SQLite. */
    single {
        val builder = Room.databaseBuilder<AppDatabase>(androidContext(), AppDatabase.NAME).setDriver(AndroidSQLiteDriver())
        buildAppDatabase(builder, settings = get())
    }

    /** The file earlier versions created with `preferencesDataStoreFile("settings")`, so settings carry over. */
    single { createSettingsDataStore(androidContext().filesDir.resolve("datastore/settings.preferences_pb").absolutePath) }
}
