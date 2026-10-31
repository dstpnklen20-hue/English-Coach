package com.englishcoach.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishcoach.app.data.db.WordProgress
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.theme.Success
import com.englishcoach.app.ui.theme.Warm
import kotlinx.coroutines.launch

/** Слово считается выученным, когда все его карточки держатся в памяти ≥ 21 дня. */
private const val KNOWN_STABILITY_DAYS = 21.0

/** Выучено — запомнено по повторениям; Знаю — отмечено вручную, в повторениях не показывается. */
private enum class Status(val label: String) { New("Новые"), Learning("Учу"), Learned("Выучено"), Known("Знаю") }

private fun WordProgress.status() = when {
    word.known -> Status.Known
    maxReps == 0 -> Status.New
    minStability >= KNOWN_STABILITY_DAYS -> Status.Learned
    else -> Status.Learning
}

@Composable
private fun Status.color(): Color = when (this) {
    Status.New -> MaterialTheme.colorScheme.outline
    Status.Learning -> Warm
    Status.Learned -> Success
    Status.Known -> MaterialTheme.colorScheme.primary
}

@Composable
fun WordsScreen(onImport: () -> Unit) {
    val c = LocalAppContainer.current
    val words by remember { c.db.wordDao().progressFlow() }.collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf<Status?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val topics = remember(words) { words.map { it.word.topic }.distinct() }
    val counts = remember(words) { words.groupingBy { it.status() }.eachCount() }
    val shown = remember(words, query, topic, status) {
        val q = query.trim().lowercase()
        words.filter {
            (topic == null || it.word.topic == topic) && (status == null || it.status() == status) &&
                (q.isEmpty() || q in it.word.en.lowercase() || q in it.word.ru.lowercase())
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Слова", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onImport) { Text("Импорт") }
                }
                // Счётчики статусов — они же фильтр: «Знаю» открывает список отмеченных слов, откуда их можно вернуть
                LazyRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Status.entries) { s ->
                        FilterChip(
                            selected = status == s,
                            onClick = { status = if (status == s) null else s },
                            label = { Text("${s.label}: ${counts[s] ?: 0}") },
                            leadingIcon = { Box(Modifier.size(8.dp).background(s.color(), CircleShape)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск по-английски или по-русски") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = topic == null, onClick = { topic = null }, label = { Text("Все") }) }
                    items(topics) { t ->
                        FilterChip(selected = topic == t, onClick = { topic = if (topic == t) null else t }, label = { Text(t) })
                    }
                }
            }
            if (status == Status.Known && shown.isEmpty()) {
                item {
                    Text(
                        "Здесь будут слова, отмеченные «знаю», — в повторении или кнопкой ✓ в списке. Они не показываются в карточках, пока вы не вернёте их в изучение.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(shown, key = { it.word.id }) { wp ->
                WordRow(wp, onClick = { c.tts.speak(wp.word.en) }, onKnown = { known -> scope.launch { c.study.setKnown(wp.word.id, known) } })
            }
        }
        FloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) { Icon(Icons.Outlined.Add, "Добавить слово") }
    }

    if (adding) {
        AddWordDialog(
            onDismiss = { adding = false },
            onAdd = { en, ru ->
                adding = false
                scope.launch { c.study.addUserWord(en, ru) }
            },
        )
    }
}

@Composable
private fun WordRow(wp: WordProgress, onClick: () -> Unit, onKnown: (Boolean) -> Unit) {
    val s = wp.status()
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(s.color(), CircleShape))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(wp.word.en, style = MaterialTheme.typography.titleMedium)
            Text(wp.word.ru, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (s == Status.Known) {
            TextButton(onClick = { onKnown(false) }) { Text("Вернуть в изучение") }
        } else {
            Text(wp.word.topic, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { onKnown(true) }) {
                Icon(Icons.Outlined.Done, "Знаю это слово", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AddWordDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var en by rememberSaveable { mutableStateOf("") }
    var ru by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое слово") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(en, { en = it }, label = { Text("По-английски") }, singleLine = true)
                OutlinedTextField(ru, { ru = it }, label = { Text("Перевод") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(en, ru) }, enabled = en.isNotBlank() && ru.isNotBlank()) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
