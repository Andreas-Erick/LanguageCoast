package com.andreaserick.languagecoast.di

import org.koin.dsl.module
import java.time.Clock

/** App-wide singletons not in [sharedModule]: the system clock. */
val appModule = module {

    /** The clock used for due dates, streaks and reminders; tests replace it with a fixed one. */
    factory<Clock> { Clock.systemDefaultZone() }
}
