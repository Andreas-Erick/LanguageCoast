package com.andreaserick.languagecoast.ui.components

import androidx.compose.runtime.Composable

/**
 * While this is in composition, the screen does not dim or lock. For work that takes minutes and stops
 * when the phone sleeps, such as writing a set of islands; the screen goes back to normal when it leaves.
 */
@Composable
expect fun KeepScreenOn()
