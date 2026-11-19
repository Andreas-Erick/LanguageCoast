package com.andreaserick.languagecoast.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * The "Language Coast" palette: deep water, a few lighter layers of it for surfaces, and one bright
 * sunlit accent. The accent marks what to do next (the main action, due counts, the active tab), so
 * everything else stays in the ocean tones. The light palette keeps the same roles on warm sand, with nothing in it as bright as white.
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
    val isDark: Boolean
)

/** Night on the coast: the app's original look. Every text colour is ≥ 4.5:1 on every surface, including [surfaceSelected]. */
val DarkPalette = CoastPalette(
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
    surfBlue = Color(0xFF6FB3D9),
    waveLine = Color(0xFF2C5C7C),
    streakToday = Color(0xFF4A7898),
    chipContainer = Color(0x12D8EBF7),
    isDark = true
)

/**
 * Daylight: warm sand under a hazy sky, with deeper versions of the same accents so text and non-text
 * controls keep WCAG AA contrast. Nothing here is pure white or dead gray: neutral overlays carry ocean ink
 * and warm alabaster undertones.
 */
val LightPalette = CoastPalette(
    oceanGround = Color(0xFFDFDACD),
    oceanSurface = Color(0xFFE9E4D8),
    cardEdge = Color(0x1816293A),
    navGround = Color(0xFFE6E1D5),
    surfaceCard = Color(0xFFF2EEE4),
    surfaceRaised = Color(0xFFE3DDCF),
    surfaceSelected = Color(0xFFC8D5DB),
    sunlit = Color(0xFFE0A93A),
    sunlitText = Color(0xFF754700),
    accentControl = Color(0xFF8C5600),
    onSunlit = Color(0xFF1A2733),
    onColored = Color(0xFFFAF7F0),
    textPrimary = Color(0xFF16293A),
    textSecondary = Color(0xFF43566A),
    coralText = Color(0xFFA33423),
    seafoam = Color(0xFF155E41),
    onSeafoam = Color(0xFFFAF7F0),
    surfBlue = Color(0xFF256F9F),
    waveLine = Color(0xFFB5BFC2),
    streakToday = Color(0xFF9DB8CC),
    chipContainer = Color(0x1016293A),
    isDark = false
)

/** The palette in use; [LanguageCoastTheme] sets it from the theme setting. */
var palette: CoastPalette by mutableStateOf(DarkPalette)
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
 * text on it is 10.0:1 in the dark palette and 7.2:1 in the light one. As text or an icon, use [SunlitText]:
 * this colour on a light surface is under 2:1.
 */
val Sunlit: Color get() = palette.sunlit

/**
 * The accent as a text or icon colour: links, "due" labels, the selected tab. The same as [Sunlit] in the
 * dark palette (10.0:1 on [OceanGround], 8.3:1 on [SurfaceCard], 6.4:1 on [SurfaceRaised], 5.5:1 on
 * [SurfaceSelected]); a deep amber in the light one (5.7:1, 6.8:1, 5.8:1 and 5.3:1 on the same surfaces).
 */
val SunlitText: Color get() = palette.sunlitText

/** High-contrast accent for non-text UI boundaries (focus rings, selected borders, progress indicators): ≥ 4.5:1 in both palettes. */
val AccentControl: Color get() = palette.accentControl

/** Text and icons on a [Sunlit] surface. */
val OnSunlit: Color get() = palette.onSunlit

/** Supporting text and quiet icons; ≥ 4.5:1 on every surface, including [SurfaceSelected]. */
val TextPrimary: Color get() = palette.textPrimary

/** Supporting text and quiet icons; ≥ 4.5:1 on every surface, including [SurfaceSelected]. */
val TextSecondary: Color get() = palette.textSecondary

/** "Again", errors and destructive actions, as text or icon (≥ 4.5:1 on every surface, including [SurfaceSelected]). */
val CoralText: Color get() = palette.coralText

/**
 * Success: installed, active, a correct answer. As text it is above 4.5:1 on every surface in both palettes
 * (the lowest is 4.75:1, on [SurfaceSelected]).
 */
val Seafoam: Color get() = palette.seafoam

/** Text and icons on a [Seafoam] fill: 8.7:1 in the dark palette, 7.75:1 in the light one. */
val OnSeafoam: Color get() = palette.onSeafoam

/** Progress that isn't a call to action, such as islands explored this week. */
val SurfBlue: Color get() = palette.surfBlue

/** The wave line under screen titles. */
val WaveLineColor: Color get() = palette.waveLine

/** A streak day that is today and not studied yet: stands out from other days without looking done. */
val StreakToday: Color get() = palette.streakToday
