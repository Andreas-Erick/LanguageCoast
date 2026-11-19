package com.andreaserick.languagecoast.data

import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual val onDeviceTranslationAvailable = true

/** The ML Kit language for [language]. */
actual fun onDeviceLanguage(language: String): String? =
    Languages.byName(language)?.code?.let(TranslateLanguage::fromLanguageTag)

/**
 * [Translator] that runs Google ML Kit on the phone: free, private and offline once the language pack
 * (about 30 MB per language) is downloaded on first use. It only translates, so cards go on the island
 * the user typed, or [DEFAULT_CATEGORY].
 */
actual class OnDeviceTranslator actual constructor() : Translator {

    actual override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult {
        val source = onDeviceLanguage(request.nativeLanguage)
        val target = onDeviceLanguage(request.targetLanguage)
        if (source == null || target == null) {
            val unsupported = if (source == null) request.nativeLanguage else request.targetLanguage
            return TranslationResult.failure(
                "On-device translation doesn't cover $unsupported. Pick an AI provider in Settings, or type the translation yourself."
            )
        }

        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
        )
        return try {
            try {
                // Without a connection the download neither finishes nor fails, and whoever asked would wait
                // for good. After this long they are told instead; the download itself carries on, so trying
                // again later finds the pack there.
                val downloaded = withTimeoutOrNull(LANGUAGE_PACK_WAIT_MILLIS) {
                    translator.downloadModelIfNeeded().await()
                    true
                }
                if (downloaded == null) {
                    return TranslationResult.failure(
                        "A language pack is still downloading. Check your internet connection and try again in a moment."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError(TAG, "Language pack download failed", e)
                return TranslationResult.failure(
                    "A language pack couldn't be downloaded. Check your internet connection and try again."
                )
            }
            val translated = withTimeoutOrNull(TRANSLATE_TIMEOUT_MILLIS) {
                translator.translate(request.nativeSentence).await()
            } ?: return TranslationResult.failure("On-device translation took too long. Try again.")
            TranslationResult(translated, request.userCategory.ifBlank { DEFAULT_CATEGORY }, isSuccess = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(TAG, "On-device translation failed", e)
            TranslationResult.failure("The translation didn't work. Try again.")
        } finally {
            translator.close()
        }
    }

    private companion object {
        const val TAG = "OnDeviceTranslator"

        /** A language pack is about 30 MB: seconds on Wi-Fi, a minute or so on a slow connection. */
        const val LANGUAGE_PACK_WAIT_MILLIS = 90_000L

        /** Guard against hung Play Services translation IPC. */
        const val TRANSLATE_TIMEOUT_MILLIS = 30_000L
    }
}

/** Waits for a Play services [Task] without blocking. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
