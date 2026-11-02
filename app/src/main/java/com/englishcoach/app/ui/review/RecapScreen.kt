package com.englishcoach.app.ui.review

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.englishcoach.app.AppContainer
import com.englishcoach.app.data.StudyRepository
import com.englishcoach.app.data.db.CardWithWord
import com.englishcoach.app.data.practice.PracticeKind
import com.englishcoach.app.data.practice.Recap
import com.englishcoach.app.data.practice.RecapSession
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.practice.PracticeFrame
import com.englishcoach.app.ui.practice.ResultBlock
import com.englishcoach.app.ui.theme.Motion
import com.englishcoach.app.ui.theme.Success
import kotlinx.coroutines.launch

data class RecapUi(
    val loading: Boolean = true,
    val counts: Map<Recap.Source, Int> = emptyMap(),
    /** null — экран выбора; иначе идёт занятие. */
    val current: CardWithWord? = null,
    val revealed: Boolean = false,
    val remaining: Int = 0,
    val finished: Boolean = false,
    val correct: Int = 0,
    val total: Int = 0,
)

class RecapViewModel(private val c: AppContainer) : ViewModel() {
    var ui by mutableStateOf(RecapUi())
        private set

    private var cards: List<CardWithWord> = emptyList()
    private var session: RecapSession? = null
    private var source = Recap.Source.ALL
    private var count: Int? = null
    private var startedAt = 0L

    init {
        reload()
    }

    private fun reload(then: () -> Unit = {}) {
        viewModelScope.launch {
            cards = c.study.studiedCards()
            ui = ui.copy(loading = false, counts = Recap.counts(cards, StudyRepository.dayStart()))
            then()
        }
    }

    fun start(source: Recap.Source, count: Int?) {
        this.source = source
        this.count = count
        startedAt = System.currentTimeMillis()
        session = RecapSession(Recap.pick(cards, source, StudyRepository.dayStart(), count))
        show()
    }

    /** «Ещё раз» — новая подборка того же списка: отметки могли измениться. */
    fun again() {
        ui = ui.copy(finished = false)
        reload { start(source, count) }
    }

    fun reveal() {
        val cur = ui.current ?: return
        ui = ui.copy(revealed = true)
        if (cur.card.direction == 1) speak(cur.word.en)
    }

    fun answer(remembered: Boolean) {
        if (!ui.revealed) return // защита от двойного нажатия: на экране уже следующая карточка
        session?.answer(remembered)
        show()
    }

    /** «Знаю» / «Вернуть в изучение»: меняет только отметку; слово уходит из этого занятия. */
    fun toggleKnown() {
        val cur = ui.current ?: return
        val mark = !cur.word.known
        c.appScope.launch { c.study.setKnown(cur.word.id, mark) }
        session?.dropCurrent()
        show()
    }

    fun speak(text: String, slow: Boolean = false) = c.tts.speak(text, slow)

    private fun show() {
        val s = session ?: return
        val next = s.current
        if (next == null) {
            logResult()
            // Если все слова убраны из занятия, показывать нечего — возвращаемся к выбору
            ui = if (s.total > 0) ui.copy(current = null, finished = true, correct = s.correct, total = s.total) else ui.copy(current = null)
            if (s.total == 0) reload()
            session = null
            return
        }
        ui = ui.copy(current = next, revealed = false, remaining = s.remaining, finished = false, correct = s.correct, total = s.total)
        if (next.card.direction == 0) speak(next.word.en)
    }

    /** Время идёт в минуты дня; расписание карточек и дневной лимит новых не затрагиваются. */
    private fun logResult() {
        val s = session ?: return
        if (s.answeredCount == 0) return
        val ms = System.currentTimeMillis() - startedAt
        val (correct, total, id) = Triple(s.correct, s.answeredCount, source.name)
        c.appScope.launch { c.practice.log(PracticeKind.WORD_RECAP, id, correct, total, ms) }
    }

    // Вышли посреди занятия — пройденная часть всё равно считается
    override fun onCleared() = logResult()
}

/** Повторить пройденные слова: выбор списка и занятие на тех же карточках, без влияния на расписание. */
@Composable
fun RecapScreen(onClose: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: RecapViewModel = viewModel(factory = viewModelFactory { initializer { RecapViewModel(container) } })
    val ui = vm.ui
    val cur = ui.current
    when {
        ui.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        ui.finished -> PracticeFrame(progress = 1f, onClose = onClose) {
            ResultBlock(ui.correct, ui.total, onRetry = vm::again, onClose = onClose)
        }
        cur != null -> Session(vm, cur, onClose)
        else -> Setup(ui.counts, onStart = vm::start, onClose = onClose)
    }
}

/** Варианты размера занятия; null — все слова списка. */
private val SIZES = listOf<Int?>(10, 20, null)

@Composable
private fun Setup(counts: Map<Recap.Source, Int>, onStart: (Recap.Source, Int?) -> Unit, onClose: () -> Unit) {
    var size by rememberSaveable { mutableStateOf<Int?>(20) }
    PracticeFrame(progress = null, onClose = onClose) {
        Text("Повторить пройденные слова", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Те же карточки, но можно в любой момент и сколько угодно раз: расписание повторений от этого не меняется.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Сколько слов", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SIZES.forEach { s ->
                FilterChip(selected = size == s, onClick = { size = s }, label = { Text(s?.toString() ?: "Все") })
            }
        }
        Text("Что повторить", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (counts[Recap.Source.ALL] == 0) {
            Text(
                "Пока нечего: сначала пройдите новые слова на экране «Сегодня».",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Recap.Source.entries.forEach { src ->
            val n = counts[src] ?: 0
            Panel(if (n > 0) Modifier.clickable { onStart(src, size) } else Modifier) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val color = if (n > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        Text(src.label, style = MaterialTheme.typography.titleMedium, color = color)
                        Text(src.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("$n", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Session(vm: RecapViewModel, cur: CardWithWord, onClose: () -> Unit) {
    val ui = vm.ui
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Закрыть") }
            Text(
                "Повторение вне расписания",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("осталось ${ui.remaining}", style = MaterialTheme.typography.labelLarge)
        }
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
        TextButton(onClick = vm::toggleKnown, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (cur.word.known) "Вернуть в изучение" else "Знаю это слово — больше не показывать")
        }
        if (!ui.revealed) {
            Button(onClick = vm::reveal, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Показать ответ") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val forgot = MaterialTheme.colorScheme.error
                Button(
                    onClick = { vm.answer(false) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = forgot.copy(alpha = 0.16f), contentColor = forgot),
                ) { Text("Не помню") }
                Button(
                    onClick = { vm.answer(true) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Success.copy(alpha = 0.16f), contentColor = Success),
                ) { Text("Помню") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
