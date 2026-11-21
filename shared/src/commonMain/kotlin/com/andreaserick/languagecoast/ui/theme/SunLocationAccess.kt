package com.andreaserick.languagecoast.ui.theme

import androidx.compose.runtime.Composable

/** Whether Follow the sun can use the phone's location on this platform; iPhone doesn't yet. */
expect val sunLocationAvailable: Boolean

/**
 * Whether the system lets the app read the phone's approximate location, and the way to ask.
 *
 * @property allowed Read again each time the app comes back to the front, since the user may have changed it
 *     in the system's settings meanwhile.
 * @property request Shows the system's question; once the system won't ask any more, opens the app's page in
 *     the system's settings instead.
 */
class SunLocationAccess(val allowed: Boolean, val request: () -> Unit)

@Composable
expect fun rememberSunLocationAccess(): SunLocationAccess

/**
 * While [active], reads the phone's approximate location each time the app comes to the front and stores it
 * for Follow the sun (see [com.andreaserick.languagecoast.data.SunLocation]). It reads nothing while the
 * permission is missing or the phone's location is switched off; the last location is kept then.
 */
@Composable
expect fun SunLocationUpdates(active: Boolean)
