package com.andreaserick.languagecoast

import android.app.Application
import com.andreaserick.languagecoast.di.appModule
import com.andreaserick.languagecoast.di.dataModule
import com.andreaserick.languagecoast.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LanguageCoastApplication : Application() {

    /** Starts dependency injection before any activity, widget or worker asks for a dependency. */
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@LanguageCoastApplication)
            modules(appModule, dataModule, viewModelModule)
        }
    }
}
