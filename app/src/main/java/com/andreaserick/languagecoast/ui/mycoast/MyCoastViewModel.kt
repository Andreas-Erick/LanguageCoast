package com.andreaserick.languagecoast.ui.mycoast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.data.CoastSummary
import com.andreaserick.languagecoast.data.DeletedContent
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.Language
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.availableCoastLanguages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

data class MyCoastUiState(
    val coasts: List<CoastSummary> = emptyList(),
    /** Languages a new coast can be started for. */
    val availableLanguages: List<Language> = emptyList(),
    val streakCount: Int = 0,
    /** Days with a completed study session, for the week overview. */
    val studyDays: Set<LocalDate> = emptySet(),
    val today: LocalDate = LocalDate.MIN,
    /** Current time in epoch millis, for "last studied" labels. */
    val now: Long = 0L,
    val isLoading: Boolean = true
)

/** How far back an island's last session counts towards a coast's weekly progress. */
val RECENT_STUDY_WINDOW: Duration = Duration.ofDays(7)

/** The overview of all coasts (one per language being studied) and the study streak. */
@HiltViewModel
class MyCoastViewModel @Inject constructor(
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository,
    private val clock: Clock
) : ViewModel() {

    val uiState: StateFlow<MyCoastUiState> = combine(
        flashcards.observeCoastSummaries(studiedSince = clock.millis() - RECENT_STUDY_WINDOW.toMillis(), now = clock.millis()),
        settings.nativeLanguage,
        settings.streakCount,
        settings.studyDays
    ) { coasts, nativeLanguage, streak, studyDays ->
        MyCoastUiState(
            coasts = coasts,
            availableLanguages = availableCoastLanguages(coasts.map { it.coast }, nativeLanguage),
            streakCount = streak,
            studyDays = studyDays,
            today = LocalDate.now(clock),
            now = clock.millis(),
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

    /** Deletes [coast] right away; [onDeleted] receives what was removed so it can be restored. */
    fun deleteCoast(coast: Coast, onDeleted: (DeletedContent) -> Unit) {
        viewModelScope.launch { onDeleted(flashcards.deleteCoast(coast)) }
    }

    /** Puts back a deleted coast. Not tied to this ViewModel's scope, so undo works after leaving the screen. */
    suspend fun restore(content: DeletedContent) = flashcards.restore(content)
}
