package com.andreaserick.languagecoast

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import com.andreaserick.languagecoast.data.PersonalIslandBuilder
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.StarterImporter
import com.andreaserick.languagecoast.navigation.RecapScreenRoute
import com.andreaserick.languagecoast.ui.recap.RecapScreen
import com.andreaserick.languagecoast.ui.theme.ThemeMode
import com.andreaserick.languagecoast.ui.theme.SunLocationUpdates
import com.andreaserick.languagecoast.ui.components.LocalFeedback
import com.andreaserick.languagecoast.ui.components.LocalHapticsEnabled
import com.andreaserick.languagecoast.ui.components.LocalReducedMotion
import com.andreaserick.languagecoast.ui.components.rememberFeedback
import org.koin.compose.koinInject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.andreaserick.languagecoast.navigation.AboutYouScreenRoute
import com.andreaserick.languagecoast.navigation.CoastScreenRoute
import com.andreaserick.languagecoast.navigation.CreateScreenRoute
import com.andreaserick.languagecoast.navigation.IslandScreenRoute
import com.andreaserick.languagecoast.navigation.MyCoastScreenRoute
import com.andreaserick.languagecoast.navigation.SearchScreenRoute
import com.andreaserick.languagecoast.navigation.SettingsScreenRoute
import com.andreaserick.languagecoast.navigation.StudyScreenRoute
import com.andreaserick.languagecoast.ui.about.AboutYouScreen
import com.andreaserick.languagecoast.ui.coast.CoastScreen
import com.andreaserick.languagecoast.ui.components.CoastSnackbar
import com.andreaserick.languagecoast.ui.components.KeepScreenOn
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.OceanBackground
import com.andreaserick.languagecoast.ui.components.UndoMessenger
import com.andreaserick.languagecoast.ui.create.CreateScreen
import com.andreaserick.languagecoast.ui.island.IslandScreen
import com.andreaserick.languagecoast.ui.mycoast.MyCoastScreen
import com.andreaserick.languagecoast.ui.search.SearchScreen
import com.andreaserick.languagecoast.ui.settings.AiSharingPrompt
import com.andreaserick.languagecoast.ui.settings.SettingsScreen
import com.andreaserick.languagecoast.ui.study.StudyScreen
import com.andreaserick.languagecoast.ui.theme.LanguageCoastTheme
import com.andreaserick.languagecoast.ui.theme.CardEdge
import com.andreaserick.languagecoast.ui.theme.NavGround
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/** A tab in the bottom navigation bar. */
private data class TopLevelDestination(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val isSelected: (NavDestination) -> Boolean
)

private val topLevelDestinations = listOf(
    TopLevelDestination(
        route = CreateScreenRoute,
        label = "Today",
        selectedIcon = Icons.Filled.WbSunny,
        unselectedIcon = Icons.Outlined.WbSunny
    ) { it.hasRoute<CreateScreenRoute>() },
    // A coast is only opened from My Coasts, so it keeps that tab selected.
    TopLevelDestination(
        route = MyCoastScreenRoute,
        label = "My Coasts",
        selectedIcon = Icons.Filled.Sailing,
        unselectedIcon = Icons.Outlined.Sailing
    ) { it.hasRoute<MyCoastScreenRoute>() || it.hasRoute<CoastScreenRoute>() || it.hasRoute<RecapScreenRoute>() },
    TopLevelDestination(
        route = SettingsScreenRoute(),
        label = "Settings",
        selectedIcon = Icons.Filled.Tune,
        unselectedIcon = Icons.Outlined.Tune
    ) { it.hasRoute<SettingsScreenRoute>() }
)

private const val TRANSITION_MILLIS = 300

/** With reduced motion, screens only cross-fade, and quicker. */
private const val REDUCED_TRANSITION_MILLIS = 150

/**
 * A screen opened on top of another: it slides in [towards] that way and back out the opposite way, and while
 * a horizontal screen covers it, it moves aside to the left and comes back from there. Each of the four
 * transitions is set, because a missing one falls back to another and slides the wrong way. A study session
 * covers what is below it from the bottom, so nothing slides sideways for it. With reduced motion everything
 * only fades.
 */
private inline fun <reified T : Any> NavGraphBuilder.detailScreen(
    reducedMotion: Boolean,
    towards: SlideDirection = SlideDirection.Start,
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    val back = if (towards == SlideDirection.Up) SlideDirection.Down else SlideDirection.End
    composable<T>(
        enterTransition = {
            if (reducedMotion) fadeIn(tween(REDUCED_TRANSITION_MILLIS))
            else slideIntoContainer(towards, tween(TRANSITION_MILLIS)) + fadeIn()
        },
        exitTransition = {
            if (reducedMotion || targetState.destination.hasRoute<StudyScreenRoute>()) {
                fadeOut(tween(if (reducedMotion) REDUCED_TRANSITION_MILLIS else TRANSITION_MILLIS))
            } else slideOutOfContainer(SlideDirection.Start, tween(TRANSITION_MILLIS)) + fadeOut()
        },
        popEnterTransition = {
            if (reducedMotion || initialState.destination.hasRoute<StudyScreenRoute>()) {
                fadeIn(tween(if (reducedMotion) REDUCED_TRANSITION_MILLIS else TRANSITION_MILLIS))
            } else slideIntoContainer(SlideDirection.End, tween(TRANSITION_MILLIS)) + fadeIn()
        },
        popExitTransition = {
            if (reducedMotion) fadeOut(tween(REDUCED_TRANSITION_MILLIS))
            else slideOutOfContainer(back, tween(TRANSITION_MILLIS)) + fadeOut()
        },
        content = content
    )
}

/**
 * The widest the screens get. On a tablet, an unfolded phone or a desktop window they stay a column of this
 * width in the middle; the background, the bottom bar and snackbars still span the window.
 */
private val MaxContentWidth = 640.dp

/** Stands in for the phone's haptics while Vibration is off in Settings, so that nothing on a screen buzzes. */
private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {}
}

/** The whole app: bottom navigation and the screens, in the app's theme. Each platform shows it in its own window. */
@Composable
fun LanguageCoastApp() {
    val settings = koinInject<SettingsRepository>()
    val themeMode by settings.themeMode.collectAsState(initial = SettingsDefaults.THEME_MODE)
    val sunUsesLocation by settings.sunUsesLocation.collectAsState(initial = false)
    val sunLocation by settings.sunLocation.collectAsState(initial = null)
    val mode = ThemeMode.fromKey(themeMode)
    // The location is read again each time the app comes to the front, but only while it is being used.
    SunLocationUpdates(active = mode == ThemeMode.Sun && sunUsesLocation)
    LanguageCoastTheme(mode = mode, location = sunLocation.takeIf { sunUsesLocation }) {
        // The interface is English only, so it is laid out left to right on every phone. Without this, a phone
        // set to Arabic or Hebrew mirrors the screens and puts the words of a card in reverse order.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            AppContent()
        }
    }
}

/**
 * Keeps the screen on while islands are being written or starter topics added. Both take minutes, run only
 * while the app is in front, and would be cut short by the screen timing out. It sits at the root of the
 * app, so it also holds after the user has left the coast's screen.
 */
@Composable
private fun KeepScreenOnWhileWriting() {
    val building by koinInject<PersonalIslandBuilder>().progress.collectAsState()
    val importing by koinInject<StarterImporter>().progress.collectAsState()
    if (building?.isFinished == false || importing?.isFinished == false) KeepScreenOn()
}

@Composable
private fun AppContent() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val rootScope = rememberCoroutineScope()
    val undoMessenger = remember { UndoMessenger(snackbarHostState, rootScope) }
    val settings = koinInject<SettingsRepository>()
    val hapticsEnabled by settings.hapticsEnabled.collectAsState(initial = true)
    val soundsEnabled by settings.soundsEnabled.collectAsState(initial = true)
    val feedback = rememberFeedback(hapticsEnabled, soundsEnabled)
    val systemHaptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    // Provided by LanguageCoastTheme, which this sits inside.
    val reducedMotion = LocalReducedMotion.current
    val fadeMillis = if (reducedMotion) REDUCED_TRANSITION_MILLIS else TRANSITION_MILLIS

    CompositionLocalProvider(
        LocalUndoMessenger provides undoMessenger,
        LocalFeedback provides feedback,
        LocalHapticsEnabled provides hapticsEnabled,
        // Silences the buzzes of the screens. Sheets and dialogs get the phone's haptics again, so controls there read LocalHapticsEnabled.
        LocalHapticFeedback provides (if (hapticsEnabled) systemHaptics else NoHaptics)
    ) {
        // A tap on anything that doesn't handle taps itself leaves the text field and so closes the keyboard.
        // An iPhone has no back gesture or key for that, and Return in a field of several lines adds a line.
        OceanBackground(modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }) {
            Scaffold(
                containerColor = Color.Transparent,
                // Can't be worked out from a transparent container, and would default to black.
                contentColor = TextPrimary,
                // The window isn't resized for the keyboard, so the snackbar is lifted over it here; otherwise an
                // Undo shown while typing (a card deleted from Search, say) would sit under the keyboard.
                snackbarHost = {
                    SnackbarHost(
                        snackbarHostState,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                    ) { CoastSnackbar(it) }
                },
                bottomBar = {
                    val backStack by navController.currentBackStack.collectAsState()
                    // A study session is a focus mode, so it gets the whole screen.
                    val inStudySession = backStack.lastOrNull()?.destination?.hasRoute<StudyScreenRoute>() == true
                    AnimatedVisibility(
                        visible = !inStudySession,
                        // With reduced motion the bar simply goes and comes back.
                        enter = if (reducedMotion) EnterTransition.None else expandVertically(tween(TRANSITION_MILLIS)),
                        exit = if (reducedMotion) ExitTransition.None else shrinkVertically(tween(TRANSITION_MILLIS))
                    ) {
                        Column {
                            HorizontalDivider(color = CardEdge, thickness = 1.dp)
                            NavigationBar(containerColor = NavGround, tonalElevation = 0.dp) {
                                // A screen on top of a study session belongs to the tab the session was opened from
                                // (My Coasts or Today's session button), so select the tab of the newest screen that has one.
                                val tabDestination = backStack.lastOrNull { entry ->
                                    topLevelDestinations.any { tab -> entry.destination.hierarchy.any(tab.isSelected) }
                                }?.destination

                                topLevelDestinations.forEach { destination ->
                                    val selected = tabDestination?.hierarchy?.any(destination.isSelected) == true
                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                                contentDescription = null
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = destination.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold
                                            )
                                        },
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(destination.route) {
                                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = SunlitText,
                                            indicatorColor = Color.Transparent,
                                            selectedTextColor = SunlitText,
                                            unselectedIconColor = TextSecondary,
                                            unselectedTextColor = TextSecondary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    contentAlignment = Alignment.TopCenter,
                    // The window is not resized for the keyboard, so every screen is padded for it here. The bar's
                    // padding is consumed first, so the keyboard only adds what the bottom bar does not already cover.
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .imePadding()
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = CreateScreenRoute,
                        modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize(),
                        // Tabs cross-fade; screens opened from a list slide in from the side.
                        enterTransition = { fadeIn(tween(fadeMillis)) },
                        exitTransition = { fadeOut(tween(fadeMillis)) }
                    ) {
                        composable<CreateScreenRoute> {
                            CreateScreen(
                                onStartDaily = { id, language -> navController.navigate(StudyScreenRoute.daily(id, language)) },
                                onReviewCoast = { id, name -> navController.navigate(StudyScreenRoute.coast(id, name)) },
                                onReviewAll = { navController.navigate(StudyScreenRoute.Today) },
                                onAboutYou = { navController.navigate(AboutYouScreenRoute()) },
                                // Opened like its tab, so the Today tab leads back here; a screen pushed on top of Today would stay Today's.
                                onOpenSettings = { focus ->
                                    navController.navigate(SettingsScreenRoute(focus)) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                    }
                                },
                                onSearch = { navController.navigate(SearchScreenRoute) }
                            )
                        }
                        composable<MyCoastScreenRoute> {
                            MyCoastScreen(
                                onCoastClick = { id, name ->
                                    navController.navigate(CoastScreenRoute(coastId = id, coastName = name))
                                },
                                onSearchClick = { navController.navigate(SearchScreenRoute) },
                                onLeakyClick = { navController.navigate(StudyScreenRoute.Leaky) },
                                onRecapClick = { navController.navigate(RecapScreenRoute) }
                            )
                        }
                        detailScreen<RecapScreenRoute>(reducedMotion) {
                            RecapScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        detailScreen<SearchScreenRoute>(reducedMotion) {
                            SearchScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        detailScreen<CoastScreenRoute>(reducedMotion) {
                            // CoastViewModel reads the route arguments from its SavedStateHandle.
                            CoastScreen(
                                onIslandClick = { id, name ->
                                    navController.navigate(StudyScreenRoute.island(id, name))
                                },
                                onReviewCoast = { id, name ->
                                    navController.navigate(StudyScreenRoute.coast(id, name))
                                },
                                onShowCards = { id, name ->
                                    navController.navigate(IslandScreenRoute(islandId = id, islandName = name))
                                },
                                onAboutYou = { islandId -> navController.navigate(AboutYouScreenRoute(islandId)) },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        detailScreen<AboutYouScreenRoute>(reducedMotion) { entry ->
                            AboutYouScreen(
                                openAt = entry.toRoute<AboutYouScreenRoute>().islandId,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        detailScreen<IslandScreenRoute>(reducedMotion) {
                            // IslandViewModel reads the route arguments from its SavedStateHandle.
                            IslandScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable<SettingsScreenRoute> { entry ->
                            SettingsScreen(
                                onAboutYou = { navController.navigate(AboutYouScreenRoute()) },
                                focus = entry.toRoute<SettingsScreenRoute>().focus
                            )
                        }
                        detailScreen<StudyScreenRoute>(reducedMotion, towards = SlideDirection.Up) { entry ->
                            val route = entry.toRoute<StudyScreenRoute>()
                            // StudyViewModel reads the route arguments from its SavedStateHandle.
                            StudyScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onShowCards = if (route.scope == StudyScreenRoute.SCOPE_ISLAND) {
                                    { navController.navigate(IslandScreenRoute(islandId = route.islandId, islandName = route.islandName)) }
                                } else null,
                                // The bonus round takes the finished session's place, so Back leads to where the session was opened from.
                                onKeepGoing = { coastId, language ->
                                    navController.navigate(StudyScreenRoute.bonus(coastId, language)) {
                                        popUpTo<StudyScreenRoute> { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        // Dialogs and effects that belong to no screen. They are inside the providers above, so they get the app's feedback and undo.
        AiSharingPrompt()
        KeepScreenOnWhileWriting()
    }
}
