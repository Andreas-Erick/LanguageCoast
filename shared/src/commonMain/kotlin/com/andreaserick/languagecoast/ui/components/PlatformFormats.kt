package com.andreaserick.languagecoast.ui.components

import androidx.compose.runtime.Composable
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

// Dates, times and the app version, as each platform shows them.

/** The name of [day] in the phone's language: "Monday", or "M" when [narrow]. */
expect fun dayOfWeekName(day: DayOfWeek, narrow: Boolean): String

/** Whether the phone shows times on a 24-hour clock. */
@Composable
expect fun is24HourClock(): Boolean

/** [time] as the phone shows times: 24-hour or with AM/PM, as the user prefers. */
@Composable
expect fun formatTime(time: LocalTime): String

/** The installed version name, like "v1.2", or an empty string if it can't be read. */
@Composable
expect fun appVersion(): String
