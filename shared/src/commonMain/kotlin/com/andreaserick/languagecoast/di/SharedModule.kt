package com.andreaserick.languagecoast.di

import com.andreaserick.languagecoast.data.AppDatabase
import com.andreaserick.languagecoast.data.BackupManager
import com.andreaserick.languagecoast.data.DataStoreSettingsRepository
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.GeminiTranslator
import com.andreaserick.languagecoast.data.OfflineFlashcardRepository
import com.andreaserick.languagecoast.data.OnDeviceTranslator
import com.andreaserick.languagecoast.data.OpenRouterTranslator
import com.andreaserick.languagecoast.data.RoutingTranslator
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.Translator
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.datetime.TimeZone
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import kotlin.time.Clock

/** The data layer both apps use: cards in Room, settings in DataStore, and translation. Needs [platformModule] for the files. */
val sharedModule = module {

    /** The DAO of the app's database. */
    factory { get<AppDatabase>().languageCoastDao() }

    /** Cards are stored in the Room database. */
    singleOf(::OfflineFlashcardRepository) { bind<FlashcardRepository>() }

    /** Settings are stored in Preferences DataStore. */
    single<SettingsRepository> { DataStoreSettingsRepository(get(), get(), get()) }

    factoryOf(::BackupManager)

    /** The clock used for due dates, streaks and reminders, and the time zone days are counted in; tests fix both. */
    single<Clock> { Clock.System }
    factory { TimeZone.currentSystemDefault() }

    /** Translations go to the provider picked in each request. */
    factoryOf(::RoutingTranslator) { bind<Translator>() }
    singleOf(::OnDeviceTranslator)
    singleOf(::GeminiTranslator)
    singleOf(::OpenRouterTranslator)

    /** For the Gemini and OpenRouter APIs; Ktor picks each platform's own networking. */
    single {
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 60_000
            }
        }
    }
}

/** What each platform does its own way: where the [AppDatabase] and settings are stored, and reading aloud. */
expect val platformModule: Module
