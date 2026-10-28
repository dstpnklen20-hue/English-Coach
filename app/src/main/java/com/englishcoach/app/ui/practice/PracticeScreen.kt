package com.englishcoach.app.ui.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishcoach.app.data.practice.GrammarLesson
import com.englishcoach.app.data.practice.PracticeKind
import com.englishcoach.app.data.practice.ReadingText
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.ComingSoon
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.components.ScreenColumn
import com.englishcoach.app.ui.theme.Success

/** Урок/текст считается пройденным при результате от 70%. */
private const val PASS_PERCENT = 70

@Composable
fun PracticeScreen(onGrammar: (String) -> Unit, onReading: (String) -> Unit, onListening: () -> Unit) {
    val c = LocalAppContainer.current
    val lessons by produceState(emptyList<GrammarLesson>()) { value = c.practice.grammarLessons() }
    val texts by produceState(emptyList<ReadingText>()) { value = c.practice.readingTexts() }
    val grammarBest by remember { c.practice.bestScores(PracticeKind.GRAMMAR) }.collectAsStateWithLifecycle(emptyList())
    val readingBest by remember { c.practice.bestScores(PracticeKind.READING) }.collectAsStateWithLifecycle(emptyList())
    val gMap = grammarBest.associate { it.itemId to it.best }
    val rMap = readingBest.associate { it.itemId to it.best }

    ScreenColumn(title = "Практика", subtitle = "Уровень A1 · всё работает без интернета") {
        Section("Грамматика")
        lessons.forEach { l ->
            ItemRow(l.title, gMap[l.id]?.let { "лучший результат $it%" } ?: "${l.exercises.size} упражнений", gMap[l.id]) { onGrammar(l.id) }
        }
        Section("Чтение")
        texts.forEach { t ->
            ItemRow(t.title, rMap[t.id]?.let { "${t.topic} · лучший результат $it%" } ?: t.topic, rMap[t.id]) { onReading(t.id) }
        }
        Section("Аудирование")
        ItemRow("Послушай и запиши", "8 фраз из примеров к вашим словам", null, onListening)
        ComingSoon(4, "Произношение: повторить вслух, приложение проверит.")
        ComingSoon(4, "Диалоги: сценарии (аэропорт, магазин, стройка, игровой чат).")
        ComingSoon(5, "Собеседник ИИ: свободный разговор и разбор ошибок.")
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ItemRow(title: String, subtitle: String, best: Int?, onClick: () -> Unit) {
    val passed = best != null && best >= PASS_PERCENT
    Panel(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (passed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                null,
                tint = if (passed) Success else MaterialTheme.colorScheme.outline,
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
