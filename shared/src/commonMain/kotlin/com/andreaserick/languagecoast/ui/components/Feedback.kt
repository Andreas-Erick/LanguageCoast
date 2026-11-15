package com.andreaserick.languagecoast.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** What just happened, for a buzz and a sound to match. */
enum class FeedbackKind { Tap, Success, Failure }

/** Plays the haptic and sound feedback the user has switched on in Settings. */
fun interface Feedback {
    fun play(kind: FeedbackKind)
}

/** The app's [Feedback]; silent until [LanguageCoastApp] provides one from the settings. */
val LocalFeedback = staticCompositionLocalOf<Feedback> { Feedback {} }

/** Whether Vibration is on in Settings. A sheet or dialog gets the phone's own haptics again, so a control that buzzes by itself asks here. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/** A [Feedback] that buzzes if [hapticsEnabled] and plays a short sound if [soundsEnabled]. */
@Composable
fun rememberFeedback(hapticsEnabled: Boolean, soundsEnabled: Boolean): Feedback {
    val haptics = LocalHapticFeedback.current
    return remember(hapticsEnabled, soundsEnabled, haptics) {
        Feedback { kind ->
            if (hapticsEnabled) {
                haptics.performHapticFeedback(
                    when (kind) {
                        FeedbackKind.Tap -> HapticFeedbackType.ContextClick
                        FeedbackKind.Success -> HapticFeedbackType.Confirm
                        FeedbackKind.Failure -> HapticFeedbackType.Reject
                    }
                )
            }
            // Tones are for right and wrong answers, as Settings says; a flip only buzzes.
            if (soundsEnabled && kind != FeedbackKind.Tap) playFeedbackSound(kind)
        }
    }
}

/** Plays the platform's short sound for [kind]; quietly does nothing if it can't. */
expect fun playFeedbackSound(kind: FeedbackKind)
