package com.andreaserick.languagecoast.ui.components

import platform.AudioToolbox.AudioServicesPlaySystemSound

/** iOS system sounds: a key click, the "sent" tink and a soft error tone. */
actual fun playFeedbackSound(kind: FeedbackKind) {
    val soundId: UInt = when (kind) {
        FeedbackKind.Tap -> 1104u
        FeedbackKind.Success -> 1057u
        FeedbackKind.Failure -> 1053u
    }
    AudioServicesPlaySystemSound(soundId)
}
