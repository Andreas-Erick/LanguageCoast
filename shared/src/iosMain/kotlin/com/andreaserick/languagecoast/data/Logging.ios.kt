package com.andreaserick.languagecoast.data

internal actual fun logError(tag: String, message: String, error: Throwable?) {
    println("E/$tag: $message${error?.let { ": $it" } ?: ""}")
}

internal actual fun logInfo(tag: String, message: String) {
    println("I/$tag: $message")
}
