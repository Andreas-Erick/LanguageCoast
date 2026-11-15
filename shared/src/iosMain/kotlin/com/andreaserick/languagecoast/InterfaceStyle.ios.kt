package com.andreaserick.languagecoast

import com.andreaserick.languagecoast.ui.theme.ThemeMode
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * Tells iOS which appearance the app is in, so that the status bar, the keyboard and system alerts match the
 * theme picked in Settings: light text over the dark coast, dark text over the light one. With the theme that
 * follows the phone, the override is lifted and iOS decides again.
 *
 * It is set on the app's windows, which the status bar follows, and on the [controller] that shows the
 * Compose UI, which decides what Compose takes for the phone's own appearance and how the view looks before
 * its first frame is drawn.
 */
internal fun applyInterfaceStyle(mode: ThemeMode, controller: UIViewController) {
    val style = when (mode) {
        ThemeMode.Dark -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
        ThemeMode.Light -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
        else -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
    }
    controller.overrideUserInterfaceStyle = style
    UIApplication.sharedApplication.connectedScenes.forEach { scene ->
        (scene as? UIWindowScene)?.windows?.forEach { window ->
            (window as? UIWindow)?.overrideUserInterfaceStyle = style
        }
    }
}
