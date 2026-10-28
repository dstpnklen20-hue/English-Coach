package com.englishcoach.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Quiz
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishcoach.app.data.Streak
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.components.ScreenColumn
import com.englishcoach.app.ui.theme.Success
import com.englishcoach.app.ui.theme.Warm
import java.time.LocalDate

@Composable
fun TodayScreen(onStudyWords: () -> Unit, onPlacement: () -> Unit, onPractice: () -> Unit) {
    val c = LocalAppContainer.current
    val practiced by remember { c.practice.practicedTodayFlow() }.collectAsStateWithLifecycle(0)
    val due by remember { c.study.dueCountFlow(System.currentTimeMillis()) }.collectAsStateWithLifecycle(0)
    val new by remember { c.study.newTodayCountFlow() }.collectAsStateWithLifecycle(0)
    val settings by remember { c.settings.flow }.collectAsStateWithLifecycle(null)
    val days by remember { c.study.dayTotalsFlow() }.collectAsStateWithLifecycle(emptyList())

    val today = LocalDate.now()
    val todayMs = days.firstOrNull { it.day == today.toString() }?.ms ?: 0
    val goalMin = settings?.sessionMinutes ?: 30
    val minutes = (todayMs / 60_000).toInt()
    val streak = Streak.compute(days, today)

    ScreenColumn(title = "Сегодня", subtitle = "Уровень ${settings?.level ?: "A1"} · цель — свободный разговор") {
        if (settings?.placementDone == false) {
            Panel(Modifier.clickable(onClick = onPlacement)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Quiz, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "  Стартовый тест · 5 минут",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "Определим уровень и отметим слова, которые вы уже знаете, — чтобы не учить лишнее.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Panel {
            // Тонкая акцентная полоска сверху — «небольшая деталь» минималистичного дизайна
            Box(
                Modifier.fillMaxWidth().height(3.dp).clip(MaterialTheme.shapes.small)
                    .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, Success))),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { (minutes.toFloat() / goalMin).coerceIn(0f, 1f) },
                        modifier = Modifier.size(72.dp),
                        color = if (minutes >= goalMin) Success else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$minutes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("из $goalMin мин", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Сессия дня", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Step("Повторение", if (due > 0) "$due" else null, done = due == 0)
                    Step("Новые слова", if (new > 0) "$new" else null, done = new == 0)
                    Step("Практика", if (practiced > 0) "$practiced" else "открыть", done = practiced > 0, onClick = onPractice)
                    Step("Речь", "этап 4", locked = true)
                }
            }
            Button(onClick = onStudyWords, enabled = due + new > 0, modifier = Modifier.fillMaxWidth()) {
                Text(if (due + new > 0) "Начать · ${due + new} карточек" else "Слова на сегодня пройдены")
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
                Text("$streak", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            "День засчитывается в серию после 5 минут занятий.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Step(title: String, detail: String?, done: Boolean = false, locked: Boolean = false, onClick: (() -> Unit)? = null) {
    val (icon: ImageVector, tint) = when {
        locked -> Icons.Outlined.Lock to MaterialTheme.colorScheme.outline
        done -> Icons.Outlined.CheckCircle to Success
        else -> Icons.Outlined.RadioButtonUnchecked to MaterialTheme.colorScheme.primary
    }
    Row(
        if (onClick != null) Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onClick) else Modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            "  $title",
            style = MaterialTheme.typography.bodyMedium,
            color = if (locked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
