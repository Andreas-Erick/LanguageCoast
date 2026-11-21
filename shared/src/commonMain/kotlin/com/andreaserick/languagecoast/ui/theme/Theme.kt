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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.andreaserick.languagecoast.data.SunLocation
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.ui.components.LocalReducedMotion
import com.andreaserick.languagecoast.ui.components.rememberSystemReducedMotion

/**
 * How the app is lit: following the sun through the day (sunrise, midday, evening, sunset, night), as the
 * device is set (dark or light, or switching by itself), the original navy-and-gold look, always dark, or
 * always light.
 */
enum class ThemeMode(val key: String, val label: String) {
    Sun("sun", "Follow the sun"),
    System("system", "Like the phone"),
    Classic("classic", "Classic"),
    Dark("dark", "Dark"),
    Light("light", "Light");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: Sun
    }
}

/**
 * The palette [this] mode shows at [now] in [zone] (and at [location], if the user lets the app use it), on a
 * phone that is itself set dark or not ([systemDark]). Dark is the night look and Light the midday one.
 */
fun ThemeMode.paletteAt(
    systemDark: Boolean,
    now: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
    location: SunLocation? = null
): CoastPalette = when (this) {
    ThemeMode.Sun -> SkyClock.skyAt(now, zone, location).palette
    ThemeMode.System -> if (systemDark) NightPalette else MiddayPalette
    ThemeMode.Classic -> ClassicPalette
    ThemeMode.Dark -> NightPalette
    ThemeMode.Light -> MiddayPalette
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
fun LanguageCoastTheme(mode: ThemeMode = ThemeMode.Sun, location: SunLocation? = null, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    // Following the sun, the look moves on by itself: the clock is read again every minute, which keeps a
    // blend (half an hour to an hour long) moving in steps too small to see.
    var now by remember { mutableStateOf(Clock.System.now()) }
    if (mode == ThemeMode.Sun) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(SKY_TICK_MILLIS)
                now = Clock.System.now()
            }
        }
    }
    val chosen = remember(mode, systemDark, now, location) { mode.paletteAt(systemDark, now, location = location) }
    // The tokens in Color.kt read this. It is set before anything below composes, so the first frame
    // is already in the right palette; a change recomposes whatever read a token.
    if (palette != chosen) palette = chosen
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

private const val SKY_TICK_MILLIS = 60_000L

/** Whether the theme currently showing is dark, for platform chrome such as status bar icons. */
val isDarkPalette: Boolean get() = palette.isDark
