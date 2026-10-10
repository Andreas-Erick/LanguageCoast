package com.andreaserick.languagecoast

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.andreaserick.languagecoast.navigation.CoastScreenRoute
import com.andreaserick.languagecoast.navigation.CreateScreenRoute
import com.andreaserick.languagecoast.navigation.MyCoastScreenRoute
import com.andreaserick.languagecoast.navigation.SettingsScreenRoute
import com.andreaserick.languagecoast.navigation.StudyScreenRoute
import com.andreaserick.languagecoast.notifications.ReminderScheduler
import com.andreaserick.languagecoast.ui.coast.CoastScreen
import com.andreaserick.languagecoast.ui.components.CoastSnackbar
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.OceanBackground
import com.andreaserick.languagecoast.ui.components.UndoMessenger
import com.andreaserick.languagecoast.ui.create.CreateScreen
import com.andreaserick.languagecoast.ui.mycoast.MyCoastScreen
import com.andreaserick.languagecoast.ui.settings.SettingsScreen
import com.andreaserick.languagecoast.ui.study.StudyScreen
import com.andreaserick.languagecoast.ui.theme.AbyssBlue
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.LanguageCoastTheme
import com.andreaserick.languagecoast.ui.theme.SandBeige
import com.andreaserick.languagecoast.ui.theme.SandMuted
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The app's only activity: shows the splash screen, then the Compose UI. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var reminderScheduler: ReminderScheduler

    /** Sets up the splash screen and edge-to-edge drawing, then shows the app. */
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // The app is always dark, so use light system bar icons over a transparent background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            LanguageCoastTheme {
                LanguageCoastApp(
                    scheduleReminders = { lifecycleScope.launch { reminderScheduler.ensureScheduled() } }
                )
            }
        }
    }
}

/** A tab in the bottom navigation bar. */
private data class TopLevelDestination(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val isSelected: (NavDestination) -> Boolean
)

private val topLevelDestinations = listOf(
    TopLevelDestination(CreateScreenRoute, "Create", Icons.Default.EditNote) { it.hasRoute<CreateScreenRoute>() },
    // A coast is only opened from My Coasts, so it keeps that tab selected.
    TopLevelDestination(MyCoastScreenRoute, "My Coasts", Icons.Default.Sailing) {
        it.hasRoute<MyCoastScreenRoute>() || it.hasRoute<CoastScreenRoute>()
    },
    TopLevelDestination(SettingsScreenRoute, "Settings", Icons.Default.Tune) { it.hasRoute<SettingsScreenRoute>() }
)

private const val TRANSITION_MILLIS = 300

/**
 * @param scheduleReminders Called once notifications may be shown, to schedule the study reminder.
 */
@Composable
fun LanguageCoastApp(scheduleReminders: () -> Unit) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val rootScope = rememberCoroutineScope()
    val undoMessenger = remember { UndoMessenger(snackbarHostState, rootScope) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> if (isGranted) scheduleReminders() }
    )

    LaunchedEffect(Unit) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            scheduleReminders()
        }
    }

    CompositionLocalProvider(LocalUndoMessenger provides undoMessenger) {
        OceanBackground {
            Scaffold(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) { CoastSnackbar(it) } },
                bottomBar = {
                    // Matches the bottom of the background gradient.
                    NavigationBar(containerColor = AbyssBlue) {
                        // A study session belongs to the tab it was opened from (My Coasts or Create's
                        // "Start reviewing"), so select the tab of the newest screen that has one.
                        val backStack by navController.currentBackStack.collectAsState()
                        val tabDestination = backStack.lastOrNull { entry ->
                            topLevelDestinations.any { tab -> entry.destination.hierarchy.any(tab.isSelected) }
                        }?.destination

                        topLevelDestinations.forEach { destination ->
                            NavigationBarItem(
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(destination.label) },
                                selected = tabDestination?.hierarchy?.any(destination.isSelected) == true,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = DeepOceanBlue,
                                    indicatorColor = SandBeige,
                                    selectedTextColor = SandBeige,
                                    unselectedIconColor = SandMuted,
                                    unselectedTextColor = SandMuted
                                )
                            )
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
                        CreateScreen(
                            onStartReview = { id, name ->
                                navController.navigate(StudyScreenRoute(islandId = id, islandName = name))
                            }
                        )
                    }
                    composable<MyCoastScreenRoute> {
                        MyCoastScreen(
                            onCoastClick = { id, name ->
                                navController.navigate(CoastScreenRoute(coastId = id, coastName = name))
                            }
                        )
                    }
                    composable<CoastScreenRoute>(
                        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(TRANSITION_MILLIS)) + fadeIn() },
                        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(TRANSITION_MILLIS)) + fadeOut() }
                    ) {
                        // CoastViewModel reads the route arguments from its SavedStateHandle.
                        CoastScreen(
                            onIslandClick = { id, name ->
                                navController.navigate(StudyScreenRoute(islandId = id, islandName = name))
                            },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable<SettingsScreenRoute> { SettingsScreen() }
                    composable<StudyScreenRoute>(
                        enterTransition = { slideIntoContainer(SlideDirection.Up, tween(TRANSITION_MILLIS)) + fadeIn() },
                        popExitTransition = { slideOutOfContainer(SlideDirection.Down, tween(TRANSITION_MILLIS)) + fadeOut() }
                    ) {
                        // StudyViewModel reads the route arguments from its SavedStateHandle.
                        StudyScreen(onNavigateBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
