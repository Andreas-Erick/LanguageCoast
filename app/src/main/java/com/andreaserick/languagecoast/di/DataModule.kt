package com.andreaserick.languagecoast.di

import com.andreaserick.languagecoast.notifications.ReminderScheduler
import com.andreaserick.languagecoast.notifications.WorkManagerReminderScheduler
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** Binds the Android-only parts of the data layer to their implementations; the rest is in [sharedModule]. */
val dataModule = module {

    /** Reminders are scheduled with WorkManager; the worker asks for the concrete class to queue the next one. */
    singleOf(::WorkManagerReminderScheduler) { bind<ReminderScheduler>() }
}
