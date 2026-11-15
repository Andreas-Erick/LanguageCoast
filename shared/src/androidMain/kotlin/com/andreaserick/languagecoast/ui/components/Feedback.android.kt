package com.andreaserick.languagecoast.ui.components

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Short system tones; the generator is made once, since making one is slow. They play on the system stream,
 * not the media one, so they stay quiet while the phone is on silent or vibrate. On a phone that stream
 * follows the ring volume: the phone's Touch sounds setting does not govern the tones, and the volume keys
 * in the app change the spoken audio, not them. Sounds in Settings is what switches them off.
 */
private val tones: ToneGenerator? by lazy { runCatching { ToneGenerator(AudioManager.STREAM_SYSTEM, 35) }.getOrNull() }

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
