package com.andreaserick.languagecoast.speech

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance

/** [Speaker] using iOS's speech synthesizer and the voices on the iPhone: free and offline. */
class IosSpeaker : Speaker {

    private val synthesizer = AVSpeechSynthesizer()

    override fun canSpeak(language: String): Flow<Boolean> = flowOf(voice(language) != null)

    override fun speak(text: String, language: String) {
        val voice = voice(language) ?: return
        stop()
        val utterance = AVSpeechUtterance(string = text)
        utterance.voice = voice
        synthesizer.speakUtterance(utterance)
    }

    override fun stop() {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }
}

/** The default voice for [language], or null if the iPhone has none. */
private fun voice(language: String): AVSpeechSynthesisVoice? =
    speechLanguageCode(language)?.let { AVSpeechSynthesisVoice.voiceWithLanguage(it) }
