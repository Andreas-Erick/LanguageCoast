package com.andreaserick.languagecoast.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toJavaLocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

actual fun dayOfWeekName(day: DayOfWeek, narrow: Boolean): String =
    day.toJavaDayOfWeek().getDisplayName(if (narrow) TextStyle.NARROW else TextStyle.FULL, Locale.getDefault())

@Composable
actual fun is24HourClock(): Boolean = DateFormat.is24HourFormat(LocalContext.current)

@Composable
actual fun formatTime(time: LocalTime): String =
    if (is24HourClock()) time.toJavaLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    else time.toJavaLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable
actual fun appVersion(): String {
    val context = LocalContext.current
    return remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
            ?.let { "v$it" } ?: ""
    }
}
