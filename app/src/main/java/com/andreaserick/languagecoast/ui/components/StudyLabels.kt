package com.andreaserick.languagecoast.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** "Studied today", "Studied yesterday", "Last studied 3 days ago" or "Not studied yet". */
fun lastStudiedLabel(lastStudied: Long?, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
    if (lastStudied == null) return "Not studied yet"
    val day = Instant.ofEpochMilli(lastStudied).atZone(zone).toLocalDate()
    return when (val days = ChronoUnit.DAYS.between(day, today)) {
        0L -> "Studied today"
        1L -> "Studied yesterday"
        else -> "Last studied $days days ago"
    }
}

/** "1 island", "3 cards" etc. */
fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"
