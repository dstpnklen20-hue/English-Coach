package com.englishcoach.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishcoach.app.data.practice.AutoLevel
import com.englishcoach.app.data.practice.Difficulty
import com.englishcoach.app.data.practice.Exercise
import com.englishcoach.app.data.practice.GroupItem
import com.englishcoach.app.data.practice.Groups
import com.englishcoach.app.data.practice.PracticeKind
import com.englishcoach.app.data.practice.WordGroup
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.theme.Success
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Выбор уровня сложности для вида задания; уровень запоминается и сам растёт после серии хороших результатов. */
@Composable
fun DifficultyPicker(kind: String) {
    val c = LocalAppContainer.current
    val levels by c.difficulty.levels.collectAsStateWithLifecycle()
    val level = levels[kind] ?: Difficulty.NORMAL
    Text("Сложность", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Difficulty.entries.forEach { d ->
            FilterChip(selected = d == level, onClick = { c.difficulty.set(kind, d) }, label = { Text(d.label) })
        }
    }
    Text(
        "${level.label}: ${level.about}." + if (level.next() != null) " После ${AutoLevel.STREAK} результатов от ${AutoLevel.GOOD_PERCENT}% подряд уровень повысится сам." else "",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Сообщение под итогом, если уровень только что повысился. */
@Composable
fun RaisedNote(level: Difficulty?) {
    if (level != null) {
        Text("Отличная серия! Уровень повышен: «${level.label}».", style = MaterialTheme.typography.titleMedium, color = Success)
    }
}

/** Группа слов: что в ней, что уже выучено, озвучка и кнопка тренировки. */
@Composable
fun GroupScreen(groupId: String, onTrain: () -> Unit, onClose: () -> Unit) {
    val c = LocalAppContainer.current
    val group by produceState<WordGroup?>(null, groupId) { value = c.practice.groups().firstOrNull { it.id == groupId } }
    val net by remember { c.practice.groupNetFlow() }.collectAsStateWithLifecycle(emptyMap())
    val g = group ?: return
    val mastered = Groups.mastered(g, net)
    PracticeFrame(progress = null, onClose = onClose) {
        Text(g.level, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(g.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(g.about, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Выучено $mastered из ${g.items.size}", style = MaterialTheme.typography.titleMedium, color = if (mastered == g.items.size) Success else MaterialTheme.colorScheme.onSurface)
        DifficultyPicker(PracticeKind.GROUP)
        Button(onClick = onTrain, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Тренировка") }
        Panel {
            g.items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                GroupItemRow(item, done = (net[Groups.itemId(g, item)] ?: 0) >= Groups.MASTERED_NET) {
                    c.tts.speak(if (item.forms.isNotEmpty()) item.forms.joinToString(", ") { it.replace("/", " or ") } else item.en)
                }
            }
        }
    }
}

@Composable
private fun GroupItemRow(item: GroupItem, done: Boolean, onSpeak: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(
            if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
            tint = if (done) Success else MaterialTheme.colorScheme.outline,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(item.ru, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (item.example.isNotBlank()) {
                Text(item.example, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onSpeak) { Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Озвучить", tint = MaterialTheme.colorScheme.primary) }
    }
}

/**
 * Тренировка группы: сначала невыученные слова; вид упражнения чередуется (формы, перевод, пропуск, сборка фразы).
 * Лёгкий — 6 слов и только выбор; обычный — 10; сложный — 12 и ввод с клавиатуры.
 */
@Composable
fun GroupTrainingScreen(groupId: String, onClose: () -> Unit) {
    val c = LocalAppContainer.current
    var attempt by remember { mutableIntStateOf(0) }
    val level = remember(attempt) { c.difficulty.get(PracticeKind.GROUP) }
    val session by produceState<Pair<WordGroup, List<Pair<GroupItem, Exercise>>>?>(null, groupId, attempt) {
        val g = c.practice.groups().firstOrNull { it.id == groupId } ?: return@produceState
        value = g to plan(g, c.practice.groupNetFlow().first(), level)
    }
    val (g, tasks) = session ?: return
    val n = tasks.size
    var idx by remember(attempt) { mutableIntStateOf(0) }
    var correct by remember(attempt) { mutableIntStateOf(0) }
    var raised by remember(attempt) { mutableStateOf<Difficulty?>(null) }
    val startedAt by remember(attempt) { mutableLongStateOf(System.currentTimeMillis()) }

    PracticeFrame(progress = idx.toFloat() / n, onClose = onClose) {
        if (idx >= n) {
            RaisedNote(raised)
            ResultBlock(correct, n, onRetry = { attempt++ }, onClose = onClose)
            return@PracticeFrame
        }
        val (item, ex) = tasks[idx]
        Text("${g.title} · ${idx + 1} из $n · ${level.label.lowercase()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        key(attempt, idx) {
            ExerciseView(ex) { ok ->
                if (ok) correct++
                val score = correct
                c.appScope.launch {
                    c.practice.log(PracticeKind.GROUP_ITEM, Groups.itemId(g, item), if (ok) 1 else 0, 1, 0)
                    if (idx + 1 == n) {
                        c.practice.log(PracticeKind.GROUP, g.id, score, n, System.currentTimeMillis() - startedAt)
                        raised = c.difficulty.record(PracticeKind.GROUP, score, n)
                    }
                }
                idx++
            }
        }
    }
}

/** Слова и упражнения на одну тренировку по уровню сложности. */
private fun plan(g: WordGroup, net: Map<String, Int>, level: Difficulty): List<Pair<GroupItem, Exercise>> {
    // На лёгком нет ввода форм — только выбор и сборка; если других видов нет, остаётся пропуск с выбором
    val modes = if (level == Difficulty.EASY) (g.modes - "forms").ifEmpty { listOf("gap") } else g.modes
    val count = when (level) {
        Difficulty.EASY -> 6
        Difficulty.NORMAL -> Groups.SESSION
        Difficulty.HARD -> 12
    }
    return Groups.pick(g, net, count).mapIndexed { i, item ->
        item to Groups.exercise(g, item, modes[i % modes.size], hard = level == Difficulty.HARD)
    }
}
