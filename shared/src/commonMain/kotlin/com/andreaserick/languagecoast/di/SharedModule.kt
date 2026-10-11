package com.andreaserick.languagecoast.di

import com.andreaserick.languagecoast.data.AppDatabase
import com.andreaserick.languagecoast.data.DataStoreSettingsRepository
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.OfflineFlashcardRepository
import com.andreaserick.languagecoast.data.SettingsRepository
import kotlinx.datetime.TimeZone
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import kotlin.time.Clock

/** The data layer both apps use: cards in Room, settings in DataStore. Needs [platformModule] for the files. */
val sharedModule = module {

    /** The DAO of the app's database. */
    factory { get<AppDatabase>().languageCoastDao() }

    /** Cards are stored in the Room database. */
    singleOf(::OfflineFlashcardRepository) { bind<FlashcardRepository>() }

    /** Settings are stored in Preferences DataStore; days are counted in the phone's time zone. */
    single<SettingsRepository> { DataStoreSettingsRepository(get(), Clock.System, TimeZone.currentSystemDefault()) }
}

/** Where each platform keeps the files: the [AppDatabase] and the settings' DataStore. */
expect val platformModule: Module
