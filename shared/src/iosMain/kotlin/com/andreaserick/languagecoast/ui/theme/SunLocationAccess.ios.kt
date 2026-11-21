package com.andreaserick.languagecoast.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Not on iPhone yet: it needs Core Location and a usage text in Info.plist. Follow the sun goes by the time zone there. */
actual val sunLocationAvailable: Boolean = false

@Composable
actual fun rememberSunLocationAccess(): SunLocationAccess = remember { SunLocationAccess(allowed = false, request = {}) }

@Composable
actual fun SunLocationUpdates(active: Boolean) = Unit
