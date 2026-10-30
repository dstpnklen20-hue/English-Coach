package com.englishcoach.app.ui.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishcoach.app.data.practice.Cloze
import com.englishcoach.app.data.practice.DrillSet
import com.englishcoach.app.data.practice.Match
import com.englishcoach.app.data.practice.Phrase
import com.englishcoach.app.data.practice.PracticeKind
import com.englishcoach.app.data.practice.Tiles
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.theme.Motion
import com.englishcoach.app.ui.theme.Success
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Название и описание вида тренировки — для «Практики» и списка наборов. */
internal data class DrillInfo(val title: String, val subtitle: String, val about: String) {
    companion object {
        fun of(kind: String) = when (kind) {
            PracticeKind.TRANSLATE -> DrillInfo(
                "Скажи по-английски", "Перевод фраз из плиток-слов",
                "Фраза по-русски — соберите её по-английски. Среди плиток есть лишние слова. Готовит к разговору: мысль → английская фраза.",
            )
            PracticeKind.CLOZE -> DrillInfo(
                "Текст с пропусками", "Слова в связном тексте",
                "Прочитайте текст и вставьте слова из банка. Незнакомое слово в тексте можно нажать — появится перевод.",
            )
            PracticeKind.MATCH -> DrillInfo(
                "Пары слов", "Сочетания и фразовые глаголы",
                "Соедините половинки: какой глагол с каким словом говорят по-английски (make a decision, а не do a decision).",
            )
            else -> DrillInfo(
                "Что ответишь?", "Быстрые ответы в живых ситуациях",
                "Вам что-то сказали — выберите самый естественный ответ. После ошибки объясню, почему вариант не подходит.",
            )
        }
    }
}

/** Список наборов одного вида тренировки по уровням. */
@Composable
fun DrillListScreen(kind: String, onOpen: (String) -> Unit, onClose: () -> Unit) {
    val c = LocalAppContainer.current
    val all by produceState(c.practice.cachedDrills()) { value = c.practice.drills() }
    val best by remember(kind) { c.practice.bestScores(kind) }.collectAsStateWithLifecycle(emptyList())
    val bMap = best.associate { it.itemId to it.best }
    val info = DrillInfo.of(kind)
    PracticeFrame(progress = null, onClose = onClose) {
        Text(info.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(info.about, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        all.filter { it.kind == kind }.groupBy { it.level }.forEach { (level, sets) ->
            Section("Уровень $level")
            sets.forEach { d ->
                ItemRow(
                    d.title,
                    listOf(d.topic, bMap[d.id]?.let { "лучший результат $it%" } ?: "${d.size} заданий").filter { it.isNotEmpty() }.joinToString(" · "),
                    bMap[d.id],
                ) { onOpen(d.id) }
            }
        }
    }
}

/** Один набор тренировки — экран по его виду. */
@Composable
fun DrillScreen(setId: String, onClose: () -> Unit) {
    val c = LocalAppContainer.current
    val set by produceState<DrillSet?>(null, setId) { value = c.practice.drills().firstOrNull { it.id == setId } }
    val d = set ?: return
    val scope = rememberCoroutineScope()
    var startedAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val finish = { correct: Int, total: Int ->
        val ms = System.currentTimeMillis() - startedAt
        scope.launch { c.practice.log(d.kind, d.id, correct, total, ms) }
        Unit
    }
    val restart = { startedAt = System.currentTimeMillis() }
    when (d.kind) {
        PracticeKind.TRANSLATE -> TranslateDrill(d, finish, restart, onClose)
        PracticeKind.CLOZE -> ClozeDrill(d, finish, restart, onClose)
        PracticeKind.MATCH -> MatchDrill(d, finish, restart, onClose)
        else -> ReplyDrill(d, finish, restart, onClose)
    }
}

// ——— Скажи по-английски ———

@Composable
private fun TranslateDrill(d: DrillSet, finish: (Int, Int) -> Unit, restart: () -> Unit, onClose: () -> Unit) {
    val n = d.phrases.size
    var idx by rememberSaveable { mutableIntStateOf(0) }
    var correct by rememberSaveable { mutableIntStateOf(0) }
    PracticeFrame(progress = idx.toFloat() / n, onClose = onClose) {
        if (idx >= n) {
            ResultBlock(correct, n, onRetry = { idx = 0; correct = 0; restart() }, onClose = onClose)
            return@PracticeFrame
        }
        Text("Фраза ${idx + 1} из $n · соберите по-английски", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        key(idx) {
            TranslateTask(d.phrases[idx]) { ok ->
                if (ok) correct++
                idx++
                if (idx == n) finish(correct, n)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranslateTask(p: Phrase, onDone: (Boolean) -> Unit) {
    val c = LocalAppContainer.current
    val words = remember(p) { Tiles.of(p) }
    // Индексы плиток, чтобы одинаковые слова не путались
    val pool = remember(p) { mutableStateListOf<Int>().apply { addAll(words.indices.shuffled()) } }
    val picked = remember(p) { mutableStateListOf<Int>() }
    var failedOnce by remember(p) { mutableStateOf(false) }
    var solved by remember(p) { mutableStateOf(false) }
    var revealed by remember(p) { mutableStateOf(false) }
    var hint by remember(p) { mutableStateOf<String?>(null) }
    val finished = solved || revealed
    val shown = remember(p) { MutableTransitionState(false).apply { targetState = true } }

    AnimatedVisibility(shown, enter = Motion.exerciseEnter) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel { Text(p.ru, style = MaterialTheme.typography.headlineSmall) }
            Panel {
                FlowRow(Modifier.heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    picked.toList().forEach { i ->
                        AssistChip(onClick = { if (!finished) { picked.remove(i); pool.add(i) } }, label = { Text(words[i], fontWeight = FontWeight.SemiBold) })
                    }
                }
            }
            if (!finished) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pool.toList().forEach { i ->
                        AssistChip(onClick = { pool.remove(i); picked.add(i) }, label = { Text(words[i]) })
                    }
                }
                Button(
                    onClick = {
                        if (Tiles.isRight(p, picked.map { words[it] })) {
                            solved = true
                            hint = null
                            c.tts.speak(p.en)
                        } else {
                            failedOnce = true
                            hint = p.hint ?: "Пока неверно. Проверьте порядок слов и уберите лишние."
                        }
                    },
                    enabled = picked.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("Проверить") }
                hint?.let {
                    HintCard(it)
                    TextButton(onClick = { revealed = true; c.tts.speak(p.en) }) { Text("Показать ответ") }
                }
            } else {
                SpokenLine(p.en, if (solved) Success else MaterialTheme.colorScheme.primary)
                Text(
                    when {
                        revealed -> "Запомните этот вариант"
                        failedOnce -> "Теперь верно!"
                        else -> "Верно!"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (solved) Success else MaterialTheme.colorScheme.primary,
                )
                // В счёт идёт только ответ с первой попытки — как в уроках
                Button(onClick = { onDone(solved && !failedOnce) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Дальше") }
            }
        }
    }
}

/** Английская фраза с кнопкой озвучки. */
@Composable
private fun SpokenLine(en: String, color: Color) {
    val c = LocalAppContainer.current
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(en, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.weight(1f))
            IconButton(onClick = { c.tts.speak(en) }) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Озвучить", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ——— Текст с пропусками ———

private val WORD = Regex("[A-Za-z']+")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClozeDrill(d: DrillSet, finish: (Int, Int) -> Unit, restart: () -> Unit, onClose: () -> Unit) {
    val c = LocalAppContainer.current
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    val tokens = remember(d) { Cloze.parse(d.text) }
    val gaps = remember(tokens) { tokens.filterIsInstance<Cloze.Gap>() }
    val bank = remember(d, attempt) { Cloze.bank(d.text, d.extra).shuffled() }
    // filled[номер пропуска] — индекс слова из банка или -1
    val filled = remember(d, attempt) { mutableStateListOf<Int>().apply { repeat(gaps.size) { add(-1) } } }
    var current by remember(d, attempt) { mutableIntStateOf(0) }
    var checked by remember(d, attempt) { mutableStateOf(false) }
    var selected by remember(d, attempt) { mutableStateOf<String?>(null) }
    val n = gaps.size
    val score = gaps.count { Cloze.isRight(it, bank.getOrNull(filled[it.index])) }

    fun nextEmpty(from: Int) = (0 until n).map { (from + it) % n }.firstOrNull { filled[it] < 0 } ?: from

    // Банк слов и перевод нажатого слова закреплены внизу: длинный текст прокручивается отдельно
    val bottom: @Composable () -> Unit = {
        selected?.let { WordPanel(it, d.glossary) }
        val used = filled.toSet()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            bank.indices.filter { it !in used }.forEach { i ->
                AssistChip(onClick = {
                    filled[current] = i
                    current = nextEmpty(current)
                }, label = { Text(bank[i], style = MaterialTheme.typography.titleMedium) })
            }
        }
        Button(
            onClick = {
                checked = true
                finish(score, n)
            },
            enabled = filled.all { it >= 0 },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Проверить") }
    }

    PracticeFrame(progress = if (checked) 1f else filled.count { it >= 0 }.toFloat() / n, onClose = onClose, bottom = bottom.takeIf { !checked }) {
        Text("${d.topic} · ${d.level}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(d.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        if (!checked) {
            Text(
                "Нажмите на пропуск, затем на слово ниже. Нажатие на заполненный пропуск освобождает его. Любое слово текста — перевод.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Panel {
            // Пропуски по центру строки, иначе они «проваливаются» ниже текста
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                tokens.forEach { t ->
                    when (t) {
                        is Cloze.Word -> {
                            val w = WORD.find(t.text)?.value
                            Text(
                                t.text,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 30.sp),
                                modifier = if (w != null) Modifier.clickable { selected = w } else Modifier,
                                color = if (w != null && w.equals(selected, ignoreCase = true)) MaterialTheme.colorScheme.primary else Color.Unspecified,
                            )
                        }
                        is Cloze.Gap -> Row(verticalAlignment = Alignment.CenterVertically) {
                            val word = bank.getOrNull(filled[t.index])
                            GapChip(
                                word = word,
                                active = !checked && t.index == current,
                                result = if (checked) Cloze.isRight(t, word) else null,
                                right = t.accept.first(),
                            ) {
                                if (!checked) {
                                    if (filled[t.index] >= 0) filled[t.index] = -1
                                    current = t.index
                                }
                            }
                            if (t.tail.isNotEmpty()) Text(t.tail, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp))
                        }
                    }
                }
            }
        }
        if (checked) {
            selected?.let { WordPanel(it, d.glossary) }
            if (d.ru.isNotBlank()) {
                Panel { Text(d.ru, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            OutlinedButton(onClick = { c.tts.speak(gaps.fold(d.text) { s, g -> s.replaceFirst(Regex("\\[[^\\]]*\\]"), g.accept.first()) }, slow = true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Послушать текст")
            }
            ResultBlock(score, n, onRetry = { attempt++; restart() }, onClose = onClose)
        }
    }
}

/** Пропуск в тексте: пустой (подчёркивание), заполненный или проверенный (зелёный / красный с верным словом). */
@Composable
private fun GapChip(word: String?, active: Boolean, result: Boolean?, right: String, onClick: () -> Unit) {
    val color = when (result) {
        true -> Success
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.primary
    }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = if (active) 0.28f else 0.12f),
        border = if (active) BorderStroke(1.5.dp, color) else null,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
            if (result == false) {
                if (word != null) Text(word, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp), color = color, textDecoration = TextDecoration.LineThrough)
                Text(" $right", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp), fontWeight = FontWeight.SemiBold, color = Success)
            } else {
                Text(word ?: "______", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp), fontWeight = FontWeight.SemiBold, color = color)
            }
        }
    }
}

// ——— Пары слов ———

@Composable
private fun MatchDrill(d: DrillSet, finish: (Int, Int) -> Unit, restart: () -> Unit, onClose: () -> Unit) {
    val c = LocalAppContainer.current
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    val n = d.pairs.size
    val left = remember(d, attempt) { d.pairs.indices.shuffled() }
    val right = remember(d, attempt) { d.pairs.indices.shuffled() }
    val done = remember(d, attempt) { mutableStateListOf<Int>() }
    var selL by remember(d, attempt) { mutableStateOf<Int?>(null) }
    var selR by remember(d, attempt) { mutableStateOf<Int?>(null) }
    var wrong by remember(d, attempt) { mutableStateOf<Pair<Int, Int>?>(null) }
    var mistakes by remember(d, attempt) { mutableIntStateOf(0) }

    // Неверная пара коротко подсвечивается красным и сбрасывается
    LaunchedEffect(wrong) {
        if (wrong != null) {
            delay(700)
            wrong = null
        }
    }

    fun tryPair() {
        val l = selL ?: return
        val r = selR ?: return
        selL = null
        selR = null
        if (l == r) {
            done.add(l)
            val p = d.pairs[l]
            c.tts.speak(p.example.ifBlank { p.a })
            if (done.size == n) finish(Match.score(n, mistakes), n)
        } else {
            mistakes++
            wrong = l to r
        }
    }

    PracticeFrame(progress = done.size.toFloat() / n, onClose = onClose) {
        if (done.size == n) {
            Text("Все пары собраны", style = MaterialTheme.typography.titleMedium, color = Success)
            Panel {
                d.pairs.forEach { p ->
                    // Сочетание (make + a decision) пишется слитно с переводом ниже; пара «слово — перевод» — через тире
                    val phrase = if (p.ru.isNotBlank()) "${p.a} ${p.b}" else "${p.a} — ${p.b}"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(phrase, style = MaterialTheme.typography.titleMedium)
                            if (p.ru.isNotBlank()) Text(p.ru, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (p.example.isNotBlank()) Text(p.example, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { c.tts.speak(p.example.ifBlank { p.a }) }) {
                            Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Озвучить", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            ResultBlock(Match.score(n, mistakes), n, onRetry = { attempt++; restart() }, onClose = onClose)
            return@PracticeFrame
        }
        Text(
            "Нажмите слово слева, затем подходящее справа · ошибок: $mistakes",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
        )
        Text(d.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                left.forEach { i ->
                    MatchButton(d.pairs[i].a, done = i in done, selected = selL == i, wrong = wrong?.first == i) {
                        selL = i
                        tryPair()
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                right.forEach { i ->
                    MatchButton(d.pairs[i].b, done = i in done, selected = selR == i, wrong = wrong?.second == i) {
                        selR = i
                        tryPair()
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchButton(text: String, done: Boolean, selected: Boolean, wrong: Boolean, onClick: () -> Unit) {
    val color = when {
        done -> Success
        wrong -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.primary
        else -> null
    }
    OutlinedButton(
        onClick = { if (!done) onClick() },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        // Узкие поля: на телефоне длинный перевод помещается в две строки, а не в три
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = if (color != null) {
            ButtonDefaults.outlinedButtonColors(containerColor = color.copy(alpha = if (done) 0.10f else 0.18f), contentColor = color)
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
    ) { Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center) }
}

// ——— Что ответишь? ———

@Composable
private fun ReplyDrill(d: DrillSet, finish: (Int, Int) -> Unit, restart: () -> Unit, onClose: () -> Unit) {
    val c = LocalAppContainer.current
    val n = d.items.size
    var idx by rememberSaveable { mutableIntStateOf(0) }
    var correct by rememberSaveable { mutableIntStateOf(0) }
    PracticeFrame(progress = idx.toFloat() / n, onClose = onClose) {
        if (idx >= n) {
            ResultBlock(correct, n, onRetry = { idx = 0; correct = 0; restart() }, onClose = onClose)
            return@PracticeFrame
        }
        val item = d.items[idx]
        LaunchedEffect(idx) { c.tts.speak(item.q) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Ситуация ${idx + 1} из $n",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { c.tts.speak(item.q) }) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Послушать", tint = MaterialTheme.colorScheme.primary)
            }
        }
        item.ru?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        // Ситуация уже показана выше — в самом упражнении только реплика и варианты
        ExerciseView(remember(item) { item.copy(ru = null) }) { ok ->
            if (ok) correct++
            idx++
            if (idx == n) finish(correct, n)
        }
    }
}
