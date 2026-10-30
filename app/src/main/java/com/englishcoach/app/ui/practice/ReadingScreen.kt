package com.englishcoach.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.data.practice.PracticeKind
import com.englishcoach.app.data.practice.ReadingText
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.Panel
import kotlinx.coroutines.launch

private val WORD = Regex("[A-Za-z']+")

/** Чтение: текст, где любое слово нажимается для перевода → вопросы на понимание → итог. */
@Composable
fun ReadingScreen(textId: String, onClose: () -> Unit) {
    val c = LocalAppContainer.current
    val text by produceState<ReadingText?>(null, textId) {
        value = c.practice.readingTexts().firstOrNull { it.id == textId }
    }
    val t = text ?: return
    val n = t.questions.size
    var step by rememberSaveable { mutableIntStateOf(-1) } // -1 — чтение, 0..n-1 — вопросы, n — итог
    var correct by rememberSaveable { mutableIntStateOf(0) }
    val startedAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    PracticeFrame(progress = if (step < 0) null else step.toFloat() / n, onClose = onClose) {
        when {
            step < 0 -> ReadText(t, onQuestions = { step = 0 })
            step < n -> ExerciseView(t.questions[step]) { ok ->
                if (ok) correct++
                step++
                if (step == n) {
                    val score = correct
                    scope.launch { c.practice.log(PracticeKind.READING, t.id, score, n, System.currentTimeMillis() - startedAt) }
                }
            }
            else -> ResultBlock(correct, n, onRetry = {
                step = -1
                correct = 0
            }, onClose = onClose)
        }
    }
}

@Composable
private fun ReadText(t: ReadingText, onQuestions: () -> Unit) {
    val c = LocalAppContainer.current
    var selected by remember { mutableStateOf<String?>(null) }

    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val annotated = remember(t.text, selected) {
        buildAnnotatedString {
            var last = 0
            WORD.findAll(t.text).forEach { m ->
                append(t.text.substring(last, m.range.first))
                val word = m.value
                val style = SpanStyle(
                    textDecoration = TextDecoration.None,
                    background = if (word.equals(selected, ignoreCase = true)) highlight else Color.Unspecified,
                )
                withLink(LinkAnnotation.Clickable(word, TextLinkStyles(style)) { selected = word }) { append(word) }
                last = m.range.last + 1
            }
            append(t.text.substring(last))
        }
    }

    Text("${t.topic} · ${t.level}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Text(t.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text("Нажмите на любое слово — появится перевод.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Panel {
        Text(annotated, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 30.sp))
    }
    selected?.let { WordPanel(it, t.glossary) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { c.tts.speak(t.text, slow = true) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Слушать") }
        Button(onClick = onQuestions, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("К вопросам") }
    }
}

/** Перевод нажатого слова: словарь текста → общий → слова курса; озвучка и добавление в «Мои слова». */
@Composable
fun WordPanel(w: String, glossary: Map<String, String>) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var translation by remember(w) { mutableStateOf<String?>(null) }
    var inDictionary by remember(w) { mutableStateOf(true) }
    var added by remember(w) { mutableStateOf(false) }
    var loaded by remember(w) { mutableStateOf(false) }
    LaunchedEffect(w) {
        translation = c.practice.translate(w, glossary)
        loaded = true
        inDictionary = c.practice.inDictionary(w)
        c.tts.speak(w)
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(w, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(translation ?: if (loaded) "Нет в словаре" else "…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { c.tts.speak(w) }) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Озвучить", tint = MaterialTheme.colorScheme.primary)
            }
        }
        val tr = translation
        if (tr != null && !inDictionary) {
            TextButton(onClick = {
                added = true
                scope.launch { c.study.addUserWord(w.lowercase(), tr) }
            }, enabled = !added) { Text(if (added) "Добавлено в «Мои слова»" else "+ В мои слова для карточек") }
        }
    }
}
