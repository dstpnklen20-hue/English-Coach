package com.englishcoach.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import com.englishcoach.app.ui.theme.Motion
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
import com.englishcoach.app.ui.placement.PlacementScreen
import com.englishcoach.app.ui.review.ReviewScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishcoach.app.ui.importer.ImportScreen
import com.englishcoach.app.ui.ai.AiChatScreen
import com.englishcoach.app.ui.ai.AiExercisesScreen
import com.englishcoach.app.ui.ai.TextCheckScreen
import com.englishcoach.app.ui.practice.DialogScreen
import com.englishcoach.app.ui.practice.GrammarScreen
import com.englishcoach.app.ui.practice.SpeakingScreen
import com.englishcoach.app.ui.practice.ListeningScreen
import com.englishcoach.app.ui.practice.PracticeScreen
import com.englishcoach.app.ui.practice.ReadingScreen
import com.englishcoach.app.ui.screens.ProgressScreen
import com.englishcoach.app.ui.screens.SettingsScreen
import com.englishcoach.app.ui.screens.TodayScreen
import com.englishcoach.app.ui.screens.WordsScreen

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer не передан") }

private const val REVIEW_ROUTE = "review"
private const val PLACEMENT_ROUTE = "placement"
private const val LISTENING_ROUTE = "listening"
private const val SPEAKING_ROUTE = "speaking"
private const val AI_CHAT_ROUTE = "ai-chat"
private const val TEXT_CHECK_ROUTE = "ai-text"
private const val AI_EXERCISES_ROUTE = "ai-exercises"
private const val IMPORT_ROUTE = "import"

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
    // Пришёл текст через «Поделиться» — сразу на экран импорта
    val shared by LocalAppContainer.current.pendingShare.collectAsStateWithLifecycle()
    LaunchedEffect(shared) { if (shared != null && route != IMPORT_ROUTE) nav.navigate(IMPORT_ROUTE) }
    // Повторение, тест и упражнения — полноэкранные, без панели разделов, чтобы ничего не отвлекало
    val showNav = Tab.entries.any { it.route == route } || route == null
    // Один и тот же NavHost при любой раскладке: раньше при входе в полноэкранный режим он пересоздавался
    // с нуля (рывок и оборванная анимация); movableContentOf переносит его вместе с состоянием
    val host = remember(nav) { movableContentOf { m: Modifier -> AppNavHost(nav, m) } }
    val content = Modifier.widthIn(max = if (showNav) 840.dp else 720.dp)

    if (wide) {
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            AnimatedVisibility(
                showNav,
                enter = fadeIn(Motion.soft()) + expandHorizontally(Motion.soft()),
                exit = fadeOut(Motion.soft(160)) + shrinkHorizontally(Motion.soft()),
            ) {
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
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { host(content) }
        }
    } else {
        Scaffold(
            bottomBar = {
                // Панель разделов плавно уезжает вниз на полноэкранных экранах и возвращается обратно
                AnimatedVisibility(
                    showNav,
                    enter = fadeIn(Motion.soft()) + expandVertically(Motion.soft(), expandFrom = Alignment.Top),
                    exit = fadeOut(Motion.soft(160)) + shrinkVertically(Motion.soft(), shrinkTowards = Alignment.Top),
                ) {
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
                }
            },
        ) { padding ->
            // Отступы системных панелей отдаются здесь и помечаются использованными — полноэкранные экраны не добавят их второй раз
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
                host(content)
            }
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, modifier: Modifier) {
    NavHost(
        nav,
        startDestination = Tab.Today.route,
        modifier = modifier,
        enterTransition = { Motion.screenEnter },
        exitTransition = { Motion.screenExit },
        popEnterTransition = { Motion.screenEnter },
        popExitTransition = { Motion.screenPopExit },
    ) {
        composable(Tab.Today.route) {
            TodayScreen(
                onStudyWords = { nav.navigate(REVIEW_ROUTE) },
                onPlacement = { nav.navigate(PLACEMENT_ROUTE) },
                onPractice = { nav.goTo(Tab.Practice.route) },
            )
        }
        composable(REVIEW_ROUTE) { ReviewScreen(onClose = { nav.popBackStack() }) }
        composable(PLACEMENT_ROUTE) { PlacementScreen(onClose = { nav.popBackStack() }) }
        composable(Tab.Words.route) { WordsScreen(onImport = { nav.navigate(IMPORT_ROUTE) }) }
        composable(IMPORT_ROUTE) { ImportScreen(onClose = { nav.popBackStack() }) }
        composable(Tab.Practice.route) {
            PracticeScreen(
                onGrammar = { nav.navigate("grammar/$it") },
                onReading = { nav.navigate("reading/$it") },
                onListening = { nav.navigate(LISTENING_ROUTE) },
                onSpeaking = { nav.navigate(SPEAKING_ROUTE) },
                onDialog = { nav.navigate("dialog/$it") },
                onAiChat = { nav.navigate(AI_CHAT_ROUTE) },
                onTextCheck = { nav.navigate(TEXT_CHECK_ROUTE) },
                onAiExercises = { nav.navigate(AI_EXERCISES_ROUTE) },
            )
        }
        composable(AI_CHAT_ROUTE) { AiChatScreen(onClose = { nav.popBackStack() }) }
        composable(TEXT_CHECK_ROUTE) { TextCheckScreen(onClose = { nav.popBackStack() }) }
        composable(AI_EXERCISES_ROUTE) { AiExercisesScreen(onClose = { nav.popBackStack() }) }
        composable(SPEAKING_ROUTE) { SpeakingScreen(onClose = { nav.popBackStack() }) }
        composable("dialog/{id}") { DialogScreen(it.arguments?.getString("id").orEmpty(), onClose = { nav.popBackStack() }) }
        composable("grammar/{id}") { GrammarScreen(it.arguments?.getString("id").orEmpty(), onClose = { nav.popBackStack() }) }
        composable("reading/{id}") { ReadingScreen(it.arguments?.getString("id").orEmpty(), onClose = { nav.popBackStack() }) }
        composable(LISTENING_ROUTE) { ListeningScreen(onClose = { nav.popBackStack() }) }
        composable(Tab.Progress.route) { ProgressScreen() }
        composable(Tab.Settings.route) { SettingsScreen(onPlacement = { nav.navigate(PLACEMENT_ROUTE) }) }
    }
}

private fun NavHostController.goTo(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
