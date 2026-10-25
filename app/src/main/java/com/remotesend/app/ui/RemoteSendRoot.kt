package com.remotesend.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remotesend.app.domain.model.ConsoleConnectionStatus
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.remotesend.app.ui.screens.console.ConsoleScreen
import com.remotesend.app.ui.screens.home.HomeScreen
import com.remotesend.app.ui.screens.installed.InstalledScreen
import com.remotesend.app.ui.screens.logs.LogsScreen
import com.remotesend.app.ui.screens.packages.PackagesScreen
import com.remotesend.app.ui.screens.queue.QueueScreen
import com.remotesend.app.ui.screens.settings.SettingsScreen

private enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Inicio", Icons.Outlined.Home),
    PACKAGES("packages", "Paquetes", Icons.Outlined.Inventory2),
    QUEUE("queue", "Cola", Icons.AutoMirrored.Outlined.List),
    CONSOLE("console", "Consola", Icons.Outlined.Wifi),
    SETTINGS("settings", "Ajustes", Icons.Outlined.Settings),
}

private const val LOGS_ROUTE = "logs"
private const val INSTALLED_ROUTE = "installed"

@Composable
fun RemoteSendRoot() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val container = LocalAppContainer.current
    val consoleConnection by container.consoleDetector.connection.collectAsStateWithLifecycle()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val connectedColor = if (dark) Color(0xFF7EE2A0) else Color(0xFF1B7F3A)

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            if (currentRoute != destination.route) {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            if (destination == Destination.CONSOLE) {
                                BadgedBox(
                                    badge = {
                                        if (consoleConnection.status ==
                                            ConsoleConnectionStatus.CONNECTED
                                        ) {
                                            Badge(containerColor = connectedColor)
                                        }
                                    },
                                ) {
                                    Icon(
                                        destination.icon,
                                        contentDescription = if (
                                            consoleConnection.status ==
                                            ConsoleConnectionStatus.CONNECTED
                                        ) {
                                            "Consola conectada"
                                        } else {
                                            destination.label
                                        },
                                    )
                                }
                            } else {
                                Icon(destination.icon, contentDescription = destination.label)
                            }
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            NavHost(
                navController = navController,
                startDestination = Destination.HOME.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(Destination.HOME.route) {
                    HomeScreen(
                        onOpenPackages = { navigateTab(navController, Destination.PACKAGES.route) },
                        onOpenQueue = { navigateTab(navController, Destination.QUEUE.route) },
                        onOpenConsole = { navigateTab(navController, Destination.CONSOLE.route) },
                        onOpenLogs = { navController.navigate(LOGS_ROUTE) },
                    )
                }
                composable(Destination.PACKAGES.route) {
                    PackagesScreen(
                        onOpenInstalled = { navController.navigate(INSTALLED_ROUTE) },
                    )
                }
                composable(Destination.QUEUE.route) {
                    QueueScreen(
                        onOpenPackages = { navigateTab(navController, Destination.PACKAGES.route) },
                        onOpenConsole = { navigateTab(navController, Destination.CONSOLE.route) },
                    )
                }
                composable(Destination.CONSOLE.route) {
                    ConsoleScreen()
                }
                composable(Destination.SETTINGS.route) {
                    SettingsScreen(
                        onOpenLogs = { navController.navigate(LOGS_ROUTE) },
                    )
                }
                composable(LOGS_ROUTE) {
                    LogsScreen(onBack = { navController.popBackStack() })
                }
                composable(INSTALLED_ROUTE) {
                    InstalledScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

private fun navigateTab(
    navController: androidx.navigation.NavHostController,
    route: String,
) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
