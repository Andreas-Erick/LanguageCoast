package com.andreaserick.languagecoast.ui.create

import androidx.compose.runtime.Composable

/** Reading text from photos isn't built for iPhone yet. */
@Composable
actual fun rememberPhotoTextReader(onText: (String) -> Unit, onError: (String) -> Unit): (() -> Unit)? = null
