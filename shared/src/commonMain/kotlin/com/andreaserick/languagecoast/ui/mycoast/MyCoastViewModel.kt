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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

data class MyCoastUiState(
    val coasts: List<CoastSummary> = emptyList(),
    /** Languages a new coast can be started for. */
    val availableLanguages: List<Language> = emptyList(),
    val streakCount: Int = 0,
    /** Days with a completed study session, for the week overview. */
    val studyDays: Set<LocalDate> = emptySet(),
    /** Null until loaded. */
    val today: LocalDate? = null,
    /** Current time in epoch millis, for "last studied" labels. */
    val now: Long = 0L,
    val isLoading: Boolean = true
)

/** How far back an island's last session counts towards a coast's weekly progress. */
val RECENT_STUDY_WINDOW: Duration = 7.days

/** The overview of all coasts (one per language being studied) and the study streak. */
class MyCoastViewModel(
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val timeZone: TimeZone
) : ViewModel() {

    val uiState: StateFlow<MyCoastUiState> = combine(
        flashcards.observeCoastSummaries(studiedSince = clock.now().toEpochMilliseconds() - RECENT_STUDY_WINDOW.inWholeMilliseconds, now = clock.now().toEpochMilliseconds()),
        settings.nativeLanguage,
        settings.streakCount,
        settings.studyDays
    ) { coasts, nativeLanguage, streak, studyDays ->
        MyCoastUiState(
            coasts = coasts,
            availableLanguages = availableCoastLanguages(coasts.map { it.coast }, nativeLanguage),
            streakCount = streak,
            studyDays = studyDays,
            today = clock.todayIn(timeZone),
            now = clock.now().toEpochMilliseconds(),
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
