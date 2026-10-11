package com.andreaserick.languagecoast.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import platform.Foundation.NSBundle
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSCalendar
import platform.Foundation.currentCalendar
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

actual fun dayOfWeekName(day: DayOfWeek, narrow: Boolean): String {
    val formatter = NSDateFormatter().apply { locale = NSLocale.currentLocale }
    val names = if (narrow) formatter.veryShortStandaloneWeekdaySymbols else formatter.standaloneWeekdaySymbols
    // iOS lists the days from Sunday; ISO numbers them from Monday (1) to Sunday (7).
    return names[day.isoDayNumber % 7] as String
}

@Composable
actual fun is24HourClock(): Boolean = remember {
    // The phone's time pattern only contains "a" (AM/PM) on a 12-hour clock.
    NSDateFormatter.dateFormatFromTemplate("j", 0u, NSLocale.currentLocale)?.contains("a") != true
}

@Composable
actual fun formatTime(time: LocalTime): String = remember(time) {
    val components = NSDateComponents().apply {
        hour = time.hour.toLong()
        minute = time.minute.toLong()
    }
    val date = NSCalendar.currentCalendar.dateFromComponents(components)
    val formatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterNoStyle
        timeStyle = NSDateFormatterShortStyle
    }
    date?.let { formatter.stringFromDate(it) } ?: time.toString()
}

@Composable
actual fun appVersion(): String = remember {
    (NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String)?.let { "v$it" } ?: ""
}
