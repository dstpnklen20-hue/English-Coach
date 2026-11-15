package com.englishcoach.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishcoach.app.data.Settings
import com.englishcoach.app.data.TopicMode
import com.englishcoach.app.data.Topics
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.practice.PracticeFrame
import com.englishcoach.app.ui.theme.Success
import kotlinx.coroutines.launch

/** Какие темы учить первыми, а какие отложить. Повторение уже начатых слов от этого не зависит. */
@Composable
fun TopicsScreen(onClose: () -> Unit) {
    val c = LocalAppContainer.current
    val settings by remember { c.settings.flow }.collectAsStateWithLifecycle(null)
    val topics by remember { c.db.wordDao().topicsFlow() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val s = settings ?: return
    val update = { f: (Settings) -> Settings -> scope.launch { c.settings.update(transform = f) }; Unit }

    PracticeFrame(progress = null, onClose = onClose) {
        Text("Темы новых слов", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Новые слова сначала берутся из тем «Сначала». Темы «Позже» не дают новых слов, пока вы их не вернёте. " +
                "Уже начатые слова из любых тем повторяются как обычно.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { update { Topics.studyPreset(it, topics.map { t -> t.topic }) } },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Под учёбу и общение") }
        Text(
            "База, университет и общение — сначала; путешествия, переезд и стройка — позже.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (s.firstTopics.isNotEmpty() || s.laterTopics.isNotEmpty()) {
            TextButton(onClick = { update { it.copy(firstTopics = emptySet(), laterTopics = emptySet()) } }) { Text("Сбросить: все темы по порядку") }
        }
        topics.forEach { t ->
            val mode = Topics.mode(s, t.topic)
            Panel {
                Text(t.topic, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOf(t.level, "начато ${t.started} из ${t.total}").filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (t.started == t.total) Success else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TopicMode.entries.forEach { m ->
                        FilterChip(selected = mode == m, onClick = { update { Topics.withMode(it, t.topic, m) } }, label = { Text(m.label) })
                    }
                }
            }
        }
    }
}
