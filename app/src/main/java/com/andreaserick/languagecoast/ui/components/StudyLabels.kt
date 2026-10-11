package com.andreaserick.languagecoast.ui.components

import com.andreaserick.languagecoast.data.Flashcard
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** "Studied today", "Studied yesterday", "Last studied 3 days ago" or "Not studied yet". */
fun lastStudiedLabel(lastStudied: Long?, today: LocalDate, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    if (lastStudied == null) return "Not studied yet"
    val day = Instant.fromEpochMilliseconds(lastStudied).toLocalDateTime(zone).date
    return when (val days = day.daysUntil(today)) {
        0 -> "Studied today"
        1 -> "Studied yesterday"
        else -> "Last studied $days days ago"
    }
}

/** "New", "Due now", "Due later today", "Due tomorrow" or "Due in 5 days": when [card] is next studied, seen at [now]. */
fun dueLabel(card: Flashcard, now: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val due = card.due ?: return "New"
    if (due <= now) return "Due now"
    val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
    return when (val days = today.daysUntil(Instant.fromEpochMilliseconds(due).toLocalDateTime(zone).date)) {
        0 -> "Due later today"
        1 -> "Due tomorrow"
        else -> "Due in $days days"
    }
}

/** "12 cards", or "12 cards (3 due)" when some of them are due. */
fun cardCountLabel(cardCount: Int, dueCount: Int) =
    if (dueCount == 0) plural(cardCount, "card") else "${plural(cardCount, "card")} ($dueCount due)"

/** "1 island", "3 cards" etc. */
fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"
