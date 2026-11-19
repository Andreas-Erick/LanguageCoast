package com.andreaserick.languagecoast.di

import com.andreaserick.languagecoast.data.AppDatabase
import com.andreaserick.languagecoast.data.BackupManager
import com.andreaserick.languagecoast.data.DataStoreSettingsRepository
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.GeminiTranslator
import com.andreaserick.languagecoast.data.LocalAiTranslator
import com.andreaserick.languagecoast.data.ModelLister
import com.andreaserick.languagecoast.data.MultiCloudTranslator
import com.andreaserick.languagecoast.data.ProviderModelLister
import com.andreaserick.languagecoast.data.OfflineFlashcardRepository
import com.andreaserick.languagecoast.data.OnDeviceTranslator
import com.andreaserick.languagecoast.data.OpenRouterTranslator
import com.andreaserick.languagecoast.data.PersonalIslandBuilder
import com.andreaserick.languagecoast.data.RoutingTranslator
import com.andreaserick.languagecoast.data.SentenceSuggester
import com.andreaserick.languagecoast.data.StarterImporter
import com.andreaserick.languagecoast.data.StarterTranslator
import com.andreaserick.languagecoast.data.SuggestionPool
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.Translator
import com.andreaserick.languagecoast.data.WorksheetGenerator
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

    /** Settings are stored in Preferences DataStore; API keys are kept in hardware-backed [SecretStore]. */
    single<SettingsRepository> { DataStoreSettingsRepository(get(), get(), get(), get()) }

    factoryOf(::BackupManager)

    /** Sentences for the daily session, kept ready in the background, and the starter bank's translation and import. */
    factoryOf(::StarterTranslator)
    factoryOf(::SentenceSuggester)
    single { SuggestionPool(get(), get(), get(), get()) }
    single { StarterImporter(get(), get(), get()) }

    /** Writes the islands about the user from what they answered under About you. */
    single { PersonalIslandBuilder(get(), get(), get(), get()) }

    /** Writes a coast's practice sheet with the AI provider; each platform draws the PDF (see [platformModule]). */
    factory { WorksheetGenerator(get(), get(), get(), get(), get()) }

    /** The clock used for due dates, streaks and reminders, and the time zone days are counted in; tests fix both. */
    single<Clock> { Clock.System }
    factory { TimeZone.currentSystemDefault() }

    /** Translations go to the provider picked in each request. */
    factoryOf(::RoutingTranslator) { bind<Translator>() }
    singleOf(::OnDeviceTranslator)
    singleOf(::LocalAiTranslator)
    singleOf(::GeminiTranslator)
    singleOf(::OpenRouterTranslator)
    singleOf(::MultiCloudTranslator)

    /** Asks a cloud provider which models it offers, for "Check for new models" in Settings. */
    single<ModelLister> { ProviderModelLister(get()) }

    /**
     * For the cloud AI APIs; Ktor picks each platform's own networking. No connection shows within seconds;
     * an answer gets two minutes, since a model that thinks first can take longer than one over a batch of sentences.
     */
    single {
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 120_000
                // Silence from the provider. Above the request timeout, so that one ends a slow answer on both
                // engines; unset, iPhone gives up after 60 s without a byte and Android after 100 s.
                socketTimeoutMillis = 125_000
            }
        }
    }
}

/** What each platform does its own way: where the [AppDatabase] and settings are stored, reading aloud, and drawing PDFs. */
expect val platformModule: Module
