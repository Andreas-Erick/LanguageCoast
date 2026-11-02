package com.andreaserick.languagecoast.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * The "Language Coast" palette: deep water, a few lighter layers of it for surfaces, and one bright
 * sunlit accent. The accent marks what to do next (the main action, due counts, the active tab), so
 * everything else stays in the ocean tones. The light palette keeps the same roles on sand and daylight.
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
    val onSunlit: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val coralText: Color,
    val seafoam: Color,
    val surfBlue: Color,
    val waveLine: Color,
    val streakToday: Color,
    val chipContainer: Color,
    val isDark: Boolean
)

/** Night on the coast: the app's original look. */
val DarkPalette = CoastPalette(
    oceanGround = Color(0xFF0C2536),
    oceanSurface = Color(0xFF123349),
    cardEdge = Color(0x12FFFFFF),
    navGround = Color(0xFF0A1F2E),
    surfaceCard = Color(0xFF153449),
    surfaceRaised = Color(0xFF1F4560),
    surfaceSelected = Color(0xFF24506E),
    sunlit = Color(0xFFF3C969),
    onSunlit = Color(0xFF0C2536),
    textPrimary = Color(0xFFF5F2EA),
    textSecondary = Color(0xFFA9BDCB),
    coralText = Color(0xFFF5A28C),
    seafoam = Color(0xFF7FD1AE),
    surfBlue = Color(0xFF6FB3D9),
    waveLine = Color(0xFF2C5C7C),
    streakToday = Color(0xFF4A7898),
    chipContainer = Color.White.copy(alpha = 0.06f),
    isDark = true
)

/** Daylight: pale sand and sea mist, with deeper versions of the same accents so text keeps its contrast. */
val LightPalette = CoastPalette(
    oceanGround = Color(0xFFEDF2F5),
    oceanSurface = Color(0xFFF9FBFC),
    cardEdge = Color(0x14000000),
    navGround = Color(0xFFFFFFFF),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFE2EAF0),
    surfaceSelected = Color(0xFFCCDCE7),
    sunlit = Color(0xFFE0A93A),
    onSunlit = Color(0xFF1A2733),
    textPrimary = Color(0xFF16293A),
    textSecondary = Color(0xFF4E6477),
    coralText = Color(0xFFB8402F),
    seafoam = Color(0xFF1F7A55),
    surfBlue = Color(0xFF2C7FB8),
    waveLine = Color(0xFFB7CAD8),
    streakToday = Color(0xFF9DB8CC),
    chipContainer = Color.Black.copy(alpha = 0.05f),
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

/** The one accent. [OnSunlit] text on it has over 10:1 contrast, as does this as text on [OceanGround]. */
val Sunlit: Color get() = palette.sunlit

/** Text and icons on a [Sunlit] surface. */
val OnSunlit: Color get() = palette.onSunlit

/** Standard text and icons. */
val TextPrimary: Color get() = palette.textPrimary

/** Supporting text and quiet icons; above 4.5:1 on every surface except [SurfaceSelected]. */
val TextSecondary: Color get() = palette.textSecondary

/** "Again", errors and destructive actions, as text or icon on any surface (above 4.5:1). */
val CoralText: Color get() = palette.coralText

/** Success: installed, active, a correct answer. */
val Seafoam: Color get() = palette.seafoam

/** Progress that isn't a call to action, such as islands explored this week. */
val SurfBlue: Color get() = palette.surfBlue

/** The wave line under screen titles. */
val WaveLineColor: Color get() = palette.waveLine

/** A streak day that is today and not studied yet: stands out from other days without looking done. */
val StreakToday: Color get() = palette.streakToday
