package com.andreaserick.languagecoast.data

/** Writes an error to the platform's log (Logcat on Android, system log on iOS) after scrubbing secrets. */
internal expect fun logError(tag: String, message: String, error: Throwable? = null)

/** Writes a note worth seeing in the platform's log, after scrubbing secrets. */
internal expect fun logInfo(tag: String, message: String)

private val BEARER_TOKEN = Regex("""(?i)\b(Bearer\s+)[^\s"',;]+""")
private val HEADER_SECRET = Regex("""(?i)\b(x-(?:goog-)?api-key|authorization)(\s*[:=]\s*)[^\s"',;]+""")
private val QUERY_KEY = Regex("""(?i)([?&](?:key|api_key|token)=)[^&\s"']+""")
private val KNOWN_API_KEYS = Regex("""\b(?:AIza[0-9A-Za-z_-]{16,}|sk-(?:ant-|or-|proj-)?[0-9A-Za-z_-]{12,}|gsk_[0-9A-Za-z_-]{12,}|hf_[0-9A-Za-z_-]{12,})\b""")

/** Scrubs API keys, Bearer headers, and access tokens from diagnostic messages before logging. */
fun redactSecrets(text: String): String {
    if (text.isEmpty()) return text
    return text
        .replace(BEARER_TOKEN, "$1[REDACTED]")
        .replace(HEADER_SECRET, "$1$2[REDACTED]")
        .replace(QUERY_KEY, "$1[REDACTED]")
        .replace(KNOWN_API_KEYS, "[REDACTED]")
}

/** Formats [error] safely for logs: class name and secret-scrubbed message without leaking raw secret headers. */
internal fun safeErrorSummary(error: Throwable): String {
    val name = error::class.simpleName ?: "Throwable"
    val detail = error.message?.let(::redactSecrets)?.take(300)
    return if (detail.isNullOrBlank()) name else "$name: $detail"
}
