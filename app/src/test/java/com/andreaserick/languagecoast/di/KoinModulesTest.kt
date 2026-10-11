package com.andreaserick.languagecoast.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

class KoinModulesTest {

    /** Koin resolves dependencies at runtime, so check here that every constructor parameter has a definition. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun everyDependencyIsDefined() {
        module { includes(sharedModule, platformModule, appModule, dataModule, viewModelModule) }
            // Provided by Android: the context by startKoin, the SavedStateHandle by the navigation entry.
            .verify(extraTypes = listOf(Context::class, SavedStateHandle::class))
    }
}
