package com.andreaserick.languagecoast.ui.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.Languages
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.isLeaky
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** One coast's share of the month: its flag, name and how many of its cards were reviewed. */
data class CoastRecap(val flag: String, val name: String, val cardsReviewed: Int)

/**
 * What happened on the coasts this month, counted from the first of the month to today.
 *
 * @property daysStudied Days with a completed session this month.
 * @property cardsReviewed Cards graded this month.
 * @property cardsSettled Cards whose next review is more than a month away: the ones that have stuck.
 * @property newIslands Islands started this month.
 */
data class RecapUiState(
    val monthName: String = "",
    val year: Int = 0,
    val dayOfMonth: Int = 0,
    val daysStudied: Int = 0,
    val cardsReviewed: Int = 0,
    val cardsSettled: Int = 0,
    val totalCards: Int = 0,
    val newIslands: Int = 0,
    val leakyCount: Int = 0,
    val streakCount: Int = 0,
    val coasts: List<CoastRecap> = emptyList(),
    val isLoading: Boolean = true
) {
    /** The headline of the recap, e.g. "October 2026". */
    val title: String get() = "$monthName $year"
}

/** The month's recap for the My Coasts screen, from the cards' review dates and the study days. */
class RecapViewModel(
    flashcards: FlashcardRepository,
    settings: SettingsRepository,
    private val clock: Clock,
    private val timeZone: TimeZone
) : ViewModel() {

    val uiState: StateFlow<RecapUiState> = combine(
        flashcards.observeAllCards(),
        flashcards.observeAllIslands(),
        settings.studyDays,
        settings.streakCount
    ) { cards, islands, studyDays, streak ->
        val today = clock.todayIn(timeZone)
        val monthStart = LocalDate(today.year, today.month, 1)
        val monthStartMillis = monthStart.atStartOfDayIn(timeZone).toEpochMilliseconds()
        val now = clock.now().toEpochMilliseconds()
        val reviewed = cards.filter { (it.card.lastReviewed ?: 0L) >= monthStartMillis }
        RecapUiState(
            monthName = today.month.name.lowercase().replaceFirstChar { it.uppercase() },
            year = today.year,
            dayOfMonth = today.day,
            daysStudied = studyDays.count { it >= monthStart && it <= today },
            cardsReviewed = reviewed.size,
            cardsSettled = cards.count { (it.card.due ?: 0L) - now >= SETTLED_MILLIS },
            totalCards = cards.size,
            newIslands = islands.count { it.creationDate >= monthStartMillis },
            leakyCount = cards.count { isLeaky(it.card) },
            streakCount = streak,
            coasts = reviewed.groupBy { it.language }
                .map { (language, coastCards) -> CoastRecap(Languages.byName(language)?.flag ?: "🌍", language, coastCards.size) }
                .sortedByDescending { it.cardsReviewed },
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecapUiState())

    private companion object {
        /** A card due at least this far ahead counts as settled. */
        const val SETTLED_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
