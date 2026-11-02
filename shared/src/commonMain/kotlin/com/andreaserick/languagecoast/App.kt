package com.andreaserick.languagecoast

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material.icons.outlined.Tune
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
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.ui.components.LocalFeedback
import com.andreaserick.languagecoast.ui.components.rememberFeedback
import org.koin.compose.koinInject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.andreaserick.languagecoast.navigation.CoastScreenRoute
import com.andreaserick.languagecoast.navigation.CreateScreenRoute
import com.andreaserick.languagecoast.navigation.IslandScreenRoute
import com.andreaserick.languagecoast.navigation.MyCoastScreenRoute
import com.andreaserick.languagecoast.navigation.SearchScreenRoute
import com.andreaserick.languagecoast.navigation.SettingsScreenRoute
import com.andreaserick.languagecoast.navigation.StudyScreenRoute
import com.andreaserick.languagecoast.ui.coast.CoastScreen
import com.andreaserick.languagecoast.ui.components.CoastSnackbar
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.OceanBackground
import com.andreaserick.languagecoast.ui.components.UndoMessenger
import com.andreaserick.languagecoast.ui.create.CreateScreen
import com.andreaserick.languagecoast.ui.island.IslandScreen
import com.andreaserick.languagecoast.ui.mycoast.MyCoastScreen
import com.andreaserick.languagecoast.ui.search.SearchScreen
import com.andreaserick.languagecoast.ui.settings.SettingsScreen
import com.andreaserick.languagecoast.ui.study.StudyScreen
import com.andreaserick.languagecoast.ui.theme.LanguageCoastTheme
import com.andreaserick.languagecoast.ui.theme.CardEdge
import com.andreaserick.languagecoast.ui.theme.NavGround
import com.andreaserick.languagecoast.ui.theme.Sunlit
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
        label = "Create",
        selectedIcon = Icons.Filled.EditNote,
        unselectedIcon = Icons.Outlined.EditNote
    ) { it.hasRoute<CreateScreenRoute>() },
    // A coast is only opened from My Coasts, so it keeps that tab selected.
    TopLevelDestination(
        route = MyCoastScreenRoute,
        label = "My Coasts",
        selectedIcon = Icons.Filled.Sailing,
        unselectedIcon = Icons.Outlined.Sailing
    ) { it.hasRoute<MyCoastScreenRoute>() || it.hasRoute<CoastScreenRoute>() },
    TopLevelDestination(
        route = SettingsScreenRoute,
        label = "Settings",
        selectedIcon = Icons.Filled.Tune,
        unselectedIcon = Icons.Outlined.Tune
    ) { it.hasRoute<SettingsScreenRoute>() }
)

private const val TRANSITION_MILLIS = 300

/** The whole app: bottom navigation and the screens, in the app's theme. Each platform shows it in its own window. */
@Composable
fun LanguageCoastApp() {
    LanguageCoastTheme {
        AppContent()
    }
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

    CompositionLocalProvider(LocalUndoMessenger provides undoMessenger, LocalFeedback provides feedback) {
        OceanBackground {
            Scaffold(
                containerColor = Color.Transparent,
                // Can't be worked out from a transparent container, and would default to black.
                contentColor = TextPrimary,
                snackbarHost = { SnackbarHost(snackbarHostState) { CoastSnackbar(it) } },
                bottomBar = {
                    val backStack by navController.currentBackStack.collectAsState()
                    // A study session is a focus mode, so it gets the whole screen.
                    val inStudySession = backStack.lastOrNull()?.destination?.hasRoute<StudyScreenRoute>() == true
                    AnimatedVisibility(
                        visible = !inStudySession,
                        enter = expandVertically(tween(TRANSITION_MILLIS)),
                        exit = shrinkVertically(tween(TRANSITION_MILLIS))
                    ) {
                        Column {
                            HorizontalDivider(color = CardEdge, thickness = 1.dp)
                            NavigationBar(containerColor = NavGround, tonalElevation = 0.dp) {
                                // A screen on top of a study session belongs to the tab the session was opened from
                                // (My Coasts or Create's review button), so select the tab of the newest screen that has one.
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
                                            selectedIconColor = Sunlit,
                                            indicatorColor = Color.Transparent,
                                            selectedTextColor = Sunlit,
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
                NavHost(
                    navController = navController,
                    startDestination = CreateScreenRoute,
                    modifier = Modifier.padding(innerPadding),
                    // Tabs cross-fade; screens opened from a list slide in from the side.
                    enterTransition = { fadeIn(tween(TRANSITION_MILLIS)) },
                    exitTransition = { fadeOut(tween(TRANSITION_MILLIS)) }
                ) {
                    composable<CreateScreenRoute> {
                        CreateScreen(onStartReview = { navController.navigate(StudyScreenRoute.Today) })
                    }
                    composable<MyCoastScreenRoute> {
                        MyCoastScreen(
                            onCoastClick = { id, name ->
                                navController.navigate(CoastScreenRoute(coastId = id, coastName = name))
                            },
                            onSearchClick = { navController.navigate(SearchScreenRoute) }
                        )
                    }
                    composable<SearchScreenRoute>(
                        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(TRANSITION_MILLIS)) + fadeIn() },
                        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(TRANSITION_MILLIS)) + fadeOut() }
                    ) {
                        SearchScreen(onNavigateBack = { navController.popBackStack() })
                    }
                    composable<CoastScreenRoute>(
                        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(TRANSITION_MILLIS)) + fadeIn() },
                        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(TRANSITION_MILLIS)) + fadeOut() }
                    ) {
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
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable<IslandScreenRoute>(
                        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(TRANSITION_MILLIS)) + fadeIn() },
                        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(TRANSITION_MILLIS)) + fadeOut() }
                    ) {
                        // IslandViewModel reads the route arguments from its SavedStateHandle.
                        IslandScreen(onNavigateBack = { navController.popBackStack() })
                    }
                    composable<SettingsScreenRoute> { SettingsScreen() }
                    composable<StudyScreenRoute>(
                        enterTransition = { slideIntoContainer(SlideDirection.Up, tween(TRANSITION_MILLIS)) + fadeIn() },
                        popExitTransition = { slideOutOfContainer(SlideDirection.Down, tween(TRANSITION_MILLIS)) + fadeOut() }
                    ) { entry ->
                        val route = entry.toRoute<StudyScreenRoute>()
                        // StudyViewModel reads the route arguments from its SavedStateHandle.
                        StudyScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onShowCards = if (route.scope == StudyScreenRoute.SCOPE_ISLAND) {
                                { navController.navigate(IslandScreenRoute(islandId = route.islandId, islandName = route.islandName)) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}
