package com.andreaserick.languagecoast.data

import android.util.Log

actual fun logError(tag: String, message: String, error: Throwable?) {
    val safeMessage = redactSecrets(message)
    if (error == null) {
        Log.e(tag, safeMessage)
    } else {
        Log.e(tag, "$safeMessage (${safeErrorSummary(error)})")
    }
}

actual fun logInfo(tag: String, message: String) {
    Log.i(tag, redactSecrets(message))
}
