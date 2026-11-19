package com.andreaserick.languagecoast.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.ui.components.LocalReducedMotion
import com.andreaserick.languagecoast.ui.components.rememberSystemReducedMotion

/** How the app is lit: as the device is set (dark or light, or switching by itself), always dark, or always light. */
enum class ThemeMode(val key: String, val label: String) {
    System("system", "Automatic"),
    Dark("dark", "Dark"),
    Light("light", "Light");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: System
    }
}

/**
 * The "Language Coast" color scheme for [p]. Material components pick their colors from here, so dialogs,
 * sheets, menus and pickers match the app without setting colors on each one.
 */
private fun colorScheme(p: CoastPalette): ColorScheme {
    val base = if (p.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.sunlit,
        onPrimary = p.onSunlit,

        primaryContainer = p.surfaceSelected,
        onPrimaryContainer = p.textPrimary,

        secondary = p.surfBlue,
        onSecondary = p.onColored,

        secondaryContainer = p.surfaceSelected,
        onSecondaryContainer = p.textPrimary,

        tertiary = p.coralText,
        onTertiary = p.onColored,

        tertiaryContainer = p.surfaceSelected,
        onTertiaryContainer = p.textPrimary,

        background = p.oceanGround,
        onBackground = p.textPrimary,

        surface = p.oceanGround,
        onSurface = p.textPrimary,

        surfaceVariant = p.surfaceRaised,
        onSurfaceVariant = p.textSecondary,

        // Bottom sheets use Low, menus plain, dialogs High, and the time picker's dial Highest.
        surfaceContainerLowest = p.navGround,
        surfaceContainerLow = p.surfaceCard,
        surfaceContainer = p.surfaceRaised,
        surfaceContainerHigh = p.surfaceCard,
        surfaceContainerHighest = p.surfaceRaised,

        outline = p.textSecondary,
        outlineVariant = p.surfaceSelected,

        error = p.coralText,
        onError = p.onColored
    )
}

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
internal val ChipContainer: Color get() = palette.chipContainer

/**
 * The main theme wrapper for the Language Coast application.
 * Configures the Material 3 color system, rounded shapes, and typography for [mode].
 *
 * @param content The Composable content to be themed.
 */
@Composable
fun LanguageCoastTheme(mode: ThemeMode = ThemeMode.Dark, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val chosen = when (mode) {
        ThemeMode.System -> if (systemDark) DarkPalette else LightPalette
        ThemeMode.Dark -> DarkPalette
        ThemeMode.Light -> LightPalette
    }
    // The tokens in Color.kt read this. It is set before anything below composes, so the first frame
    // is already in the right palette; a change recomposes whatever read a token.
    if (palette !== chosen) palette = chosen
    val reducedMotion = rememberSystemReducedMotion()
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = colorScheme(chosen),
            shapes = CoastShapes,
            typography = coastTypography(),
            content = content
        )
    }
}

/** Whether the theme currently showing is dark, for platform chrome such as status bar icons. */
val isDarkPalette: Boolean get() = palette.isDark
