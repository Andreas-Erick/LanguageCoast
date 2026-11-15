package com.andreaserick.languagecoast.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

/**
 * How many [KeepScreenOn] calls are in composition, so that two of them (the app's and a sheet's, say)
 * don't switch each other off. Only touched on the main thread, where effects run.
 */
private var screenOnHolders = 0

/** iOS has one switch for the whole app: while it is set, the iPhone does not dim or lock by itself. */
@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        screenOnHolders++
        UIApplication.sharedApplication.idleTimerDisabled = true
        onDispose {
            screenOnHolders--
            if (screenOnHolders <= 0) {
                screenOnHolders = 0
                UIApplication.sharedApplication.idleTimerDisabled = false
            }
        }
    }
}
