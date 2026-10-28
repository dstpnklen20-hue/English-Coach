package com.englishcoach.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.englishcoach.app.data.db.WordEntity
import com.englishcoach.app.data.practice.Answers
import com.englishcoach.app.data.practice.PracticeKind
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.theme.Success
import kotlinx.coroutines.launch

private const val PHRASES = 6
/** Фраза засчитана, если распознано не меньше 80% слов. */
private const val PASS_RATIO = 0.8

/** Произношение: послушать фразу → сказать её → увидеть, какие слова распознаны. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeakingScreen(onClose: () -> Unit) {
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
        val phrase = list[idx]
        val sentence = phrase.example.orEmpty()
        var heard by remember(idx) { mutableStateOf("") }
        var best by remember(idx) { mutableStateOf<Pair<String, Double>?>(null) } // лучшая попытка и её доля
        LaunchedEffect(idx) { c.tts.speak(sentence) }

        Text("Фраза ${idx + 1} из $n · послушайте и повторите", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sentence, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    phrase.exampleRu?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                IconButton(onClick = { c.tts.speak(sentence, slow = true) }) {
                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Послушать медленно", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        MicButton(onPartial = { heard = it }) { variants ->
            // Берём вариант распознавания, ближе всего к эталону; сохраняем лучшую из попыток
            val top = variants.maxBy { Answers.matchRatio(it, sentence) }
            heard = top
            val ratio = Answers.matchRatio(top, sentence)
            if (ratio >= (best?.second ?: -1.0)) best = top to ratio
        }
        if (heard.isNotEmpty()) {
            Text("Распознано: «$heard»", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        best?.let { (said, ratio) ->
            Panel {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Answers.wordDiff(said, sentence).forEach { (word, hit) ->
                        Text(
                            word,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hit) Success else MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(
                    if (ratio >= PASS_RATIO) "Отлично, вас поняли! ${(ratio * 100).toInt()}%"
                    else "Понятно ${(ratio * 100).toInt()}%. Красные слова — послушайте и попробуйте ещё раз.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ratio >= PASS_RATIO) Success else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val passed = (best?.second ?: 0.0) >= PASS_RATIO
        val next = {
            if (passed) correct++
            idx++
            if (idx == n) {
                val score = correct
                scope.launch { c.practice.log(PracticeKind.SPEAKING, "phrases", score, n, System.currentTimeMillis() - startedAt) }
            }
        }
        if (best != null) {
            Button(onClick = next, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Дальше") }
        } else {
            OutlinedButton(onClick = next, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Пропустить") }
        }
    }
}
