package com.andreaserick.languagecoast.di

import com.andreaserick.languagecoast.ui.coast.CoastViewModel
import com.andreaserick.languagecoast.ui.create.CreateViewModel
import com.andreaserick.languagecoast.ui.island.IslandViewModel
import com.andreaserick.languagecoast.ui.mycoast.MyCoastViewModel
import com.andreaserick.languagecoast.ui.search.SearchViewModel
import com.andreaserick.languagecoast.ui.settings.BackupViewModel
import com.andreaserick.languagecoast.ui.settings.CardTransferViewModel
import com.andreaserick.languagecoast.ui.settings.SettingsViewModel
import com.andreaserick.languagecoast.ui.study.StudyViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Every screen's ViewModel; Koin passes in the navigation arguments' SavedStateHandle where one is asked for. */
val viewModelModule = module {
    viewModelOf(::CoastViewModel)
    viewModelOf(::CreateViewModel)
    viewModelOf(::IslandViewModel)
    viewModelOf(::MyCoastViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::BackupViewModel)
    viewModelOf(::CardTransferViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::StudyViewModel)
}
