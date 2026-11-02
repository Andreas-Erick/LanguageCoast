package com.andreaserick.languagecoast.ui.mycoast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.data.CoastSummary
import com.andreaserick.languagecoast.data.DeletedContent
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.Language
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.availableCoastLanguages
import com.andreaserick.languagecoast.data.isLeaky
import com.andreaserick.languagecoast.data.startOfWeek
import kotlinx.datetime.atStartOfDayIn
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
    /** Islands the user aims to study each week. */
    val weeklyGoal: Int = SettingsDefaults.WEEKLY_GOAL,
    /** Islands with a session since Monday. */
    val islandsThisWeek: Int = 0,
    /** The day this week a streak freeze stood in for, if any. */
    val freezeUsedOn: LocalDate? = null,
    /** Cards the user keeps forgetting (see [isLeaky]). */
    val leakyCount: Int = 0,
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

    /** What the weekly goal and the leaky-word booster need: islands studied since Monday, and the leaky cards. */
    private val progress = combine(
        flashcards.observeAllIslands(),
        flashcards.observeAllCards(),
        settings.weeklyGoal,
        settings.freezeUsedOn
    ) { islands, cards, goal, freezeUsedOn ->
        val today = clock.todayIn(timeZone)
        val weekStart = startOfWeek(today).atStartOfDayIn(timeZone).toEpochMilliseconds()
        Progress(
            weeklyGoal = goal,
            islandsThisWeek = islands.count { (it.lastStudied ?: 0L) >= weekStart },
            freezeUsedOn = freezeUsedOn?.takeIf { it >= startOfWeek(today) },
            leakyCount = cards.count { isLeaky(it.card) }
        )
    }

    private data class Progress(val weeklyGoal: Int, val islandsThisWeek: Int, val freezeUsedOn: LocalDate?, val leakyCount: Int)

    val uiState: StateFlow<MyCoastUiState> = combine(
        flashcards.observeCoastSummaries(studiedSince = clock.now().toEpochMilliseconds() - RECENT_STUDY_WINDOW.inWholeMilliseconds, now = clock.now().toEpochMilliseconds()),
        settings.nativeLanguage,
        settings.streakCount,
        settings.studyDays,
        progress
    ) { coasts, nativeLanguage, streak, studyDays, progress ->
        MyCoastUiState(
            coasts = coasts,
            availableLanguages = availableCoastLanguages(coasts.map { it.coast }, nativeLanguage),
            streakCount = streak,
            studyDays = studyDays,
            today = clock.todayIn(timeZone),
            now = clock.now().toEpochMilliseconds(),
            weeklyGoal = progress.weeklyGoal,
            islandsThisWeek = progress.islandsThisWeek,
            freezeUsedOn = progress.freezeUsedOn,
            leakyCount = progress.leakyCount,
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
