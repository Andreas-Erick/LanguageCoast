package com.andreaserick.languagecoast.di

import com.andreaserick.languagecoast.data.DataStoreSettingsRepository
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.OfflineFlashcardRepository
import com.andreaserick.languagecoast.data.RoutingTranslator
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.Translator
import com.andreaserick.languagecoast.notifications.ReminderScheduler
import com.andreaserick.languagecoast.notifications.WorkManagerReminderScheduler
import com.andreaserick.languagecoast.speech.AndroidSpeaker
import com.andreaserick.languagecoast.speech.Speaker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the data-layer interfaces to their production implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    /** Cards are stored in the Room database. */
    @Binds
    @Singleton
    abstract fun bindFlashcardRepository(impl: OfflineFlashcardRepository): FlashcardRepository

    /** Settings are stored in Preferences DataStore. */
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    /** Translations go to the provider picked in each request. */
    @Binds
    abstract fun bindTranslator(impl: RoutingTranslator): Translator

    /** Reminders are scheduled with WorkManager. */
    @Binds
    abstract fun bindReminderScheduler(impl: WorkManagerReminderScheduler): ReminderScheduler

    /** Text is read aloud with Android's text-to-speech engine. */
    @Binds
    abstract fun bindSpeaker(impl: AndroidSpeaker): Speaker
}
