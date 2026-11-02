package com.andreaserick.languagecoast.ui.recap

import androidx.compose.runtime.Composable

/** Sharing the recap as a picture isn't built for iPhone yet. */
@Composable
actual fun rememberRecapSharer(): ((RecapShare) -> Unit)? = null
