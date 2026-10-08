package com.andreaserick.languagecoast

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.andreaserick.languagecoast.navigation.CreateScreenRoute
import com.andreaserick.languagecoast.navigation.MyCoastScreenRoute
import com.andreaserick.languagecoast.navigation.SettingsScreenRoute
import com.andreaserick.languagecoast.navigation.StudyScreenRoute
import com.andreaserick.languagecoast.notifications.StudyReminderWorker
import com.andreaserick.languagecoast.ui.screens.CreateScreen
import com.andreaserick.languagecoast.ui.screens.MyCoastScreen
import com.andreaserick.languagecoast.ui.screens.SettingsScreen
import com.andreaserick.languagecoast.ui.screens.StudyScreen
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.LanguageCoastTheme
import com.andreaserick.languagecoast.ui.theme.SandBeige

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LanguageCoastTheme {
                LanguageCoastApp()
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
    TopLevelDestination(CreateScreenRoute, "Create", Icons.Default.AddCircle) { it.hasRoute<CreateScreenRoute>() },
    TopLevelDestination(MyCoastScreenRoute, "My Coast", Icons.AutoMirrored.Filled.List) { it.hasRoute<MyCoastScreenRoute>() },
    TopLevelDestination(SettingsScreenRoute, "Settings", Icons.Default.Settings) { it.hasRoute<SettingsScreenRoute>() }
)

@Composable
fun LanguageCoastApp() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) StudyReminderWorker.scheduleInitialReminder(context)
        }
    )

    LaunchedEffect(Unit) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            StudyReminderWorker.scheduleInitialReminder(context)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = DeepOceanBlue) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                topLevelDestinations.forEach { destination ->
                    NavigationBarItem(
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        selected = currentDestination?.hierarchy?.any(destination.isSelected) == true,
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
                            unselectedIconColor = SandBeige.copy(alpha = 0.6f),
                            unselectedTextColor = SandBeige.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = CreateScreenRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<CreateScreenRoute> { CreateScreen() }
            composable<MyCoastScreenRoute> {
                MyCoastScreen(
                    onIslandClick = { id, name ->
                        navController.navigate(StudyScreenRoute(islandId = id, islandName = name))
                    }
                )
            }
            composable<SettingsScreenRoute> { SettingsScreen() }
            composable<StudyScreenRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<StudyScreenRoute>()
                StudyScreen(
                    islandId = route.islandId,
                    islandName = route.islandName,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
