package com.andreaserick.languagecoast.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Locale

/**
 * [Speaker] using Android's text-to-speech engine and the voices installed on the phone: free and offline.
 * The engine is started on first use and kept for the life of the app.
 */
class AndroidSpeaker(private val context: Context) : Speaker {

    private val ready = MutableStateFlow(false)

    private val tts: TextToSpeech by lazy {
        TextToSpeech(context) { status -> ready.value = status == TextToSpeech.SUCCESS }
    }

    override fun canSpeak(language: String): Flow<Boolean> {
        val locale = speechLocale(language) ?: return flowOf(false)
        tts // Starts the engine, which reports through [ready].
        return ready.map { isReady -> isReady && tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE }
    }

    override fun speak(text: String, language: String) {
        val locale = speechLocale(language) ?: return
        if (!ready.value) return
        tts.language = locale
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    override fun stop() {
        if (ready.value) tts.stop()
    }

    private companion object {
        const val UTTERANCE_ID = "card"
    }
}

/** The locale voices use for [language], or null for a language the app doesn't know. */
internal fun speechLocale(language: String): Locale? = speechLanguageCode(language)?.let(Locale::forLanguageTag)
