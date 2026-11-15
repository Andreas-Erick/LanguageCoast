package com.andreaserick.languagecoast.ui.settings

import androidx.compose.runtime.Composable

/** Whether this platform can import, export, back up and restore files; iPhone can't yet. */
expect val fileTransferAvailable: Boolean

/** Whether this platform has the home-screen widget; iPhone doesn't yet. */
expect val homeScreenWidgetAvailable: Boolean

/**
 * Whether the app runs on Android, for texts that name what only the Android app has (reading a photo,
 * answering a reminder from its notification) or ships (its libraries). Files can be saved and opened on
 * Android only so far, which tells the two platforms apart without one more declaration on each; once
 * iPhone gets file transfer, this needs a declaration of its own.
 */
internal val runsOnAndroid: Boolean get() = fileTransferAvailable

/**
 * Whether the system lets the app show notifications, and the way to change that.
 *
 * @property allowed Whether a reminder would be shown right now. It is read again each time the app comes
 *     back to the front, since the user may have changed it in the system's settings meanwhile.
 * @property request Asks the system for the permission, and makes sure the reminders are scheduled once it
 *     is given. When the system will not ask again, it opens the app's notification settings instead.
 */
class NotificationAccess(val allowed: Boolean, val request: () -> Unit)

/** The app's [NotificationAccess], for the reminder switch and for asking after a finished session. */
@Composable
expect fun rememberNotificationAccess(): NotificationAccess
