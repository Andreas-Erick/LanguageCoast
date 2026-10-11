package com.andreaserick.languagecoast.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The "Language Coast" color scheme. It is used in both light and dark system modes:
 * the dark [DeepOceanBlue] background provides a high-contrast, immersive coastal atmosphere.
 */
private val CoastColorScheme = lightColorScheme(
    primary = SandBeige,        // Used for main buttons and top bars
    onPrimary = DeepOceanBlue,      // Text inside main buttons; white on sand would be below 4.5:1 contrast

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
 * Configures the Material 3 color system and typography. On Android, system bars are drawn edge-to-edge (see MainActivity).
 *
 * @param content The Composable content to be themed.
 */
@Composable
fun LanguageCoastTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CoastColorScheme,
        typography = coastTypography(),
        content = content
    )
}
