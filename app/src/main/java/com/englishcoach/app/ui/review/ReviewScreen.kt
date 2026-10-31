package com.englishcoach.app.ui.review

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import com.englishcoach.app.ui.theme.Motion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.englishcoach.app.data.db.CardWithWord
import com.englishcoach.app.data.fsrs.Rating
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.theme.Success
import com.englishcoach.app.ui.theme.Warm

@Composable
fun ReviewScreen(onClose: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: ReviewViewModel = viewModel(factory = viewModelFactory { initializer { ReviewViewModel(container) } })
    val ui = vm.ui

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Закрыть") }
            val progress by animateFloatAsState(
                if (ui.done + ui.remaining == 0) 0f else ui.done.toFloat() / (ui.done + ui.remaining),
                Motion.soft(450),
                label = "progress",
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
            Text("осталось ${ui.remaining}", style = MaterialTheme.typography.labelLarge)
        }

        val cur = ui.current
        when {
            ui.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            cur == null -> Finished(ui.done, onClose)
            else -> {
                // Новая карточка мягко сменяет прошлую; уходящая остаётся открытой, пока гаснет
                AnimatedContent(
                    cur,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentKey = { it.card.id },
                    transitionSpec = { Motion.exerciseEnter togetherWith Motion.exerciseExit },
                    label = "card",
                ) { item ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val revealed = if (item.card.id == ui.current?.card?.id) ui.revealed else true
                        CardFace(item, revealed, onReveal = vm::reveal, onSpeak = vm::speak)
                    }
                }
                // Знакомое слово можно убрать насовсем — и при первом показе, и в повторении; вернуть — в «Словах»
                TextButton(onClick = vm::markKnown, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (cur.card.state == 0) "Я уже знаю это слово" else "Знаю это слово — больше не показывать")
                }
                AnimatedContent(
                    ui.revealed,
                    transitionSpec = { fadeIn(Motion.soft(220, 60)) togetherWith fadeOut(Motion.soft(120)) },
                    label = "buttons",
                ) { revealed ->
                    if (!revealed) {
                        Button(onClick = vm::reveal, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Показать ответ") }
                    } else {
                        RatingButtons(ui.previews, vm::rate)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CardFace(item: CardWithWord, revealed: Boolean, onReveal: () -> Unit, onSpeak: (String, Boolean) -> Unit) {
    val w = item.word
    val enFirst = item.card.direction == 0
    Panel(Modifier.clickable(enabled = !revealed, onClick = onReveal)) {
        Text(
            if (enFirst) "Что это значит?" else "Как это по-английски?",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        if (enFirst) EnglishWord(w.en, w.transcription, onSpeak) else BigText(w.ru)

        AnimatedVisibility(revealed, enter = Motion.appear, exit = Motion.disappear) {
            Column {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                if (enFirst) BigText(w.ru) else EnglishWord(w.en, w.transcription, onSpeak)
                if (w.example != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(w.example, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onSpeak(w.example, false) }) {
                            Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Озвучить пример", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    w.exampleRu?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun EnglishWord(en: String, ipa: String?, onSpeak: (String, Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            BigText(en)
            if (ipa != null) {
                Text("/$ipa/", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = { onSpeak(en, false) }) {
            Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Озвучить", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun BigText(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun RatingButtons(previews: Map<Rating, String>, onRate: (Rating) -> Unit) {
    val items = listOf(
        Triple(Rating.Again, "Снова", MaterialTheme.colorScheme.error),
        Triple(Rating.Hard, "Трудно", Warm),
        Triple(Rating.Good, "Хорошо", MaterialTheme.colorScheme.primary),
        Triple(Rating.Easy, "Легко", Success),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (rating, label, color) ->
            Button(
                onClick = { onRate(rating) },
                modifier = Modifier.weight(1f).height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.16f), contentColor = color),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    Text(previews[rating].orEmpty(), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Finished(done: Int, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("На сегодня всё", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (done > 0) "Карточек пройдено: $done. Следующие появятся, когда подойдёт время повторения."
            else "Сейчас нечего повторять — загляните позже.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.primary)) {
            Text("Назад")
        }
    }
}
