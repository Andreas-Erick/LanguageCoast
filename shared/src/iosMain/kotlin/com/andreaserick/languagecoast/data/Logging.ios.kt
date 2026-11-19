package com.andreaserick.languagecoast.data

import platform.Foundation.NSLog

internal actual fun logError(tag: String, message: String, error: Throwable?) {
    val safeMessage = redactSecrets(message)
    val suffix = error?.let { " (${safeErrorSummary(it)})" }.orEmpty()
    NSLog("E/%@: %@%@", tag, safeMessage, suffix)
}

internal actual fun logInfo(tag: String, message: String) {
    NSLog("I/%@: %@", tag, redactSecrets(message))
}
