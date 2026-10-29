package com.englishcoach.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishcoach.app.data.Settings
import com.englishcoach.app.data.Streak
import com.englishcoach.app.data.db.DayTotal
import com.englishcoach.app.data.practice.PracticeKind
import com.englishcoach.app.data.progress.Achievements
import com.englishcoach.app.data.progress.GoalPlan
import com.englishcoach.app.data.progress.LEVEL_WORDS
import com.englishcoach.app.data.progress.Stats
import com.englishcoach.app.data.progress.bestStreak
import com.englishcoach.app.data.progress.goal
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.components.ScreenColumn
import com.englishcoach.app.ui.theme.Success
import com.englishcoach.app.ui.theme.Warm
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val KNOWN_DAYS = 21.0
private const val PASS = 70
private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@Composable
fun ProgressScreen() {
    val c = LocalAppContainer.current
    val settings = remember { c.settings.flow }.collectAsStateWithLifecycle(null).value ?: return
    val words by remember { c.db.wordDao().progressFlow() }.collectAsStateWithLifecycle(emptyList())
    val days by remember { c.study.dayTotalsFlow() }.collectAsStateWithLifecycle(emptyList())
    val grammar by remember { c.practice.bestScores(PracticeKind.GRAMMAR) }.collectAsStateWithLifecycle(emptyList())
    val reading by remember { c.practice.bestScores(PracticeKind.READING) }.collectAsStateWithLifecycle(emptyList())
    val listening by remember { c.practice.bestScores(PracticeKind.LISTENING) }.collectAsStateWithLifecycle(emptyList())
    val speaking by remember { c.practice.bestScores(PracticeKind.SPEAKING) }.collectAsStateWithLifecycle(emptyList())
    val dialogs by remember { c.practice.bestScores(PracticeKind.DIALOG) }.collectAsStateWithLifecycle(emptyList())
    val chats by remember { c.practice.bestScores(PracticeKind.AI_CHAT) }.collectAsStateWithLifecycle(emptyList())
    val totals by produceState(Triple(0, 0, 0)) {
        value = Triple(c.practice.grammarLessons().size, c.practice.readingTexts().size, c.practice.dialogs().size)
    }

    val today = LocalDate.now()
    val started = words.count { it.maxReps > 0 }
    val known = words.count { it.maxReps > 0 && it.minStability >= KNOWN_DAYS }
    val stats = Stats(
        answers = days.sumOf { it.answers },
        totalMinutes = (days.sumOf { it.ms } / 60_000).toInt(),
        streak = Streak.compute(days, today),
        bestStreak = bestStreak(days),
        knownWords = known,
        startedWords = started,
        grammarPassed = grammar.count { it.best >= PASS },
        grammarTotal = totals.first,
        dialogsPassed = dialogs.count { it.best >= PASS },
        aiChats = chats.size,
        importedWords = words.count { it.word.source == "import" },
    )

    ScreenColumn(title = "Прогресс", subtitle = "Уровень ${settings.level}") {
        GoalCard(settings, started)

        Section("Время занятий")
        Panel {
            MinutesChart(days, today, goalMinutes = settings.sessionMinutes)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Fact("Всего", "${stats.totalMinutes / 60} ч ${stats.totalMinutes % 60} мин")
                Fact("Серия", "${stats.streak}")
                Fact("Лучшая", "${stats.bestStreak}")
            }
        }

        Section("Навыки")
        Panel {
            Skill("Слова", "начато $started · выучено $known", started.toFloat() / (settings.goalWords.takeIf { it > 0 } ?: LEVEL_WORDS.getValue("A2")))
            Skill("Грамматика", "уроков на 70%+: ${stats.grammarPassed} из ${totals.first}", ratio(stats.grammarPassed, totals.first))
            Skill("Чтение", "текстов на 70%+: ${reading.count { it.best >= PASS }} из ${totals.second}", ratio(reading.count { it.best >= PASS }, totals.second))
            Skill("Аудирование", "лучший диктант: ${listening.maxOfOrNull { it.best } ?: 0}%", (listening.maxOfOrNull { it.best } ?: 0) / 100f)
            val speech = maxOf(speaking.maxOfOrNull { it.best } ?: 0, ratio(stats.dialogsPassed, totals.third).times(100).toInt())
            Skill("Речь", "произношение и диалоги: $speech%", speech / 100f)
        }

        Section("Достижения")
        Achievements.compute(stats).sortedByDescending { it.unlocked }.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Icon(Icons.Outlined.EmojiEvents, null, tint = if (a.unlocked) Warm else MaterialTheme.colorScheme.outline, modifier = Modifier.size(28.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(a.title, style = MaterialTheme.typography.titleSmall, color = if (a.unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                    Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun ratio(a: Int, b: Int) = if (b <= 0) 0f else a.toFloat() / b

/** Цель с дедлайном: план к сегодняшнему дню, темп и предложение его подстроить. */
@Composable
private fun GoalCard(s: Settings, started: Int) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    val goal = s.goal()
    Panel {
        if (goal == null) {
            Text("Цель не поставлена", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Поставьте уровень и срок — приложение рассчитает план и подскажет, успеваете ли вы.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { editing = true }) { Text("Поставить цель") }
        } else {
            val st = GoalPlan.status(goal, started, s.newWordsPerDay, LocalDate.now())
            Text(
                "Цель: ${goal.level} · ${goal.target} слов к ${goal.deadline.format(DATE)}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            )
            LinearProgressIndicator(
                progress = { (started.toFloat() / goal.target).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = if (st.onTrack) Success else Warm,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                "Начато слов: $started · по плану к сегодня: ${st.expectedNow} · осталось дней: ${st.daysLeft}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                st.reached -> Text("Цель достигнута! Поставьте следующую.", color = Success, style = MaterialTheme.typography.bodyMedium)
                st.onTrack -> Text("Идёте по плану: нужно ≈ ${st.needPerWeek} слов в неделю, настроено ${st.plannedPerWeek}.", color = Success, style = MaterialTheme.typography.bodyMedium)
                else -> {
                    Text(
                        "Отстаёте: нужно ≈ ${st.needPerWeek} новых слов в неделю, сейчас настроено ${st.plannedPerWeek}.",
                        color = Warm, style = MaterialTheme.typography.bodyMedium,
                    )
                    st.suggestPerDay?.let { n ->
                        Button(onClick = { scope.launch { c.settings.update { it.copy(newWordsPerDay = n) } } }) { Text("Ставить $n новых слов в день") }
                    }
                    st.realisticDate?.let { d ->
                        Text("Даже ${GoalPlan.MAX_PER_DAY} слов в день не хватит. Реальный срок ≈ ${d.format(DATE)}.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { scope.launch { c.settings.update { it.copy(goalDeadline = d.toEpochDay()) } } }) { Text("Перенести срок") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { editing = true }) { Text("Изменить") }
                TextButton(onClick = { scope.launch { c.settings.update { it.copy(goalDeadline = 0, goalWords = 0, goalLevel = "") } } }) { Text("Убрать цель") }
            }
        }
    }
    if (editing) {
        GoalDialog(s, onDismiss = { editing = false }) { level, target, deadline ->
            editing = false
            scope.launch {
                c.settings.update {
                    it.copy(
                        goalLevel = level, goalWords = target, goalDeadline = deadline.toEpochDay(),
                        goalStartDay = LocalDate.now().toEpochDay(), goalStartWords = started,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GoalDialog(s: Settings, onDismiss: () -> Unit, onSave: (String, Int, LocalDate) -> Unit) {
    var level by remember { mutableStateOf(s.goalLevel.ifEmpty { "A2" }) }
    var words by remember { mutableStateOf((s.goalWords.takeIf { it > 0 } ?: LEVEL_WORDS.getValue("A2")).toString()) }
    var deadline by remember { mutableStateOf(if (s.goalDeadline > 0) LocalDate.ofEpochDay(s.goalDeadline) else LocalDate.now().plusMonths(4)) }
    var picking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Цель") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Уровень", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LEVEL_WORDS.forEach { (l, n) ->
                        FilterChip(selected = level == l, onClick = { level = l; words = n.toString() }, label = { Text(l) })
                    }
                }
                OutlinedTextField(
                    value = words, onValueChange = { words = it.filter(Char::isDigit).take(5) },
                    label = { Text("Слов в словаре") }, singleLine = true,
                    supportingText = { Text("Примерный запас уровня; можно поменять") },
                )
                OutlinedButton(onClick = { picking = true }) { Text("Срок: ${deadline.format(DATE)}") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(level, words.toIntOrNull() ?: 0, deadline) }, enabled = (words.toIntOrNull() ?: 0) > 0 && deadline.isAfter(LocalDate.now())) {
                Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )

    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = deadline.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { deadline = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text("Готово") }
            },
        ) { DatePicker(state = state) }
    }
}

/** Столбики минут за 14 дней; линия — дневная цель. */
@Composable
private fun MinutesChart(days: List<DayTotal>, today: LocalDate, goalMinutes: Int) {
    val byDay = days.associate { it.day to it.ms / 60_000f }
    val values = (13 downTo 0).map { byDay[today.minusDays(it.toLong()).toString()] ?: 0f }
    val max = maxOf(values.maxOrNull() ?: 0f, goalMinutes.toFloat(), 1f)
    val bar = MaterialTheme.colorScheme.primary
    val done = Success
    val line = MaterialTheme.colorScheme.outline
    Text("Минуты за 14 дней", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val slot = size.width / values.size
        val w = slot * 0.6f
        values.forEachIndexed { i, v ->
            val h = size.height * (v / max)
            drawRoundRect(
                color = if (v >= goalMinutes) done else bar,
                topLeft = Offset(i * slot + (slot - w) / 2, size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(6f, 6f),
            )
        }
        val y = size.height * (1 - goalMinutes / max)
        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Skill(title: String, detail: String, value: Float) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { value.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            trackColor = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}
