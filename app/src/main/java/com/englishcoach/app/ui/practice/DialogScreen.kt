package com.englishcoach.app.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.englishcoach.app.data.practice.Answers
import com.englishcoach.app.data.practice.Dialog
import com.englishcoach.app.data.practice.DialogLine
import com.englishcoach.app.data.practice.PracticeKind
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.Panel
import kotlinx.coroutines.delay

/** Голосовой ответ засчитывается как выбор варианта, если совпало не меньше 60% его слов. */
private const val VOICE_MATCH = 0.6

/**
 * Сценарный диалог без ИИ: собеседник говорит (озвучка + перевод), вы отвечаете —
 * кнопкой или голосом. Неверный вариант подсвечивается, можно попробовать снова.
 */
@Composable
fun DialogScreen(dialogId: String, onClose: () -> Unit) {
    val c = LocalAppContainer.current
    val dialog by produceState<Dialog?>(null, dialogId) { value = c.practice.dialogs().firstOrNull { it.id == dialogId } }
    val d = dialog ?: return
    val lines = d.lines
    val myTurns = lines.count { it.who == "me" }
    var pos by rememberSaveable { mutableIntStateOf(0) } // сколько реплик уже показано
    var mistakes by rememberSaveable { mutableIntStateOf(0) }
    var startedAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var attempt by rememberSaveable { mutableIntStateOf(0) } // для «Ещё раз»
    val finished = pos >= lines.size
    val listState = rememberLazyListState()

    // Реплики собеседника идут сами: озвучили → пауза по длине фразы → следующая
    LaunchedEffect(pos, attempt) {
        val line = lines.getOrNull(pos) ?: return@LaunchedEffect
        if (line.who == "them") {
            c.tts.speak(line.en)
            delay(900L + line.en.split(" ").size * 300L)
            pos++
        }
    }
    LaunchedEffect(pos) { if (pos > 0) listState.animateScrollToItem(pos - 1) }
    LaunchedEffect(finished, attempt) {
        if (finished) {
            c.practice.log(PracticeKind.DIALOG, d.id, (myTurns - mistakes).coerceAtLeast(0), myTurns, System.currentTimeMillis() - startedAt)
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Закрыть") }
            Column {
                Text(d.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(d.intro, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(lines.take(pos)) { _, line -> Bubble(line, onSpeak = { c.tts.speak(it) }) }
        }
        val line = lines.getOrNull(pos)
        when {
            finished -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (mistakes == 0) "Диалог пройден без ошибок!" else "Диалог пройден. Ошибок: $mistakes",
                    style = MaterialTheme.typography.titleMedium,
                )
                ResultButtons(onRetry = {
                    pos = 0
                    mistakes = 0
                    startedAt = System.currentTimeMillis()
                    attempt++
                }, onClose = onClose)
            }
            line?.who == "me" -> MyTurn(line, pos) { ok ->
                if (ok) {
                    c.tts.speak(line.options.first())
                    pos++
                } else {
                    mistakes++
                }
            }
        }
    }
}

@Composable
private fun Bubble(line: DialogLine, onSpeak: (String) -> Unit) {
    val mine = line.who == "me"
    val text = if (mine) line.options.first() else line.en
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.widthIn(max = 320.dp)
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    MaterialTheme.shapes.medium,
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                if (!mine) {
                    IconButton(onClick = { onSpeak(text) }) {
                        Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Повторить", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(line.ru, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Ваш ход: подсказка по-русски, варианты в случайном порядке, можно ответить голосом. */
@Composable
private fun MyTurn(line: DialogLine, pos: Int, onAnswer: (Boolean) -> Unit) {
    val options = remember(pos) { line.options.shuffled() }
    var wrong by remember(pos) { mutableStateOf(setOf<String>()) }
    var voiceNote by remember(pos) { mutableStateOf<String?>(null) }
    val choose = { option: String ->
        val ok = option == line.options.first()
        if (!ok) wrong = wrong + option
        onAnswer(ok)
    }
    Panel {
        Text("Ваш ответ: ${line.hint}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        options.forEach { option ->
            val isWrong = option in wrong
            OutlinedButton(
                onClick = { if (!isWrong) choose(option) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = if (isWrong) {
                    ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
            ) { Text(option) }
        }
        MicButton { variants ->
            // Ищем вариант ответа, на который сказанное похоже больше всего
            val (said, option, ratio) = variants.flatMap { v -> options.map { o -> Triple(v, o, Answers.matchRatio(v, o)) } }
                .maxBy { it.third }
            if (ratio >= VOICE_MATCH) {
                voiceNote = null
                choose(option)
            } else {
                voiceNote = "Распознано «$said» — не похоже ни на один вариант. Попробуйте ещё раз."
            }
        }
        voiceNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ResultButtons(onRetry: () -> Unit, onClose: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Ещё раз") }
        Button(onClick = onClose, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Готово") }
    }
}
