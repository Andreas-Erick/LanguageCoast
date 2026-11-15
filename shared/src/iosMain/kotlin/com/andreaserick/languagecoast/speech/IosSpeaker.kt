package com.andreaserick.languagecoast.speech

import com.andreaserick.languagecoast.data.logError
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeSpokenAudio
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance

/** [Speaker] using iOS's speech synthesizer and the voices on the iPhone: free and offline. */
class IosSpeaker : Speaker {

    private val synthesizer = AVSpeechSynthesizer()

    /** Whether the app's audio session has been set up for speech (see [prepareAudioSession]). */
    private var audioSessionReady = false

    override fun canSpeak(language: String): Flow<Boolean> = flowOf(voice(language) != null)

    override fun speak(text: String, language: String) {
        val voice = voice(language) ?: return
        stop()
        prepareAudioSession()
        val utterance = AVSpeechUtterance(string = text)
        utterance.voice = voice
        synthesizer.speakUtterance(utterance)
    }

    override fun stop() {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }

    /**
     * An app's sound is muted by the ring/silent switch unless it says the sound is what the user asked for.
     * Reading aloud is, so the audio session becomes a playback one the first time something is read. It mixes
     * with other apps' sound, so the user's music keeps playing and nothing has to be handed back afterwards.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun prepareAudioSession() {
        if (audioSessionReady) return
        audioSessionReady = AVAudioSession.sharedInstance().setCategory(
            AVAudioSessionCategoryPlayback,
            mode = AVAudioSessionModeSpokenAudio,
            options = AVAudioSessionCategoryOptionMixWithOthers,
            error = null
        )
        // Speech still plays without it, only not with the switch on silent; the next sentence tries again.
        if (!audioSessionReady) logError(TAG, "The audio session could not be set up for reading aloud")
    }

    private companion object {
        const val TAG = "IosSpeaker"
    }
}

/** The default voice for [language], or null if the iPhone has none. */
private fun voice(language: String): AVSpeechSynthesisVoice? =
    speechLanguageCode(language)?.let { AVSpeechSynthesisVoice.voiceWithLanguage(it) }
