package com.andreaserick.languagecoast.ui.coast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.DeletedContent
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.IslandSummary
import com.andreaserick.languagecoast.data.LanguageIsland
import com.andreaserick.languagecoast.data.Languages
import com.andreaserick.languagecoast.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class CoastUiState(
    val coastName: String = "",
    /** The coast language's name for itself (e.g. "Íslenska"), if it differs from the English name. */
    val nativeName: String? = null,
    val islands: List<IslandSummary> = emptyList(),
    val today: LocalDate = LocalDate.MIN,
    val isLoading: Boolean = true
)

/** Shows the islands of one coast. Opening a coast makes it the one new cards are added to. */
@HiltViewModel
class CoastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository,
    private val clock: Clock
) : ViewModel() {

    // Populated by Navigation from the CoastScreenRoute properties.
    private val coastId: Int = checkNotNull(savedStateHandle["coastId"])
    private val coastName: String = savedStateHandle["coastName"] ?: ""

    val uiState: StateFlow<CoastUiState> =
        combine(flashcards.observeCoast(coastId), flashcards.observeIslandSummaries(coastId, now = clock.millis())) { coast, islands ->
            CoastUiState(
                coastName = coast?.displayName ?: coastName,
                nativeName = coast?.let { Languages.byName(it.language) }?.nativeName?.takeIf { it != coast?.language },
                islands = islands,
                today = LocalDate.now(clock),
                isLoading = false
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoastUiState(coastName = coastName))

    init {
        viewModelScope.launch { settings.setActiveCoastId(coastId) }
    }

    /** Deletes [island] right away; [onDeleted] receives what was removed so it can be restored. */
    fun deleteIsland(island: LanguageIsland, onDeleted: (DeletedContent) -> Unit) {
        viewModelScope.launch { onDeleted(flashcards.deleteIsland(island)) }
    }

    /** Puts back a deleted island. Not tied to this ViewModel's scope, so undo works after leaving the screen. */
    suspend fun restore(content: DeletedContent) = flashcards.restore(content)
}
