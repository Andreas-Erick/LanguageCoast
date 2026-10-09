package com.andreaserick.languagecoast.data

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The ML Kit language for [language] (an English language name), or null if on-device translation doesn't cover it. */
fun onDeviceLanguage(language: String): String? =
    Languages.byName(language)?.code?.let(TranslateLanguage::fromLanguageTag)

/**
 * [Translator] that runs Google ML Kit on the phone: free, private and offline once the language pack
 * (about 30 MB per language) is downloaded on first use. It only translates, so cards go into the
 * category the user typed, or [DEFAULT_CATEGORY].
 */
@Singleton
class OnDeviceTranslator @Inject constructor() : Translator {

    override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult {
        val source = onDeviceLanguage(request.nativeLanguage)
        val target = onDeviceLanguage(request.targetLanguage)
        if (source == null || target == null) {
            val unsupported = if (source == null) request.nativeLanguage else request.targetLanguage
            return TranslationResult.failure(
                "On-device translation doesn't cover $unsupported. Pick Gemini or OpenRouter in Settings, or type the translation yourself."
            )
        }

        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
        )
        return try {
            try {
                translator.downloadModelIfNeeded().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Language pack download failed", e)
                return TranslationResult.failure(
                    "The ${request.targetLanguage} language pack couldn't be downloaded. Check your internet connection and try again."
                )
            }
            val translated = translator.translate(request.nativeSentence).await()
            TranslationResult(translated, request.userCategory.ifBlank { DEFAULT_CATEGORY }, isSuccess = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "On-device translation failed", e)
            TranslationResult.FAILURE
        } finally {
            translator.close()
        }
    }

    private companion object {
        const val TAG = "OnDeviceTranslator"
    }
}

/** Waits for a Play services [Task] without blocking. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
