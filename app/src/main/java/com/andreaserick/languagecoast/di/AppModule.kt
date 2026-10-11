package com.andreaserick.languagecoast.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.andreaserick.languagecoast.data.AppDatabase
import com.andreaserick.languagecoast.data.readLegacyTargetLanguage
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.time.Clock

/** App-wide singletons: the Room database, DataStore and the system clock. */
val appModule = module {

    /** The Room database, with every migration from earlier schema versions. */
    single {
        val dataStore: DataStore<Preferences> = get()
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                // Migrations run on Room's background thread, so blocking on DataStore here is safe.
                AppDatabase.migration2To3 { runBlocking { dataStore.readLegacyTargetLanguage() } },
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6
            )
            .build()
    }

    /** The DAO of the app's database. */
    factory { get<AppDatabase>().languageCoastDao() }

    /** The DataStore holding the user's settings. */
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create { androidContext().preferencesDataStoreFile("settings") }
    }

    /** The clock used for due dates, streaks and reminders; tests replace it with a fixed one. */
    factory<Clock> { Clock.systemDefaultZone() }
}
