package com.andreaserick.languagecoast.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * The "Language Coast" color scheme. It is used in both light and dark system modes:
 * the dark [DeepOceanBlue] background provides a high-contrast, immersive coastal atmosphere.
 */
private val CoastColorScheme = lightColorScheme(
    primary = SandBeige,        // Used for main buttons and top bars
    onPrimary = Color.White,        // Used for text INSIDE main buttons

    primaryContainer = WaveTeal,    // Highlighted cards (e.g., save confirmation)
    onPrimaryContainer = Color.White,

    secondary = WaveTeal,           // Used for secondary elements
    onSecondary = Color.White,

    tertiary = CoralAccent,         // Used for highlights or error states

    background = DeepOceanBlue,         // The main background of all screens
    onBackground = DarkCharcoal,    // Standard text color

    surface = Color.White,          // The color of Cards and dropdowns
    onSurface = SandBeige,

    surfaceVariant = SurfLightBlue, // A softer blue for secondary cards (e.g., Island squares)
    onSurfaceVariant = Color.White
)

/**
 * The main theme wrapper for the Language Coast application.
 * Configures the Material 3 color system, typography, and system bar aesthetics.
 *
 * @param dynamicColor Whether to use Android 12+ dynamic color (Material You). Disabled by default to preserve branding.
 * @param content The Composable content to be themed.
 */
@Composable
fun LanguageCoastTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicLightColorScheme(context)
        }

        else -> CoastColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Match status and navigation bars to the DeepOceanBlue background
            val barColor = DeepOceanBlue.toArgb()

            window.statusBarColor = barColor
            window.navigationBarColor = barColor

            // Set light icons (isAppearanceLight = false) because our background is dark
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            windowInsetsController.isAppearanceLightStatusBars = false
            windowInsetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
