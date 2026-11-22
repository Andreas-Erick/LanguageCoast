package com.andreaserick.languagecoast.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Whether the user has enabled reduced motion at the OS level (disables confetti, 3D flips, and ambient glides). */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Reads the platform's reduced-motion accessibility preference. */
@Composable
expect fun rememberSystemReducedMotion(): Boolean

/** How a section opens: grows and fades in, or simply appears when the user has asked for reduced motion. */
@Composable
fun expandEnter(): androidx.compose.animation.EnterTransition =
    if (LocalReducedMotion.current) androidx.compose.animation.EnterTransition.None
    else androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn()

/** How a section closes: the counterpart of [expandEnter]. */
@Composable
fun collapseExit(): androidx.compose.animation.ExitTransition =
    if (LocalReducedMotion.current) androidx.compose.animation.ExitTransition.None
    else androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
