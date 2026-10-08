package com.andreaserick.languagecoast.di

import com.andreaserick.languagecoast.data.DataStoreSettingsRepository
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.GeminiTranslator
import com.andreaserick.languagecoast.data.OfflineFlashcardRepository
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.Translator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the data-layer interfaces to their production implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindFlashcardRepository(impl: OfflineFlashcardRepository): FlashcardRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindTranslator(impl: GeminiTranslator): Translator
}
