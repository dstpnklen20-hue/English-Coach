package com.englishcoach.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.englishcoach.app.data.db.WordEntity
import com.englishcoach.app.data.practice.Answers
import com.englishcoach.app.data.practice.PracticeKind
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.theme.Success
import kotlinx.coroutines.launch

private const val PHRASES = 8

/** Диктант: фраза звучит → пользователь записывает → подсветка угаданных слов. Фразы — примеры к словам курса. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListeningScreen(onClose: () -> Unit) {
    val c = LocalAppContainer.current
    val items by produceState<List<WordEntity>?>(null) { value = c.practice.listeningSentences(PHRASES) }
    val list = items ?: return
    var idx by rememberSaveable { mutableIntStateOf(0) }
    var correct by rememberSaveable { mutableIntStateOf(0) }
    val startedAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()
    val n = list.size

    PracticeFrame(progress = idx.toFloat() / n.coerceAtLeast(1), onClose = onClose) {
        if (idx >= n) {
            ResultBlock(correct, n, onRetry = { idx = 0; correct = 0 }, onClose = onClose)
            return@PracticeFrame
        }
        val sentence = list[idx].example.orEmpty()
        var input by remember(idx) { mutableStateOf("") }
        var diff by remember(idx) { mutableStateOf<List<Pair<String, Boolean>>?>(null) }
        LaunchedEffect(idx) { c.tts.speak(sentence) }
        val check = { if (diff == null && input.isNotBlank()) diff = Answers.wordDiff(input, sentence) }

        Text("Фраза ${idx + 1} из $n · послушайте и запишите", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { c.tts.speak(sentence) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, null)
                Text("  Ещё раз")
            }
            OutlinedButton(onClick = { c.tts.speak(sentence, slow = true) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Icon(Icons.Outlined.SlowMotionVideo, null)
                Text("  Медленно")
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { if (diff == null) input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Что вы услышали?") },
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { check() }),
            shape = MaterialTheme.shapes.medium,
        )
        val d = diff
        if (d == null) {
            Button(onClick = check, enabled = input.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Проверить") }
        } else {
            val ok = d.all { it.second }
            Panel {
                // Зелёные — услышаны верно, красные — пропущены или с ошибкой
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.forEach { (word, hit) ->
                        Text(
                            word,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hit) Success else MaterialTheme.colorScheme.error,
                        )
                    }
                }
                list[idx].exampleRu?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(
                if (ok) "Всё верно!" else "Почти. Послушайте ещё раз и сравните.",
                style = MaterialTheme.typography.titleMedium,
                color = if (ok) Success else MaterialTheme.colorScheme.error,
            )
            Button(onClick = {
                if (ok) correct++
                idx++
                if (idx == n) {
                    val score = correct
                    scope.launch { c.practice.log(PracticeKind.LISTENING, "dictation", score, n, System.currentTimeMillis() - startedAt) }
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Дальше") }
        }
    }
}
