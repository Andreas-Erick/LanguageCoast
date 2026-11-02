package com.andreaserick.languagecoast.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The "Language Coast" color scheme. Built on `darkColorScheme` so all Material 3 components
 * (text fields, dialogs, menus, buttons, icons) have high-contrast legibility on the deep
 * [DeepOceanBlue] coastal background.
 */
private val CoastColorScheme = darkColorScheme(
    primary = SandBeige,            // Main buttons, active indicators, and top bar accents
    onPrimary = DeepOceanBlue,      // High-contrast dark ocean text inside sand buttons

    primaryContainer = WaveTeal,    // Highlighted cards (e.g., save confirmation)
    onPrimaryContainer = Color.White,

    secondary = WaveTeal,           // Secondary elements
    onSecondary = Color.White,

    secondaryContainer = ElevatedCardWash,
    onSecondaryContainer = SandBeige,

    tertiary = CoralAccent,         // Highlights or error/warning accents
    onTertiary = DeepOceanBlue,

    background = DeepOceanBlue,     // The main background of all screens
    onBackground = Color.White,     // Standard text color on the dark ocean background

    surface = DeepOceanBlue,        // Dialogs, bottom sheets, and default surfaces
    onSurface = Color.White,        // High-contrast text inside inputs and surfaces

    surfaceVariant = WaveTeal.copy(alpha = 0.35f),
    onSurfaceVariant = MistWhite,   // Labels, placeholders, and supporting text

    outline = SandMuted,            // Unfocused borders for OutlinedTextFields and chips
    outlineVariant = SandBeige.copy(alpha = 0.22f),

    error = CoralText,
    onError = DeepOceanBlue
)

private val CoastShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Harmonized text field colors for translucent coastal cards. */
@Composable
fun coastTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = Color.White.copy(alpha = 0.04f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.02f),
    focusedBorderColor = SandBeige,
    unfocusedBorderColor = SandMuted.copy(alpha = 0.65f),
    focusedLabelColor = SandBeige,
    unfocusedLabelColor = MistWhite,
    focusedPlaceholderColor = MistWhite.copy(alpha = 0.55f),
    unfocusedPlaceholderColor = MistWhite.copy(alpha = 0.55f),
    focusedLeadingIconColor = SandBeige,
    unfocusedLeadingIconColor = SandMuted,
    focusedTrailingIconColor = SandBeige,
    unfocusedTrailingIconColor = SandMuted,
    cursorColor = SandBeige
)

/**
 * The main theme wrapper for the Language Coast application.
 * Configures the Material 3 color system, rounded shapes, and typography.
 *
 * @param content The Composable content to be themed.
 */
@Composable
fun LanguageCoastTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CoastColorScheme,
        shapes = CoastShapes,
        typography = coastTypography(),
        content = content
    )
}
