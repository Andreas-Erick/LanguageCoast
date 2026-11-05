package com.andreaserick.languagecoast.ui.coast

import androidx.compose.runtime.Composable

/** Practice sheets aren't built for iPhone yet (see NoWorksheetPrinter), so there is nothing to save or open. */
@Composable
actual fun rememberWorksheetFiles(): WorksheetFiles? = null
