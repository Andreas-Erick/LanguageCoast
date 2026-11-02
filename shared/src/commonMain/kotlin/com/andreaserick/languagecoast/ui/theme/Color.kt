package com.andreaserick.languagecoast.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The "Language Coast" palette: deep water, a few lighter layers of it for surfaces, and one bright
 * sunlit accent. The accent marks what to do next (the main action, due counts, the active tab), so
 * everything else stays in the ocean tones.
 */

/** The background of every screen. */
val OceanGround = Color(0xFF0C2536)

/** The bottom navigation bar, a step darker than [OceanGround]. */
val NavGround = Color(0xFF0A1F2E)

/** Cards, and fields directly on the background. */
val SurfaceCard = Color(0xFF153449)

/** One layer above [SurfaceCard]: icon tiles, menus and panels inside a card. */
val SurfaceRaised = Color(0xFF1F4560)

/** The selected part of a switch, secondary buttons and borders. Also the top layer: it works on any surface. */
val SurfaceSelected = Color(0xFF24506E)

/** The one accent. [OceanGround] text on it has over 10:1 contrast, as does this as text on [OceanGround]. */
val Sunlit = Color(0xFFF3C969)

/** Standard text and icons. */
val TextPrimary = Color(0xFFF5F2EA)

/** Supporting text and quiet icons; above 4.5:1 on every surface except [SurfaceSelected]. */
val TextSecondary = Color(0xFFA9BDCB)

/** "Again", errors and destructive actions, as text or icon on any surface (above 4.5:1). */
val CoralText = Color(0xFFF5A28C)

/** Success: installed, active, a correct answer. */
val Seafoam = Color(0xFF7FD1AE)

/** Progress that isn't a call to action, such as islands explored this week. */
val SurfBlue = Color(0xFF6FB3D9)

/** The wave line under screen titles. */
val WaveLineColor = Color(0xFF2C5C7C)

/** A streak day that is today and not studied yet: stands out from other days without looking done. */
val StreakToday = Color(0xFF4A7898)
