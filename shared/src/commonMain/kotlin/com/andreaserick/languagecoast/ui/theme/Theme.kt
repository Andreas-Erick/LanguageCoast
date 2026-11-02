package com.andreaserick.languagecoast.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The "Language Coast" color scheme. Material components pick their colors from here, so dialogs,
 * sheets, menus and pickers match the app without setting colors on each one.
 */
private val CoastColorScheme = darkColorScheme(
    primary = Sunlit,
    onPrimary = OceanGround,

    primaryContainer = SurfaceSelected,
    onPrimaryContainer = TextPrimary,

    secondary = SurfBlue,
    onSecondary = OceanGround,

    secondaryContainer = SurfaceSelected,
    onSecondaryContainer = TextPrimary,

    tertiary = CoralText,
    onTertiary = OceanGround,

    tertiaryContainer = SurfaceSelected,
    onTertiaryContainer = TextPrimary,

    background = OceanGround,
    onBackground = TextPrimary,

    surface = OceanGround,
    onSurface = TextPrimary,

    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextSecondary,

    // Bottom sheets use Low, menus plain, dialogs High, and the time picker's dial Highest.
    surfaceContainerLowest = NavGround,
    surfaceContainerLow = SurfaceCard,
    surfaceContainer = SurfaceRaised,
    surfaceContainerHigh = SurfaceCard,
    surfaceContainerHighest = SurfaceRaised,

    outline = TextSecondary,
    outlineVariant = SurfaceSelected,

    error = CoralText,
    onError = OceanGround
)

private val CoastShapes = Shapes(
    // Material's menus use this one; a plain 4dp corner would look out of place next to everything else.
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(12.dp),
    // Buttons and text fields.
    medium = RoundedCornerShape(16.dp),
    // Cards.
    large = RoundedCornerShape(20.dp),
    // The flashcard, sheets and dialogs.
    extraLarge = RoundedCornerShape(24.dp)
)

/** Translucent, so a chip reads as a step lighter on both the background and a card. */
internal val ChipContainer = Color.White.copy(alpha = 0.06f)

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
