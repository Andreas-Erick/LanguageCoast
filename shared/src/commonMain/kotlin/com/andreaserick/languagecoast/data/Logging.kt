package com.andreaserick.languagecoast.data

/** Writes an error to the platform's log (Logcat on Android, the console on iOS). */
internal expect fun logError(tag: String, message: String, error: Throwable? = null)

/** Writes a note worth seeing in the platform's log, such as how long a local model took. */
internal expect fun logInfo(tag: String, message: String)
