package com.andreaserick.languagecoast.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.andreaserick.languagecoast.data.AppDatabase
import com.andreaserick.languagecoast.data.LanguageCoastDao
import com.andreaserick.languagecoast.data.readLegacyTargetLanguage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import java.time.Clock
import javax.inject.Singleton

/** Provides app-wide singletons: the Room database, DataStore and the system clock. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context, dataStore: DataStore<Preferences>): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                // Migrations run on Room's background thread, so blocking on DataStore here is safe.
                AppDatabase.migration2To3 { runBlocking { dataStore.readLegacyTargetLanguage() } }
            )
            .build()

    @Provides
    fun provideDao(database: AppDatabase): LanguageCoastDao = database.languageCoastDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
