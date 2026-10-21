package com.andreaserick.languagecoast.ui.settings

/** Whether this platform can import, export, back up and restore files; iPhone can't yet. */
expect val fileTransferAvailable: Boolean

/** Whether this platform has the home-screen widget; iPhone doesn't yet. */
expect val homeScreenWidgetAvailable: Boolean
