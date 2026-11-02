package com.andreaserick.languagecoast.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Custom "Language Coast" branding palette.
 * These colors define the aquatic, sunlit coastal aesthetic of the application.
 */

/** The primary dark background color, representing the deep ocean. */
val DeepOceanBlue = Color(0xFF133C55)
/** A richer midnight ocean shade for the middle of the ocean gradient. */
val MidnightOcean = Color(0xFF0F3147)
/** A medium blue used for primary actions and highlights. */
val WaveTeal = Color(0xFF386FA4)
/** A softer blue used for secondary surfaces like island cards. */
val SurfLightBlue = Color(0xFF59A5D8)
/** A warm beige used for primary text and titles on dark backgrounds. */
val SandBeige = Color(0xFFC2B280)
/** A brighter golden sand accent for badges and highlights. */
val SunlitSand = Color(0xFFD8C99B)
/** A vibrant orange-red used for critical actions (e.g., Delete, Again). */
val CoralAccent = Color(0xFFEE6C4D)
/** Seafoam green used for positive status badges (installed, best match, success). */
val SeafoamEmerald = Color(0xFF6CC5A1)

/** A very dark grey used for standard text on light surfaces. */
val DarkCharcoal = Color(0xFF1E1E24)

/** The bottom of the background gradient, a deeper shade of [DeepOceanBlue]. */
val AbyssBlue = Color(0xFF0A2436)

/** [SandBeige] for secondary text on [DeepOceanBlue]; still meets the 4.5:1 contrast ratio for text. */
val SandMuted = Color(0xFFB0A67C)

/** The background of grouped content (settings, streak, stats): a translucent glass wash over the ocean. */
val CardWash = Color.White.copy(alpha = 0.07f)

/** A slightly stronger glass surface for nested cards and interactive panels. */
val ElevatedCardWash = Color.White.copy(alpha = 0.11f)

/** Subtle luminous border for glass cards on the ocean background. */
val CardBorder = SandBeige.copy(alpha = 0.18f)

/**
 * Secondary text on [CardWash] cards. [SandMuted] drops below 4.5:1 on the lighter card
 * background, while this stays above 6:1.
 */
val MistWhite = Color.White.copy(alpha = 0.78f)

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
fun cardTint(id: Int): Color = CardTints[id.mod(CardTints.size)]
