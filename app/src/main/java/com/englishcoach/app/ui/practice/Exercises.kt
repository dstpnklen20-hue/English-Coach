package com.englishcoach.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.englishcoach.app.data.practice.Answers
import com.englishcoach.app.data.practice.Exercise
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.theme.Success

/** Общий каркас полноэкранной практики: крестик, полоса прогресса, прокручиваемое содержимое. */
@Composable
fun PracticeFrame(progress: Float?, onClose: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Закрыть") }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f).padding(start = 8.dp, top = 22.dp),
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

/**
 * Одно упражнение с проверкой. Пользователь отвечает → видит, верно ли, и правильный ответ →
 * «Дальше» вызывает onDone(верно ли).
 */
@Composable
fun ExerciseView(ex: Exercise, onDone: (Boolean) -> Unit) {
    var result by remember(ex) { mutableStateOf<Boolean?>(null) }
    when (ex.type) {
        "choice" -> ChoiceExercise(ex, result) { result = it }
        "input" -> InputExercise(ex, result) { result = it }
        else -> OrderExercise(ex, result) { result = it }
    }
    result?.let { ok ->
        Feedback(ok, if (ok) null else correctAnswer(ex))
        Button(onClick = { onDone(ok) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Дальше") }
    }
}

private fun correctAnswer(ex: Exercise) = when (ex.type) {
    "choice" -> ex.options.getOrNull(ex.answer).orEmpty()
    else -> ex.accept.firstOrNull().orEmpty()
}

@Composable
private fun Question(ex: Exercise) {
    if (ex.q.isNotEmpty()) Panel { Text(ex.q, style = MaterialTheme.typography.headlineSmall) }
    ex.ru?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun ChoiceExercise(ex: Exercise, result: Boolean?, onResult: (Boolean) -> Unit) {
    var chosen by remember(ex) { mutableStateOf<Int?>(null) }
    Question(ex)
    ex.options.forEachIndexed { i, option ->
        val color = when {
            result == null -> null
            i == ex.answer -> Success
            i == chosen -> MaterialTheme.colorScheme.error
            else -> null
        }
        OutlinedButton(
            onClick = { if (result == null) { chosen = i; onResult(i == ex.answer) } },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = if (color != null) {
                ButtonDefaults.outlinedButtonColors(containerColor = color.copy(alpha = 0.16f), contentColor = color)
            } else {
                ButtonDefaults.outlinedButtonColors()
            },
        ) { Text(option, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun InputExercise(ex: Exercise, result: Boolean?, onResult: (Boolean) -> Unit) {
    var text by remember(ex) { mutableStateOf("") }
    val check = { if (result == null && text.isNotBlank()) onResult(Answers.matches(text, ex.accept)) }
    Question(ex)
    OutlinedTextField(
        value = text,
        onValueChange = { if (result == null) text = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Впишите пропущенное слово") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { check() }),
        shape = MaterialTheme.shapes.medium,
    )
    if (result == null) {
        Button(onClick = check, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Проверить") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderExercise(ex: Exercise, result: Boolean?, onResult: (Boolean) -> Unit) {
    val target = ex.accept.firstOrNull().orEmpty()
    // Индексы слов, чтобы одинаковые слова («the», «the») не путались
    val words = remember(ex) { target.split(" ") }
    val pool = remember(ex) { mutableStateListOf<Int>().apply { addAll(words.indices.shuffled()) } }
    val picked = remember(ex) { mutableStateListOf<Int>() }

    Text("Соберите фразу", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Question(ex)
    Panel {
        FlowRow(Modifier.heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            picked.toList().forEach { i ->
                AssistChip(onClick = { if (result == null) { picked.remove(i); pool.add(i) } }, label = { Text(words[i], fontWeight = FontWeight.SemiBold) })
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        pool.toList().forEach { i ->
            AssistChip(onClick = { if (result == null) { pool.remove(i); picked.add(i) } }, label = { Text(words[i]) })
        }
    }
    if (result == null) {
        Button(
            onClick = { onResult(Answers.matches(picked.joinToString(" ") { words[it] }, ex.accept)) },
            enabled = pool.isEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Проверить") }
    }
}

@Composable
private fun Feedback(ok: Boolean, correct: String?) {
    Text(
        if (ok) "Верно!" else "Не совсем. Правильно: $correct",
        style = MaterialTheme.typography.titleMedium,
        color = if (ok) Success else MaterialTheme.colorScheme.error,
    )
}

/** Итог упражнения: счёт и кнопки. */
@Composable
fun ResultBlock(correct: Int, total: Int, onRetry: () -> Unit, onClose: () -> Unit) {
    Text("$correct из $total", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
    Text(
        when {
            correct == total -> "Отлично, без ошибок!"
            correct * 10 >= total * 7 -> "Хороший результат. Ошибки стоит повторить."
            else -> "Попробуйте ещё раз — со второго раза запоминается лучше."
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Готово") }
    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Ещё раз") }
}
