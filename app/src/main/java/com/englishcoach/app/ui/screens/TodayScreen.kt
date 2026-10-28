package com.englishcoach.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.components.ScreenColumn
import com.englishcoach.app.ui.theme.Success
import com.englishcoach.app.ui.theme.Warm

@Composable
fun TodayScreen(onStudyWords: () -> Unit) {
    val study = LocalAppContainer.current.study
    val due by remember { study.dueCountFlow(System.currentTimeMillis()) }.collectAsStateWithLifecycle(0)
    val new by remember { study.newTodayCountFlow() }.collectAsStateWithLifecycle(0)

    ScreenColumn(title = "Сегодня", subtitle = "Уровень A1 · цель — свободный разговор") {
        Panel {
            // Тонкая акцентная полоска сверху — «небольшая деталь» минималистичного дизайна
            Box(
                Modifier.fillMaxWidth().height(3.dp).clip(MaterialTheme.shapes.small)
                    .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, Success))),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { 0f },
                        modifier = Modifier.size(64.dp),
                        trackColor = MaterialTheme.colorScheme.outline,
                    )
                    Text("0%", style = MaterialTheme.typography.labelLarge)
                }
                Column(Modifier.weight(1f)) {
                    Text("Сессия дня", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "≈ 30 минут: повторение → новое → практика → речь",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Полная сессия дня — этап 2; пока кнопка запускает повторение слов
            Button(onClick = onStudyWords, enabled = due + new > 0, modifier = Modifier.fillMaxWidth()) {
                Text(if (due + new > 0) "Учить слова · ${due + new}" else "Слова на сегодня пройдены")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Повторить", due.toString(), Modifier.weight(1f))
            StatTile("Новых", new.toString(), Modifier.weight(1f))
            Panel(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocalFireDepartment, null, tint = Warm, modifier = Modifier.size(14.dp))
                    Box(Modifier.width(4.dp))
                    Text("Серия", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("0", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}
