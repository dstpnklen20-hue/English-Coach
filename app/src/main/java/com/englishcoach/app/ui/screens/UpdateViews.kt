package com.englishcoach.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishcoach.app.BuildConfig
import com.englishcoach.app.data.update.Updater.State
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.theme.Motion
import kotlinx.coroutines.launch

/** Плашка «Доступна новая версия» на «Сегодня»: скачать → установить. Скрыта, пока обновлений нет. */
@Composable
fun UpdateBanner() {
    val c = LocalAppContainer.current
    val state by c.updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    AnimatedVisibility(state !is State.Idle, enter = Motion.appear, exit = Motion.disappear) {
        val s = state
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    when (s) {
                        is State.Available -> "Доступна версия ${s.info.versionName}"
                        is State.Downloading -> "Загружаю версию ${s.info.versionName}…"
                        is State.Ready -> "Версия ${s.info.versionName} загружена"
                        is State.Failed -> "Не удалось обновить"
                        State.Idle -> ""
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            val notes = when (s) {
                is State.Available -> s.info.notes
                is State.Failed -> s.message
                is State.Ready -> "Если Android попросит — разрешите приложению устанавливать обновления и нажмите «Установить» ещё раз. Прогресс сохранится."
                else -> ""
            }
            if (notes.isNotBlank()) {
                Text(notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (s) {
                is State.Available -> Button(onClick = { scope.launch { c.updater.download(s.info) } }, modifier = Modifier.fillMaxWidth()) { Text("Обновить") }
                is State.Downloading -> {
                    val p by animateFloatAsState(s.progress, Motion.soft(300), label = "download")
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.outlineVariant)
                }
                is State.Ready -> Button(onClick = c.updater::install, modifier = Modifier.fillMaxWidth()) { Text("Установить") }
                is State.Failed -> OutlinedButton(onClick = { scope.launch { c.updater.download(s.info) } }, modifier = Modifier.fillMaxWidth()) { Text("Попробовать снова") }
                State.Idle -> Unit
            }
        }
    }
}

/** Раздел «Приложение» в настройках: версии и ручная проверка обновлений. */
@Composable
fun UpdateSettings() {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    Panel {
        Text(
            "Версия ${BuildConfig.VERSION_NAME} · материалы курса v${c.content.manifest.version}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Обновления приходят с GitHub: новые уроки и слова — сами, новая версия приложения — по кнопке на «Сегодня».",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                checking = true
                scope.launch {
                    result = c.updater.check(manual = true)
                    checking = false
                }
            },
            enabled = !checking,
        ) { Text(if (checking) "Проверяю…" else "Проверить обновления") }
        result?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
