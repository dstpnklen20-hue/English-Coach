package com.englishcoach.app.ui.placement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.theme.Success

@Composable
fun PlacementScreen(onClose: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: PlacementViewModel = viewModel(factory = viewModelFactory { initializer { PlacementViewModel(container) } })

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Закрыть") }
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            when (val p = vm.phase) {
                Phase.Intro -> Intro(vm::start)
                is Phase.Question -> Question(p, vm::answer)
                is Phase.WordCheck -> WordCheck(p, vm::knowWord, vm::speak, vm::finishWords)
                is Phase.Result -> Result(p, onClose)
            }
        }
    }
}

@Composable
private fun Title(text: String) =
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Intro(onStart: () -> Unit) {
    Title("Стартовый тест")
    Hint("Часть 1 — до 16 коротких вопросов по грамматике, от простого к сложному. Тест остановится сам, когда станет трудно.")
    Hint("Часть 2 — быстро отметьте слова, которые уже знаете. Их приложение не будет учить заново, а только иногда проверит.")
    Hint("Займёт около 5 минут. Не угадывайте — честный результат полезнее.")
    Spacer(Modifier.height(8.dp))
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Начать") }
}

@Composable
private fun Question(p: Phase.Question, onAnswer: (Int) -> Unit) {
    val total = 4 * PlacementViewModel.QUESTIONS_PER_LEVEL
    LinearProgressIndicator(
        progress = { p.number.toFloat() / total },
        modifier = Modifier.fillMaxWidth(),
        trackColor = MaterialTheme.colorScheme.outlineVariant,
    )
    Text("Уровень ${p.level} · вопрос ${p.number}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Panel { Text(p.q.q, style = MaterialTheme.typography.headlineSmall) }
    p.q.options.forEachIndexed { i, option ->
        // После ответа: верный вариант — зелёный, ошибочно выбранный — красный
        val color = when {
            p.chosen == null -> null
            i == p.q.answer -> Success
            i == p.chosen -> MaterialTheme.colorScheme.error
            else -> null
        }
        OutlinedButton(
            onClick = { onAnswer(i) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = if (color != null) {
                ButtonDefaults.outlinedButtonColors(containerColor = color.copy(alpha = 0.16f), contentColor = color)
            } else {
                ButtonDefaults.outlinedButtonColors()
            },
        ) { Text(option, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun WordCheck(p: Phase.WordCheck, onKnow: (Boolean) -> Unit, onSpeak: (String) -> Unit, onFinish: () -> Unit) {
    LinearProgressIndicator(
        progress = { p.index.toFloat() / p.total },
        modifier = Modifier.fillMaxWidth(),
        trackColor = MaterialTheme.colorScheme.outlineVariant,
    )
    Text("Знаете это слово? · ${p.index} из ${p.total}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.word.en, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(p.word.ru, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onSpeak(p.word.en) }) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Озвучить", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { onKnow(false) }, modifier = Modifier.weight(1f).height(56.dp)) { Text("Не знаю") }
        Button(
            onClick = { onKnow(true) },
            modifier = Modifier.weight(1f).height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Success, contentColor = Color(0xFF0B1A12)),
        ) { Text("Знаю") }
    }
    TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Хватит, остальное выучу") }
}

@Composable
private fun Result(p: Phase.Result, onClose: () -> Unit) {
    Title(if (p.level == null) "Начинаем с самого начала" else "Ваш уровень — ${p.level}")
    Hint(
        if (p.level == null) "Курс начнётся с основ A1 — это нормально, база важнее всего."
        else "Грамматику уровня ${p.level} вы знаете уверенно. Курс будет опираться на это.",
    )
    if (p.known > 0) Hint("Отмечено знакомых слов: ${p.known}. Они появятся на проверку примерно через 2 недели.")
    Hint("Тест можно пройти заново в настройках.")
    Spacer(Modifier.height(8.dp))
    Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Готово") }
}
