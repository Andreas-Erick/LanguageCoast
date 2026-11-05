package com.andreaserick.languagecoast.di

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.andreaserick.languagecoast.data.AndroidLocalModelController
import com.andreaserick.languagecoast.data.AndroidWorksheetPrinter
import com.andreaserick.languagecoast.data.AppDatabase
import com.andreaserick.languagecoast.data.LocalModelController
import com.andreaserick.languagecoast.data.WorksheetPrinter
import com.andreaserick.languagecoast.data.buildAppDatabase
import com.andreaserick.languagecoast.data.createSettingsDataStore
import com.andreaserick.languagecoast.speech.AndroidSpeaker
import com.andreaserick.languagecoast.speech.Speaker
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual val platformModule = module {

    /** The database file in the app's databases folder, opened with the phone's own SQLite. */
    single {
        val builder = Room.databaseBuilder<AppDatabase>(androidContext(), AppDatabase.NAME).setDriver(AndroidSQLiteDriver())
        buildAppDatabase(builder, settings = get())
    }

    /** The file earlier versions created with `preferencesDataStoreFile("settings")`, so settings carry over. */
    single { createSettingsDataStore(androidContext().filesDir.resolve("datastore/settings.preferences_pb").absolutePath) }

    /** Text is read aloud with Android's text-to-speech engine. */
    singleOf(::AndroidSpeaker) { bind<Speaker>() }

    /** Practice sheets are drawn with Android's PDF writer. */
    singleOf(::AndroidWorksheetPrinter) { bind<WorksheetPrinter>() }

    /**
     * Scans the phone's hardware and downloads/manages local `.litertlm` AI models. Created at start, not when
     * Settings first asks for it: it tells the Local AI engine where the models are, and without that a
     * translation after a cold start finds "no model installed".
     */
    singleOf(::AndroidLocalModelController) {
        bind<LocalModelController>()
        createdAtStart()
    }
}
