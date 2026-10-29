package com.englishcoach.app.ui.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
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
import com.englishcoach.app.data.practice.PracticeKind
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.components.LazyScreenColumn
import com.englishcoach.app.ui.theme.Success

/** Урок/текст считается пройденным при результате от 70%. */
private const val PASS_PERCENT = 70

@Composable
fun PracticeScreen(
    onGrammar: (String) -> Unit,
    onReading: (String) -> Unit,
    onListening: () -> Unit,
    onSpeaking: () -> Unit,
    onDialog: (String) -> Unit,
    onAiChat: () -> Unit,
    onTextCheck: () -> Unit,
    onAiExercises: () -> Unit,
) {
    val c = LocalAppContainer.current
    val hasKey by c.ai.hasKey.collectAsStateWithLifecycle()
    // Начальное значение — уже загруженное: при возврате из урока список сразу на месте и прокрутка не сбрасывается
    val lessons by produceState(c.practice.cachedGrammar()) { value = c.practice.grammarLessons() }
    val texts by produceState(c.practice.cachedTexts()) { value = c.practice.readingTexts() }
    val dialogs by produceState(c.practice.cachedDialogs()) { value = c.practice.dialogs() }
    val dialogBest by remember { c.practice.bestScores(PracticeKind.DIALOG) }.collectAsStateWithLifecycle(emptyList())
    val dMap = dialogBest.associate { it.itemId to it.best }
    val grammarBest by remember { c.practice.bestScores(PracticeKind.GRAMMAR) }.collectAsStateWithLifecycle(emptyList())
    val readingBest by remember { c.practice.bestScores(PracticeKind.READING) }.collectAsStateWithLifecycle(emptyList())
    val gMap = grammarBest.associate { it.itemId to it.best }
    val rMap = readingBest.associate { it.itemId to it.best }

    LazyScreenColumn(title = "Практика", subtitle = "Уровни A1–B2 · всё работает без интернета") {
        item { Section("Грамматика") }
        items(lessons, key = { it.id }) { l ->
            ItemRow(l.title, "${l.level} · " + (gMap[l.id]?.let { "лучший результат $it%" } ?: "${l.exercises.size} упражнений"), gMap[l.id]) { onGrammar(l.id) }
        }
        item { Section("Чтение") }
        items(texts, key = { it.id }) { t ->
            ItemRow(t.title, "${t.level} · ${t.topic}" + (rMap[t.id]?.let { " · лучший результат $it%" } ?: ""), rMap[t.id]) { onReading(t.id) }
        }
        item { Group { Section("Аудирование"); ItemRow("Послушай и запиши", "8 фраз из примеров к вашим словам", null, onListening) } }
        item { Group { Section("Речь"); ItemRow("Произношение", "Повторите 6 фраз вслух — приложение проверит", null, onSpeaking) } }
        items(dialogs, key = { it.id }) { d ->
            ItemRow("Диалог: ${d.title}", "${d.level} · ${d.topic}" + (dMap[d.id]?.let { " · лучший результат $it%" } ?: ""), dMap[d.id]) { onDialog(d.id) }
        }
        item { Section("С ИИ (нужен интернет)") }
        if (hasKey) {
            item { ItemRow("Собеседник", "Разговор на ваши темы, разбор ошибок, новые слова", null, onAiChat) }
            item { ItemRow("Проверка текста", "Напишите пару предложений — исправлю и объясню", null, onTextCheck) }
            item { ItemRow("Упражнения по трудным словам", "Составлю задания из слов, которые чаще забываются", null, onAiExercises) }
        } else {
            item {
                Panel {
                    Text("Добавьте ключ API в «Настройках», чтобы открыть собеседника, проверку текста и упражнения от ИИ.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Несколько строк в одном элементе ленивого списка — с тем же отступом, что между элементами. */
@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
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
