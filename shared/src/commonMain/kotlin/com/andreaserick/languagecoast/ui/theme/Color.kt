package com.andreaserick.languagecoast.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified

/**
 * The "Language Coast" palettes: the coast as the sky over it changes through the day, at sunrise, midday,
 * in the evening, at sunset and at night ([SkyClock] blends between them). Each has one accent, the sun, which
 * marks what to do next (the main action, due counts, the active tab); everything else stays in the
 * background tones. None of the light ones has anything as bright as white.
 *
 * The tokens below read the palette the theme has chosen (see [LanguageCoastTheme]); reading one in
 * composition recomposes when the theme changes, so screens use them like constants.
 */
class CoastPalette(
    val oceanGround: Color,
    val oceanSurface: Color,
    val cardEdge: Color,
    val navGround: Color,
    val surfaceCard: Color,
    val surfaceRaised: Color,
    val surfaceSelected: Color,
    val sunlit: Color,
    val sunlitText: Color,
    val accentControl: Color,
    val onSunlit: Color,
    val onColored: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val coralText: Color,
    val seafoam: Color,
    val onSeafoam: Color,
    val surfBlue: Color,
    val waveLine: Color,
    val streakToday: Color,
    val chipContainer: Color,
    val isDark: Boolean,
    /** How strongly the background's stars show: 0 for none (the day), 1 for a full night sky. */
    val stars: Float = 0f,
    /** The soft light in the background's top corner; unspecified means the sun's, [sunlit] at 10%. */
    val glow: Color = Color.Unspecified,
    /** The colour of the background's stars, a pale lavender white; it only shows where [stars] is above 0. */
    val starColor: Color = Color(0xFFE9E7FF)
) {
    /** The colour of the background's corner glow. */
    val glowColor: Color get() = if (glow.isSpecified) glow else sunlit.copy(alpha = 0.10f)
}

/**
 * Sunrise: warm peach light with lavender shadows, the low sun a soft apricot. The tints are kept low so a
 * screen reads as calm paper, not as pink; nothing is as bright as white.
 */
val SunrisePalette = CoastPalette(
    oceanGround = Color(0xFFE9DDD6),
    oceanSurface = Color(0xFFF3DED5),
    cardEdge = Color(0x182A1E29),
    navGround = Color(0xFFEBE1DB),
    surfaceCard = Color(0xFFF6EEEA),
    surfaceRaised = Color(0xFFEBE0DB),
    surfaceSelected = Color(0xFFD5CFE4),
    sunlit = Color(0xFFF7A885),
    sunlitText = Color(0xFF8C431F),
    accentControl = Color(0xFF853E19),
    onSunlit = Color(0xFF2B1B13),
    onColored = Color(0xFFFAF6F3),
    textPrimary = Color(0xFF2A1E29),
    textSecondary = Color(0xFF5E4E5C),
    coralText = Color(0xFF9E2B32),
    seafoam = Color(0xFF176449),
    onSeafoam = Color(0xFFFAF6F3),
    surfBlue = Color(0xFF225899),
    waveLine = Color(0xFFCFB8AE),
    streakToday = Color(0xFFB5ACC7),
    chipContainer = Color(0x102A1E29),
    isDark = false
)

/**
 * Midday: clear, cool daylight with a pale-sky tint at the top, the high sun a warm gold rather than a signal
 * yellow. Soft rather than bright, so it isn't blinding.
 */
val MiddayPalette = CoastPalette(
    oceanGround = Color(0xFFD5E2E8),
    oceanSurface = Color(0xFFD3E6F2),
    cardEdge = Color(0x181A242F),
    navGround = Color(0xFFDBE5EA),
    surfaceCard = Color(0xFFEAF2F6),
    surfaceRaised = Color(0xFFDAE5EB),
    surfaceSelected = Color(0xFFC2D6E5),
    sunlit = Color(0xFFE9B452),
    sunlitText = Color(0xFF7B5200),
    accentControl = Color(0xFF754C00),
    onSunlit = Color(0xFF271E0E),
    onColored = Color(0xFFF3F8FA),
    textPrimary = Color(0xFF1A242F),
    textSecondary = Color(0xFF485564),
    coralText = Color(0xFF9E2B32),
    seafoam = Color(0xFF176449),
    onSeafoam = Color(0xFFF3F8FA),
    surfBlue = Color(0xFF225899),
    waveLine = Color(0xFFACC1CF),
    streakToday = Color(0xFF9BB5C8),
    chipContainer = Color(0x101A242F),
    isDark = false
)

/**
 * Evening: golden hour, kept to a honey tint on warm stone, the sun amber.
 */
val EveningPalette = CoastPalette(
    oceanGround = Color(0xFFE5DFD4),
    oceanSurface = Color(0xFFEEE1D1),
    cardEdge = Color(0x182C2017),
    navGround = Color(0xFFE7E2D9),
    surfaceCard = Color(0xFFF4F0E9),
    surfaceRaised = Color(0xFFE8E2D9),
    surfaceSelected = Color(0xFFE4CDC2),
    sunlit = Color(0xFFF5AC69),
    sunlitText = Color(0xFF854A00),
    accentControl = Color(0xFF7F4400),
    onSunlit = Color(0xFF2A1C10),
    onColored = Color(0xFFF9F6F2),
    textPrimary = Color(0xFF2C2017),
    textSecondary = Color(0xFF615044),
    coralText = Color(0xFF9E2B32),
    seafoam = Color(0xFF176449),
    onSeafoam = Color(0xFFF9F6F2),
    surfBlue = Color(0xFF225899),
    waveLine = Color(0xFFCABBA9),
    streakToday = Color(0xFFC7AA9B),
    chipContainer = Color(0x102C2017),
    isDark = false
)

/**
 * Sunset: indigo water under a violet sky, with the setting sun as an apricot accent. Every text colour is
 * >= 4.5:1 on every surface, including [surfaceSelected] (the lowest is 4.99:1, [coralText]).
 */
val SunsetPalette = CoastPalette(
    oceanGround = Color(0xFF171A2E),
    oceanSurface = Color(0xFF1F2140),
    cardEdge = Color(0x18E3DDF7),
    navGround = Color(0xFF12142A),
    surfaceCard = Color(0xFF222542),
    surfaceRaised = Color(0xFF2D3054),
    surfaceSelected = Color(0xFF363A63),
    sunlit = Color(0xFFFFB27A),
    sunlitText = Color(0xFFFFB27A),
    accentControl = Color(0xFFFFB27A),
    onSunlit = Color(0xFF1A1528),
    onColored = Color(0xFF171A2E),
    textPrimary = Color(0xFFF6F1EC),
    textSecondary = Color(0xFFB9B6CF),
    coralText = Color(0xFFFF8FA0),
    seafoam = Color(0xFF7DD8B5),
    onSeafoam = Color(0xFF171A2E),
    surfBlue = Color(0xFF9AAEFF),
    waveLine = Color(0xFF3C4070),
    streakToday = Color(0xFF5A5F92),
    chipContainer = Color(0x12E3DDF7),
    isDark = true
)

/**
 * Night: a midnight-blue sky fading to true black, a faint field of stars and a pale moon glow (see
 * [stars] and [glow]), cards with a cool blue undertone and a soft moonlight-lavender accent. Text sits a touch
 * below white, which glares less on the dark, and the bottom of every screen is black, which an OLED screen
 * leaves off.
 */
val NightPalette = CoastPalette(
    oceanGround = Color(0xFF000000),
    oceanSurface = Color(0xFF0E142C),
    cardEdge = Color(0x0FB8B8ED),
    navGround = Color(0xFF04060A),
    surfaceCard = Color(0xFF171921),
    surfaceRaised = Color(0xFF21242D),
    surfaceSelected = Color(0xFF2B2F3A),
    sunlit = Color(0xFFABAAE6),
    sunlitText = Color(0xFFB8B8ED),
    accentControl = Color(0xFFB8B8ED),
    onSunlit = Color(0xFF0E0D1C),
    onColored = Color(0xFF000000),
    textPrimary = Color(0xFFE7E7ED),
    textSecondary = Color(0xFFAAADBA),
    coralText = Color(0xFFEE939B),
    seafoam = Color(0xFF7BD1BA),
    onSeafoam = Color(0xFF000000),
    surfBlue = Color(0xFF84C1DE),
    waveLine = Color(0xFF282C42),
    streakToday = Color(0xFF434758),
    chipContainer = Color(0x14B8B8ED),
    isDark = true,
    stars = 1f,
    glow = Color(0x24BCB8E2)
)

/**
 * Classic: the app's look before it followed the sun, deep navy water with one gold accent. The same
 * colours, except [surfBlue], which is a shade lighter: the original 0xFF6FB3D9 was 4.1:1 on [surfaceSelected].
 */
val ClassicPalette = CoastPalette(
    oceanGround = Color(0xFF0C2536),
    oceanSurface = Color(0xFF123349),
    cardEdge = Color(0x18D8EBF7),
    navGround = Color(0xFF0A1F2E),
    surfaceCard = Color(0xFF153449),
    surfaceRaised = Color(0xFF1F4560),
    surfaceSelected = Color(0xFF1F4966),
    sunlit = Color(0xFFF3C969),
    sunlitText = Color(0xFFF3C969),
    accentControl = Color(0xFFF3C969),
    onSunlit = Color(0xFF0C2536),
    onColored = Color(0xFF0C2536),
    textPrimary = Color(0xFFF5F2EA),
    textSecondary = Color(0xFFB2C5D3),
    coralText = Color(0xFFF7AA96),
    seafoam = Color(0xFF7FD1AE),
    onSeafoam = Color(0xFF0C2536),
    surfBlue = Color(0xFF7BBDE2),
    waveLine = Color(0xFF2C5C7C),
    streakToday = Color(0xFF4A7898),
    chipContainer = Color(0x12D8EBF7),
    isDark = true
)

/** The palette in use; [LanguageCoastTheme] sets it from the theme setting. */
var palette: CoastPalette by mutableStateOf(SunsetPalette)
    internal set

/** The background of every screen: the bottom of the gradient that starts at [OceanSurface]. */
val OceanGround: Color get() = palette.oceanGround

/** The top of the screen background, a touch lighter than [OceanGround], as if lit from above. */
val OceanSurface: Color get() = palette.oceanSurface

/** A hairline along the edge of cards and the nav bar, lifting them off the background without a shadow. */
val CardEdge: Color get() = palette.cardEdge

/** The bottom navigation bar, a step darker than [OceanGround]. */
val NavGround: Color get() = palette.navGround

/** Cards, and fields directly on the background. */
val SurfaceCard: Color get() = palette.surfaceCard

/** One layer above [SurfaceCard]: icon tiles, menus and panels inside a card. */
val SurfaceRaised: Color get() = palette.surfaceRaised

/** The selected part of a switch, secondary buttons and borders. Also the top layer: it works on any surface. */
val SurfaceSelected: Color get() = palette.surfaceSelected

/**
 * The one accent, as a fill: buttons, pills, progress bars, borders, switches and the cursor. [OnSunlit]
 * text on it is at least 4.5:1 in every palette and every blend of them. As text or an icon, use
 * [SunlitText]: this colour on a light surface is under 2:1.
 */
val Sunlit: Color get() = palette.sunlit

/**
 * The accent as a text or icon colour: links, "due" labels, the selected tab. A deeper shade of [Sunlit] in
 * the light palettes; at least 4.5:1 on every surface in every palette and blend (SkyPaletteTest checks it).
 */
val SunlitText: Color get() = palette.sunlitText

/** High-contrast accent for non-text UI boundaries (focus rings, selected borders, progress indicators): ≥ 4.5:1 in every palette. */
val AccentControl: Color get() = palette.accentControl

/** Text and icons on a [Sunlit] surface. */
val OnSunlit: Color get() = palette.onSunlit

/** Main text and icons; ≥ 4.5:1 on every surface, including [SurfaceSelected]. */
val TextPrimary: Color get() = palette.textPrimary

/** Supporting text and quiet icons; ≥ 4.5:1 on every surface, including [SurfaceSelected]. */
val TextSecondary: Color get() = palette.textSecondary

/** "Again", errors and destructive actions, as text or icon (≥ 4.5:1 on every surface, including [SurfaceSelected]). */
val CoralText: Color get() = palette.coralText

/**
 * Success: installed, active, a correct answer. As text it is at least 4.5:1 on every surface in every
 * palette.
 */
val Seafoam: Color get() = palette.seafoam

/** Text and icons on a [Seafoam] fill: at least 4.5:1 in every palette. */
val OnSeafoam: Color get() = palette.onSeafoam

/** Progress that isn't a call to action, such as islands explored this week. */
val SurfBlue: Color get() = palette.surfBlue

/** The wave line under screen titles. */
val WaveLineColor: Color get() = palette.waveLine

/** A streak day that is today and not studied yet: stands out from other days without looking done. */
val StreakToday: Color get() = palette.streakToday
