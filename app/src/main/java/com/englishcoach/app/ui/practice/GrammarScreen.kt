package com.englishcoach.app.ui.practice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.englishcoach.app.data.practice.GrammarLesson
import com.englishcoach.app.data.practice.PracticeKind
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.Panel
import kotlinx.coroutines.launch

/** Урок грамматики: короткое объяснение с примерами → упражнения → итог. */
@Composable
fun GrammarScreen(lessonId: String, onClose: () -> Unit) {
    val c = LocalAppContainer.current
    val lesson by produceState<GrammarLesson?>(null, lessonId) {
        value = c.practice.grammarLessons().firstOrNull { it.id == lessonId }
    }
    val l = lesson ?: return
    val n = l.exercises.size
    var step by rememberSaveable { mutableIntStateOf(-1) } // -1 — теория, 0..n-1 — упражнения, n — итог
    var correct by rememberSaveable { mutableIntStateOf(0) }
    var startedAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    PracticeFrame(progress = if (step < 0) null else step.toFloat() / n, onClose = onClose) {
        when {
            step < 0 -> Theory(l, onSpeak = { c.tts.speak(it) }) {
                step = 0
                startedAt = System.currentTimeMillis()
            }
            step < n -> ExerciseView(l.exercises[step]) { ok ->
                if (ok) correct++
                step++
                if (step == n) {
                    val score = correct
                    scope.launch { c.practice.log(PracticeKind.GRAMMAR, l.id, score, n, System.currentTimeMillis() - startedAt) }
                }
            }
            else -> ResultBlock(correct, n, onRetry = {
                step = 0
                correct = 0
                startedAt = System.currentTimeMillis()
            }, onClose = onClose)
        }
    }
}

@Composable
private fun Theory(l: GrammarLesson, onSpeak: (String) -> Unit, onStart: () -> Unit) {
    Text(l.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Panel {
        l.theory.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
    }
    Text("Примеры", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Panel {
        l.examples.forEach { (en, ru) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(en, style = MaterialTheme.typography.titleMedium)
                    Text(ru, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onSpeak(en) }) {
                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Озвучить", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("К упражнениям") }
}
