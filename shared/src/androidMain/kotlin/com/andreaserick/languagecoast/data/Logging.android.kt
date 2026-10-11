package com.andreaserick.languagecoast.data

import android.util.Log

internal actual fun logError(tag: String, message: String, error: Throwable?) {
    Log.e(tag, message, error)
}
