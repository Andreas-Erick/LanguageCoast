package com.andreaserick.languagecoast.ui.mycoast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.data.CoastSummary
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.availableCoastLanguages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyCoastUiState(
    val coasts: List<CoastSummary> = emptyList(),
    /** Languages a new coast can be started for. */
    val availableLanguages: List<String> = emptyList(),
    val streakCount: Int = 0,
    val isLoading: Boolean = true
)

/** The overview of all coasts (one per language being studied) and the study streak. */
@HiltViewModel
class MyCoastViewModel @Inject constructor(
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<MyCoastUiState> = combine(
        flashcards.observeCoastSummaries(),
        settings.nativeLanguage,
        settings.streakCount
    ) { coasts, nativeLanguage, streak ->
        MyCoastUiState(
            coasts = coasts,
            availableLanguages = availableCoastLanguages(coasts.map { it.coast }, nativeLanguage),
            streakCount = streak,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyCoastUiState())

    init {
        viewModelScope.launch { settings.refreshStreak() }
    }

    /** Starts a coast for [language] and makes it the one new cards are added to. */
    fun addCoast(language: String) {
        viewModelScope.launch {
            val coastId = flashcards.addCoast(language)
            settings.setActiveCoastId(coastId)
        }
    }

    fun deleteCoast(coast: Coast) {
        viewModelScope.launch { flashcards.deleteCoast(coast) }
    }
}
