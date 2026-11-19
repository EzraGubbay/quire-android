package com.ezragubbay.quire.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ezragubbay.quire.APP_ROOT_TAG
import com.ezragubbay.quire.feature.chat.ChatsScreen
import com.ezragubbay.quire.feature.courses.CoursesScreen
import com.ezragubbay.quire.feature.downloads.DownloadsScreen
import com.ezragubbay.quire.feature.settings.SettingsScreen

const val ACTION_SETTINGS_TAG = "action-settings"

/**
 * The app shell: top bar (Settings action), bottom bar (Courses, Downloads, Chats) and the nav host.
 * Settings is pushed over the shell with a back arrow instead of the bottom bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val onSettings = destination?.hasRoute<SettingsRoute>() == true
    val current = TopLevelDestination.entries.firstOrNull { destination?.hasRoute(it.route::class) == true }

    Scaffold(
        modifier = Modifier.testTag(APP_ROOT_TAG),
        topBar = {
            TopAppBar(
                title = { Text(if (onSettings) "Settings" else current?.label.orEmpty()) },
                navigationIcon = {
                    if (onSettings) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!onSettings) {
                        IconButton(
                            onClick = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                            modifier = Modifier.testTag(ACTION_SETTINGS_TAG),
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!onSettings) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == current,
                            onClick = { navController.navigateToTopLevel(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag(tab.navTag),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Start.route,
            modifier = Modifier.padding(padding),
        ) {
            composable<CoursesRoute> { CoursesScreen() }
            composable<DownloadsRoute> { DownloadsScreen() }
            composable<ChatsRoute> { ChatsScreen() }
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}

/** Switches tab keeping each tab's back stack and state; Courses stays at the bottom so back returns to it. */
private fun NavHostController.navigateToTopLevel(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
