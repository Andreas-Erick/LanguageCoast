package com.andreaserick.languagecoast.di

import com.andreaserick.languagecoast.data.BackupManager
import com.andreaserick.languagecoast.data.GeminiTranslator
import com.andreaserick.languagecoast.data.OnDeviceTranslator
import com.andreaserick.languagecoast.data.OpenRouterTranslator
import com.andreaserick.languagecoast.data.RoutingTranslator
import com.andreaserick.languagecoast.data.Translator
import com.andreaserick.languagecoast.notifications.ReminderScheduler
import com.andreaserick.languagecoast.notifications.WorkManagerReminderScheduler
import com.andreaserick.languagecoast.speech.AndroidSpeaker
import com.andreaserick.languagecoast.speech.Speaker
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** Binds the Android-only parts of the data layer to their implementations; the rest is in [sharedModule]. */
val dataModule = module {

    /** Translations go to the provider picked in each request. */
    factoryOf(::RoutingTranslator) { bind<Translator>() }
    singleOf(::OnDeviceTranslator)
    singleOf(::GeminiTranslator)
    singleOf(::OpenRouterTranslator)

    /** Reminders are scheduled with WorkManager; the worker asks for the concrete class to queue the next one. */
    singleOf(::WorkManagerReminderScheduler) { bind<ReminderScheduler>() }

    /** Text is read aloud with Android's text-to-speech engine. */
    singleOf(::AndroidSpeaker) { bind<Speaker>() }

    factoryOf(::BackupManager)
}
