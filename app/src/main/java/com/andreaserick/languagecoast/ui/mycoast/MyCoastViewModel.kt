package com.andreaserick.languagecoast.ui.mycoast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.LanguageIsland
import com.andreaserick.languagecoast.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyCoastUiState(
    val islands: List<LanguageIsland> = emptyList(),
    val streakCount: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class MyCoastViewModel @Inject constructor(
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<MyCoastUiState> =
        combine(flashcards.observeIslands(), settings.streakCount) { islands, streak ->
            MyCoastUiState(islands = islands, streakCount = streak, isLoading = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyCoastUiState())

    init {
        viewModelScope.launch { settings.refreshStreak() }
    }

    fun deleteIsland(island: LanguageIsland) {
        viewModelScope.launch { flashcards.deleteIsland(island) }
    }
}
