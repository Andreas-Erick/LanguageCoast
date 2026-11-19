package com.andreaserick.languagecoast.ui.components

import androidx.compose.runtime.Composable

/** Intercepts the hardware/gesture Back button on Android; a no-op on iOS. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
