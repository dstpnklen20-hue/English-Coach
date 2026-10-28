package com.englishcoach.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.englishcoach.app.AppContainer
import com.englishcoach.app.ui.screens.PracticeScreen
import com.englishcoach.app.ui.screens.ProgressScreen
import com.englishcoach.app.ui.screens.SettingsScreen
import com.englishcoach.app.ui.screens.TodayScreen
import com.englishcoach.app.ui.screens.WordsScreen

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer не передан") }

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "Сегодня", Icons.Outlined.TaskAlt),
    Words("words", "Слова", Icons.Outlined.Style),
    Practice("practice", "Практика", Icons.Outlined.School),
    Progress("progress", "Прогресс", Icons.Outlined.BarChart),
    Settings("settings", "Настройки", Icons.Outlined.Settings),
}

/** Телефон — нижняя панель; планшет (ширина ≥ 600dp) — боковая панель и контент ограниченной ширины. */
@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    if (wide) {
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { tab ->
                    NavigationRailItem(
                        selected = route == tab.route,
                        onClick = { nav.goTo(tab.route) },
                        icon = { Icon(tab.icon, null) },
                        label = { Text(tab.label) },
                    )
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                AppNavHost(nav, Modifier.widthIn(max = 840.dp))
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.goTo(tab.route) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label, maxLines = 1) },
                        )
                    }
                }
            },
        ) { padding ->
            AppNavHost(nav, Modifier.padding(padding))
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, modifier: Modifier) {
    NavHost(nav, startDestination = Tab.Today.route, modifier = modifier) {
        composable(Tab.Today.route) { TodayScreen() }
        composable(Tab.Words.route) { WordsScreen() }
        composable(Tab.Practice.route) { PracticeScreen() }
        composable(Tab.Progress.route) { ProgressScreen() }
        composable(Tab.Settings.route) { SettingsScreen() }
    }
}

private fun NavHostController.goTo(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
