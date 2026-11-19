package com.andreaserick.languagecoast.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Deterministic unit tests for exponential backoff + jitter HTTP retry policy (Spec 2.2). */
class HttpRetryTest {

    @Test
    fun classifiesOnlyTransientHttpStatusesAsRetryable() {
        listOf(408, 429, 502, 503, 504, 529).forEach { code ->
            assertTrue(isTransientHttpStatus(code), "Expected HTTP $code to be transient/retryable")
        }
        listOf(200, 400, 401, 402, 403, 404, 422, 500).forEach { code ->
            assertFalse(isTransientHttpStatus(code), "Expected HTTP $code NOT to be auto-retried")
        }
    }

    @Test
    fun retriesTransientResponsesWithExponentialBackoffAndJitterThenSucceeds() = runTest {
        val statuses = ArrayDeque(listOf(503, 502, 200))
        val delays = mutableListOf<Long>()

        val result = retryHttpWithBackoff(
            maxRetries = 3,
            initialDelayMillis = 500L,
            maxDelayMillis = 8_000L,
            random = Random(42),
            delayFn = { delays += it },
            shouldRetry = { isTransientHttpStatus(it) }
        ) {
            statuses.removeFirst()
        }

        assertEquals(200, result)
        assertEquals(2, delays.size)
        // Attempt 0: [250, 500], Attempt 1: [500, 1000]
        assertTrue(delays[0] in 250L..501L, "Unexpected first delay: ${delays[0]}")
        assertTrue(delays[1] in 500L..1001L, "Unexpected second delay: ${delays[1]}")
    }

    @Test
    fun neverRetriesDeterministicClientErrors() = runTest {
        var calls = 0
        val delays = mutableListOf<Long>()

        val result = retryHttpWithBackoff(
            maxRetries = 3,
            random = Random(1),
            delayFn = { delays += it },
            shouldRetry = { isTransientHttpStatus(it) }
        ) {
            calls++
            401
        }

        assertEquals(401, result)
        assertEquals(1, calls)
        assertTrue(delays.isEmpty())
    }

    @Test
    fun honorsRetryAfterHeaderCappedAtMaxDelay() = runTest {
        val delays = mutableListOf<Long>()
        var calls = 0

        val result = retryHttpWithBackoff(
            maxRetries = 2,
            maxDelayMillis = 5_000L,
            random = Random(7),
            delayFn = { delays += it },
            retryAfterMillis = { (_, retryAfter) -> parseRetryAfterMillis(retryAfter) },
            shouldRetry = { (code, _) -> isTransientHttpStatus(code) }
        ) {
            calls++
            if (calls == 1) 429 to "2" else 200 to null
        }

        assertEquals(200, result.first)
        assertEquals(1, delays.size)
        assertTrue(delays[0] in 2_000L..2_250L, "Expected Retry-After (~2000ms + jitter), got ${delays[0]}")
    }

    @Test
    fun propagatesCancellationExceptionImmediatelyWithoutRetry() = runTest {
        var calls = 0
        assertFailsWith<CancellationException> {
            retryHttpWithBackoff(
                maxRetries = 3,
                delayFn = {},
                shouldRetry = { true }
            ) {
                calls++
                throw CancellationException("caller cancelled")
            }
        }
        assertEquals(1, calls)
    }
}
