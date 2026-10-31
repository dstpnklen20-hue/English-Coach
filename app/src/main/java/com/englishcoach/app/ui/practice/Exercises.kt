package com.englishcoach.app.ui.practice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import com.englishcoach.app.ui.theme.Motion
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
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.TextButton
import com.englishcoach.app.ui.theme.Warm
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
import com.englishcoach.app.data.practice.Phrase
import com.englishcoach.app.data.practice.Tiles
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.theme.Success

/**
 * Общий каркас полноэкранной практики: крестик, полоса прогресса, прокручиваемое содержимое.
 * [bottom] — закреплённая под ним часть (банк слов), чтобы на телефоне не листать туда-обратно.
 */
@Composable
fun PracticeFrame(progress: Float?, onClose: () -> Unit, bottom: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Закрыть") }
            if (progress != null) {
                val smooth by animateFloatAsState(progress, Motion.soft(450), label = "progress")
                LinearProgressIndicator(
                    progress = { smooth },
                    modifier = Modifier.weight(1f).padding(start = 8.dp, top = 22.dp),
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
        if (bottom != null) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { bottom() }
        }
    }
}

/**
 * Одно упражнение с проверкой. Пользователь отвечает → видит, верно ли, и правильный ответ →
 * «Дальше» вызывает onDone(верно ли).
 */
@Composable
fun ExerciseView(ex: Exercise, onDone: (Boolean) -> Unit) {
    val st = remember(ex) { ExerciseState() }
    // Каждое новое упражнение мягко проявляется с небольшим сдвигом
    val shown = remember(ex) { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(shown, enter = Motion.exerciseEnter) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (ex.type) {
                "choice" -> ChoiceExercise(ex, st)
                "input" -> InputExercise(ex, st)
                else -> OrderExercise(ex, st)
            }
            val feedback = when {
                st.solved -> Feedback.Solved(firstTry = !st.failedOnce)
                st.revealed -> Feedback.Revealed
                st.hint != null -> Feedback.Hint(st.hint.orEmpty())
                else -> Feedback.None
            }
            // Отзыв на ответ сменяется плавно; содержимое берётся из состояния-ключа, чтобы уходящее не мигало новым текстом
            AnimatedContent(
                feedback,
                transitionSpec = { Motion.appear togetherWith Motion.disappear using SizeTransform(clip = false) },
                label = "feedback",
            ) { f ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (f) {
                        is Feedback.Solved -> {
                            Text(if (f.firstTry) "Верно!" else "Теперь верно!", style = MaterialTheme.typography.titleMedium, color = Success)
                            // В счёт идёт только ответ с первой попытки — так результат остаётся честным
                            Button(onClick = { onDone(f.firstTry) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Дальше") }
                        }
                        Feedback.Revealed -> {
                            Text("Правильно: ${correctAnswer(ex)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Button(onClick = { onDone(false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Дальше") }
                        }
                        is Feedback.Hint -> {
                            HintCard(f.text)
                            TextButton(onClick = { st.revealed = true }) { Text("Показать ответ") }
                        }
                        Feedback.None -> Unit
                    }
                }
            }
        }
    }
}

private sealed interface Feedback {
    data object None : Feedback
    data class Hint(val text: String) : Feedback
    data class Solved(val firstTry: Boolean) : Feedback
    data object Revealed : Feedback
}

/**
 * Попытки по одному упражнению. После ошибки показывается подсказка именно к ней,
 * и можно исправить ответ; «Показать ответ» — если не получается.
 */
private class ExerciseState {
    var failedOnce by mutableStateOf(false)
    var solved by mutableStateOf(false)
    var revealed by mutableStateOf(false)
    var hint by mutableStateOf<String?>(null)
    val finished get() = solved || revealed

    fun check(ok: Boolean, hintText: String?) {
        if (ok) {
            solved = true
            hint = null
        } else {
            failedOnce = true
            hint = hintText ?: "Пока неверно — посмотрите внимательнее и попробуйте ещё раз."
        }
    }
}

/** Небольшая подсказка к ошибке. */
@Composable
fun HintCard(text: String) {
    Panel {
        Row {
            Icon(Icons.Outlined.Lightbulb, null, tint = Warm, modifier = Modifier.padding(end = 8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
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
private fun ChoiceExercise(ex: Exercise, st: ExerciseState) {
    val wrong = remember(ex) { mutableStateListOf<Int>() }
    Question(ex)
    ex.options.forEachIndexed { i, option ->
        val color = when {
            i == ex.answer && st.finished -> Success
            i in wrong -> MaterialTheme.colorScheme.error
            else -> null
        }
        OutlinedButton(
            onClick = {
                if (!st.finished && i !in wrong) {
                    val ok = i == ex.answer
                    if (!ok) wrong.add(i)
                    st.check(ok, ex.hintFor(i))
                }
            },
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
private fun InputExercise(ex: Exercise, st: ExerciseState) {
    var text by remember(ex) { mutableStateOf("") }
    val check = { if (!st.finished && text.isNotBlank()) st.check(Answers.matches(text, ex.accept), ex.hint) }
    Question(ex)
    OutlinedTextField(
        value = text,
        onValueChange = { if (!st.finished) text = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(ex.placeholder ?: "Впишите пропущенное слово") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { check() }),
        shape = MaterialTheme.shapes.medium,
    )
    if (!st.finished) {
        Button(onClick = check, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Проверить") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderExercise(ex: Exercise, st: ExerciseState) {
    val target = ex.accept.firstOrNull().orEmpty()
    // Индексы слов, чтобы одинаковые слова («the», «the») не путались; без точки и заглавной — они выдают начало и конец
    val words = remember(ex) { Tiles.of(Phrase(ru = "", en = target)) }
    val pool = remember(ex) { mutableStateListOf<Int>().apply { addAll(words.indices.shuffled()) } }
    val picked = remember(ex) { mutableStateListOf<Int>() }

    Text("Соберите фразу", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Question(ex)
    Panel {
        FlowRow(Modifier.heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            picked.toList().forEach { i ->
                AssistChip(onClick = { if (!st.finished) { picked.remove(i); pool.add(i) } }, label = { Text(words[i], fontWeight = FontWeight.SemiBold) })
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        pool.toList().forEach { i ->
            AssistChip(onClick = { if (!st.finished) { pool.remove(i); picked.add(i) } }, label = { Text(words[i]) })
        }
    }
    if (!st.finished) {
        Button(
            onClick = { st.check(Answers.matches(picked.joinToString(" ") { words[it] }, ex.accept), ex.hint) },
            enabled = pool.isEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Проверить") }
    }
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
