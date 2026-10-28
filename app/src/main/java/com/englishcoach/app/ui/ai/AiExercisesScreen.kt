package com.englishcoach.app.ui.ai

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.englishcoach.app.data.ai.AiException
import com.englishcoach.app.data.practice.Exercise
import com.englishcoach.app.data.practice.PracticeKind
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.practice.ExerciseView
import com.englishcoach.app.ui.practice.PracticeFrame
import com.englishcoach.app.ui.practice.ResultBlock
import kotlinx.coroutines.launch

private const val WEAK_WORDS = 8

/** Упражнения, которые ИИ составляет из слов, которые вы чаще всего забываете. */
@Composable
fun AiExercisesScreen(onClose: () -> Unit) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var exercises by remember { mutableStateOf<List<Exercise>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var step by remember { mutableIntStateOf(0) }
    var correct by remember { mutableIntStateOf(0) }
    var startedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(attempt) {
        exercises = null
        error = null
        val words = c.practice.weakWords(WEAK_WORDS)
        if (words.isEmpty()) {
            error = "Сначала поучите слова в карточках — упражнения строятся по словам, которые даются труднее."
            return@LaunchedEffect
        }
        try {
            exercises = c.tutor.exercises(words).ifEmpty { null }
            if (exercises == null) error = "ИИ не прислал упражнений. Попробуйте ещё раз."
        } catch (e: AiException) {
            error = e.message
        } catch (e: Exception) {
            error = "Не удалось разобрать ответ. Попробуйте ещё раз."
        }
        step = 0
        correct = 0
        startedAt = System.currentTimeMillis()
    }

    val list = exercises
    PracticeFrame(progress = list?.let { step.toFloat() / it.size }, onClose = onClose) {
        when {
            error != null -> {
                Text(error.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { attempt++ }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Попробовать снова") }
            }
            list == null -> {
                Text("Составляю упражнения по вашим трудным словам…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                CircularProgressIndicator()
            }
            step < list.size -> ExerciseView(list[step]) { ok ->
                if (ok) correct++
                step++
                if (step == list.size) {
                    val score = correct
                    scope.launch { c.practice.log(PracticeKind.AI_EXERCISES, "weak-words", score, list.size, System.currentTimeMillis() - startedAt) }
                }
            }
            // «Ещё раз» — новый набор упражнений
            else -> ResultBlock(correct, list.size, onRetry = { attempt++ }, onClose = onClose)
        }
    }
}
