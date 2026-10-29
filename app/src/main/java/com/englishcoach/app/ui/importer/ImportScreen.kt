package com.englishcoach.app.ui.importer

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.englishcoach.app.AppContainer
import com.englishcoach.app.data.ImportWord
import com.englishcoach.app.data.ai.AiException
import com.englishcoach.app.data.importer.Importer
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.Panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Строка предпросмотра: слово, перевод (можно поправить), пример и отметка «добавить». */
data class ImportRow(val en: String, val ru: String, val example: String?, val count: Int = 0, val selected: Boolean = true)

class ImportViewModel(private val c: AppContainer) : ViewModel() {
    val rows = mutableStateListOf<ImportRow>()
    var source by mutableStateOf<String?>(null)
        private set
    /** true — список пар «слово — перевод»; false — незнакомые слова из текста. */
    var pairsMode by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    init {
        // Текст, присланный через «Поделиться», открывает импорт сразу
        c.pendingShare.value?.let { shared ->
            c.pendingShare.value = null
            load(shared, "Из «Поделиться»")
        }
    }

    fun load(text: String, name: String) {
        busy = true
        message = null
        viewModelScope.launch {
            val known = c.study.knownWords()
            val parsed = withContext(Dispatchers.Default) {
                if (Importer.looksLikePairs(text)) {
                    true to Importer.parsePairs(text).map { ImportRow(it.en, it.ru, null, selected = it.en.lowercase() !in known) }
                } else {
                    // Из текста слов много — отмечаете только те, что хотите учить
                    false to Importer.extractWords(text, known).map { ImportRow(it.word, "", it.context, it.count, selected = false) }
                }
            }
            pairsMode = parsed.first
            rows.clear()
            rows.addAll(parsed.second)
            source = name
            busy = false
            if (rows.isEmpty()) message = "Новых слов не нашлось: всё уже есть в словаре или текст пустой."
        }
    }

    fun toggle(i: Int) {
        rows[i] = rows[i].copy(selected = !rows[i].selected)
    }

    fun setRu(i: Int, ru: String) {
        rows[i] = rows[i].copy(ru = ru)
    }

    /** Перевод отмеченных слов без перевода через ИИ (словарная форма + значение по контексту). */
    fun translate() {
        val todo = rows.withIndex().filter { it.value.selected && it.value.ru.isBlank() }.take(TRANSLATE_BATCH)
        if (todo.isEmpty()) return
        busy = true
        message = null
        viewModelScope.launch {
            try {
                val result = c.tutor.translate(todo.map { it.value.en to it.value.example })
                val byWord = result.associateBy { it.word.lowercase() }
                todo.forEach { (i, row) ->
                    byWord[row.en.lowercase()]?.let { t -> rows[i] = row.copy(en = t.lemma.ifBlank { row.en }, ru = t.ru) }
                }
            } catch (e: AiException) {
                message = e.message
            } finally {
                busy = false
            }
        }
    }

    fun addSelected() {
        val chosen = rows.filter { it.selected && it.ru.isNotBlank() }
        val topic = "Импорт: ${source.orEmpty().take(30)}"
        viewModelScope.launch {
            val added = c.study.addImported(chosen.map { ImportWord(it.en, it.ru, it.example, topic) })
            val noRu = rows.count { it.selected && it.ru.isBlank() }
            message = "Добавлено слов: $added." + if (noRu > 0) " Без перевода пропущено: $noRu." else ""
            rows.removeAll { it.selected && it.ru.isNotBlank() }
        }
    }

    private companion object {
        /** Не больше 40 слов за запрос — ответ короткий и быстрый. */
        const val TRANSLATE_BATCH = 40
    }
}

@Composable
fun ImportScreen(onClose: () -> Unit) {
    val c = LocalAppContainer.current
    val vm: ImportViewModel = viewModel(factory = viewModelFactory { initializer { ImportViewModel(c) } })
    val context = LocalContext.current
    val hasAi by c.ai.hasKey.collectAsStateWithLifecycle()
    var pasted by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "файл"
        // Ограничение 2 МБ: субтитры фильма — это обычно 50–150 КБ
        val text = context.contentResolver.openInputStream(uri)?.use { s ->
            String(s.readBytes().take(2_000_000).toByteArray(), Charsets.UTF_8)
        }.orEmpty()
        vm.load(text, name)
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Закрыть") }
            Column {
                Text("Импорт слов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                vm.source?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        if (vm.source == null) {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Файл со списком слов (CSV, экспорт Anki, заметка Obsidian .md) или любой текст: субтитры фильма (.srt), статья, " +
                        "глава книги. Из текста я выберу незнакомые слова и добавлю к ним примеры. Ещё можно нажать «Поделиться» " +
                        "в другом приложении и выбрать English Coach.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { picker.launch(arrayOf("text/*", "application/x-subrip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Выбрать файл")
                }
                OutlinedTextField(
                    value = pasted, onValueChange = { pasted = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    placeholder = { Text("…или вставьте текст сюда") }, shape = MaterialTheme.shapes.medium,
                )
                OutlinedButton(onClick = { vm.load(pasted, "Вставленный текст") }, enabled = pasted.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("Разобрать текст")
                }
            }
        }

        if (vm.busy) CircularProgressIndicator(Modifier.padding(8.dp))
        vm.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }

        if (vm.source != null && vm.rows.isNotEmpty()) {
            Text(
                if (vm.pairsMode) "Найдено пар: ${vm.rows.size}. Уже известные слова не отмечены."
                else "Незнакомые слова по частоте: ${vm.rows.size}. Отметьте те, что хотите учить, и добавьте перевод" +
                    if (hasAi) " — или нажмите «Перевести ИИ»." else ".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Без ключей по слову: после перевода две формы (soldier/soldiers) могут совпасть
                itemsIndexed(vm.rows) { i, row -> RowItem(row, onToggle = { vm.toggle(i) }, onRu = { vm.setRu(i, it) }) }
            }
            val missing = vm.rows.count { it.selected && it.ru.isBlank() }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!vm.pairsMode && hasAi) {
                    OutlinedButton(onClick = vm::translate, enabled = !vm.busy && missing > 0, modifier = Modifier.weight(1f)) {
                        Text("Перевести ИИ ($missing)")
                    }
                }
                Button(onClick = vm::addSelected, enabled = !vm.busy && vm.rows.any { it.selected && it.ru.isNotBlank() }, modifier = Modifier.weight(1f)) {
                    Text("Добавить (${vm.rows.count { it.selected && it.ru.isNotBlank() }})")
                }
            }
        }
    }
}

@Composable
private fun RowItem(row: ImportRow, onToggle: () -> Unit, onRu: (String) -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = row.selected, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.en, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (row.count > 1) Text("  ×${row.count}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                row.example?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
            }
        }
        if (row.selected) {
            OutlinedTextField(
                value = row.ru, onValueChange = onRu, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Перевод") }, singleLine = true, shape = MaterialTheme.shapes.medium,
            )
        }
    }
}
