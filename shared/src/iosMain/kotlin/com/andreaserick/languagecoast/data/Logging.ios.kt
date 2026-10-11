package com.andreaserick.languagecoast.data

internal actual fun logError(tag: String, message: String, error: Throwable?) {
    println("E/$tag: $message${error?.let { ": $it" } ?: ""}")
}
