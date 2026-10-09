package com.andreaserick.languagecoast.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Custom "Language Coast" branding palette.
 * These colors define the unique aquatic and coastal aesthetic of the application.
 */

/** The primary dark background color, representing the deep ocean. */
val DeepOceanBlue = Color(0xFF133C55)
/** A medium blue used for primary actions and highlights. */
val WaveTeal = Color(0xFF386FA4)
/** A softer blue used for secondary surfaces like island cards. */
val SurfLightBlue = Color(0xFF59A5D8)
/** A warm beige used for primary text and titles on dark backgrounds. */
val SandBeige = Color(0xFFC2B280)
/** A vibrant orange-red used for critical actions (e.g., Delete, Again). */
val CoralAccent = Color(0xFFEE6C4D)

/** A very dark grey used for standard text on light surfaces. */
val DarkCharcoal = Color(0xFF1E1E24)

/** The bottom of the background gradient, a deeper shade of [DeepOceanBlue]. */
val AbyssBlue = Color(0xFF0E2F44)

/** [SandBeige] for secondary text on [DeepOceanBlue]; still meets the 4.5:1 contrast ratio for text. */
val SandMuted = Color(0xFFB0A67C)

/** The background of grouped content (settings, streak, stats): a faint white wash over the ocean. */
val CardWash = Color.White.copy(alpha = 0.06f)

/**
 * Secondary text on [CardWash] cards. [SandMuted] drops below 4.5:1 on the lighter card
 * background, while this stays above 6:1.
 */
val MistWhite = Color.White.copy(alpha = 0.75f)

/** A lighter [CoralAccent] for text on [DeepOceanBlue] (5.3:1 contrast; plain coral is only 3.8:1). */
val CoralText = Color(0xFFF39882)

/**
 * Light tints for island and coast cards. All give at least 4.5:1 contrast for [DeepOceanBlue] text,
 * including secondary text at 85% opacity.
 */
val CardTints = listOf(
    Color(0xFF9FC3E7), // Sky
    Color(0xFFA8D5BA), // Seafoam
    Color(0xFFE3D3A4), // Dune
    Color(0xFFF2B8A2)  // Shell
)

/** A stable tint for the card with [id], so each card keeps its colour across launches. */
fun cardTint(id: Int): Color = CardTints[Math.floorMod(id, CardTints.size)]
