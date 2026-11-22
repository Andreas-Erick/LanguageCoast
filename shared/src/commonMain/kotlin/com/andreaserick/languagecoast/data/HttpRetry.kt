package com.andreaserick.languagecoast.data

import kotlinx.coroutines.delay
import kotlin.random.Random

/** HTTP status codes that represent transient upstream failures safe to retry with backoff. */
internal val TRANSIENT_HTTP_STATUSES = setOf(408, 429, 502, 503, 504, 529)

/** Returns true iff [status] is a transient error (408, 429, 502, 503, 504, 529) and never a 4xx client error like 400/401/403/404/422. */
fun isTransientHttpStatus(status: Int): Boolean = status in TRANSIENT_HTTP_STATUSES

/**
 * Computes exponential backoff delay with 50%–100% jitter for zero-based retry [attempt], capped at [maxDelayMillis].
 * If [retryAfterMillis] is provided and positive, honors it up to [maxDelayMillis] plus minor jitter so callers
 * do not synchronize retries into a thundering herd.
 */
fun computeBackoffDelayMillis(
    attempt: Int,
    initialDelayMillis: Long = 500L,
    maxDelayMillis: Long = 8_000L,
    retryAfterMillis: Long? = null,
    random: Random = Random.Default
): Long {
    val safeAttempt = attempt.coerceIn(0, 10)
    val safeInitial = initialDelayMillis.coerceAtLeast(1L)
    val safeMax = maxDelayMillis.coerceAtLeast(safeInitial)
    if (retryAfterMillis != null && retryAfterMillis > 0L) {
        val jitter = random.nextLong(0L, 250L)
        return (retryAfterMillis + jitter).coerceAtMost(safeMax)
    }
    val exponential = safeInitial * (1L shl safeAttempt)
    val capped = exponential.coerceAtMost(safeMax)
    val half = (capped / 2L).coerceAtLeast(1L)
    return half + random.nextLong(0L, (capped - half).coerceAtLeast(1L) + 1L)
}

/**
 * Runs [block] and retries up to [maxRetries] times with exponential backoff and jitter whenever
 * [shouldRetry] returns true. Never retries on [kotlinx.coroutines.CancellationException].
 */
internal suspend fun <T> retryHttpWithBackoff(
    maxRetries: Int = 2,
    initialDelayMillis: Long = 500L,
    maxDelayMillis: Long = 8_000L,
    random: Random = Random.Default,
    delayFn: suspend (Long) -> Unit = { delay(it) },
    retryAfterMillis: (T) -> Long? = { null },
    shouldRetry: (T) -> Boolean,
    block: suspend () -> T
): T {
    var result = block()
    var attempt = 0
    while (attempt < maxRetries && shouldRetry(result)) {
        val waitMillis = computeBackoffDelayMillis(
            attempt = attempt,
            initialDelayMillis = initialDelayMillis,
            maxDelayMillis = maxDelayMillis,
            retryAfterMillis = retryAfterMillis(result),
            random = random
        )
        delayFn(waitMillis)
        attempt++
        result = block()
    }
    return result
}

/** Parses a `retry-after` header value in seconds into milliseconds, or null if absent/invalid. */
internal fun parseRetryAfterMillis(header: String?): Long? {
    val seconds = header?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
    return (seconds * 1_000.0).toLong().coerceAtLeast(1_000L)
}

/**
 * How long to wait after a rate limit that doesn't say: the providers count requests per minute, so a
 * short wait is usually enough.
 */
const val BUSY_RETRY_SECONDS = 10

/**
 * The seconds a provider's `retry-after` header asks for, rounded up and at least one, or [BUSY_RETRY_SECONDS]
 * when [header] is missing or isn't a number.
 */
internal fun retryAfterHeaderSeconds(header: String?): Int =
    (header?.trim()?.toDoubleOrNull()?.takeIf { it >= 0 }?.let { (it + 0.999).toInt() } ?: BUSY_RETRY_SECONDS).coerceAtLeast(1)
