package com.andreaserick.languagecoast.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Whether the user has enabled reduced motion at the OS level (disables confetti, 3D flips, and ambient glides). */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Reads the platform's reduced-motion accessibility preference. */
@Composable
expect fun rememberSystemReducedMotion(): Boolean
