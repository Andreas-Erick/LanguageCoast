package com.andreaserick.languagecoast.ui.create

import androidx.compose.runtime.Composable

/**
 * A way to pick a photo and read the text in it, for capturing sentences from a page or a sign.
 * Returns what to call to open the picker, or null on platforms that can't read text from photos yet.
 * [onText] gets the recognized text; [onError] a message when nothing could be read.
 */
@Composable
expect fun rememberPhotoTextReader(onText: (String) -> Unit, onError: (String) -> Unit): (() -> Unit)?
