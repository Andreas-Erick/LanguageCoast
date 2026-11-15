package com.andreaserick.languagecoast.ui.components

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.util.WeakHashMap

/**
 * How many [KeepScreenOn] calls are in composition per window's view, so that two of them (the app's and a
 * sheet's, say) don't switch each other off. Only touched on the main thread, where effects run.
 */
private val holdersByView = WeakHashMap<View, Int>()

@Composable
actual fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        holdersByView[view] = (holdersByView[view] ?: 0) + 1
        view.keepScreenOn = true
        onDispose {
            val left = (holdersByView[view] ?: 1) - 1
            if (left <= 0) {
                holdersByView.remove(view)
                view.keepScreenOn = false
            } else {
                holdersByView[view] = left
            }
        }
    }
}
