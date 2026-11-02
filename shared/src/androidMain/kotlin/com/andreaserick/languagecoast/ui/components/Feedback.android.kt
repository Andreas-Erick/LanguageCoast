package com.andreaserick.languagecoast.ui.components

import android.media.AudioManager
import android.media.ToneGenerator

/** Short system tones; the generator is made once, since making one is slow. */
private val tones: ToneGenerator? by lazy { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 35) }.getOrNull() }

actual fun playFeedbackSound(kind: FeedbackKind) {
    val generator = tones ?: return
    runCatching {
        when (kind) {
            FeedbackKind.Tap -> generator.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
            FeedbackKind.Success -> generator.startTone(ToneGenerator.TONE_PROP_ACK, 120)
            FeedbackKind.Failure -> generator.startTone(ToneGenerator.TONE_PROP_NACK, 160)
        }
    }
}
