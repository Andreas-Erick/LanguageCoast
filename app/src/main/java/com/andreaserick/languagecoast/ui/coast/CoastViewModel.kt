package com.andreaserick.languagecoast.ui.coast

import androidx.lifecycle.SavedStateHandle
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

data class CoastUiState(
    val coastName: String = "",
    val islands: List<LanguageIsland> = emptyList(),
    val isLoading: Boolean = true
)

/** Shows the islands of one coast. Opening a coast makes it the one new cards are added to. */
@HiltViewModel
class CoastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    // Populated by Navigation from the CoastScreenRoute properties.
    private val coastId: Int = checkNotNull(savedStateHandle["coastId"])
    private val coastName: String = savedStateHandle["coastName"] ?: ""

    val uiState: StateFlow<CoastUiState> =
        combine(flashcards.observeCoast(coastId), flashcards.observeIslands(coastId)) { coast, islands ->
            CoastUiState(coastName = coast?.displayName ?: coastName, islands = islands, isLoading = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoastUiState(coastName = coastName))

    init {
        viewModelScope.launch { settings.setActiveCoastId(coastId) }
    }

    fun deleteIsland(island: LanguageIsland) {
        viewModelScope.launch { flashcards.deleteIsland(island) }
    }
}
