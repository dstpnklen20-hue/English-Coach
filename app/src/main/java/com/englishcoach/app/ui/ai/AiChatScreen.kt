package com.englishcoach.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.anthropic.models.messages.MessageParam
import com.englishcoach.app.AppContainer
import com.englishcoach.app.data.ai.AiException
import com.englishcoach.app.data.ai.AiRepository
import com.englishcoach.app.data.ai.ChatReply
import com.englishcoach.app.data.practice.PracticeKind
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.practice.MicButton
import com.englishcoach.app.ui.theme.Success
import com.englishcoach.app.ui.theme.Warm
import kotlinx.coroutines.launch

val AI_TOPICS = listOf("Игры", "Строительство", "Путешествия", "Технологии", "Спорт", "Фильмы", "Свободная тема")

/** Строка чата: реплика ученика (с разбором) или ответ собеседника. */
sealed interface ChatItem {
    data class Mine(val text: String, val correction: String = "", val explanation: String = "") : ChatItem
    data class Theirs(val reply: ChatReply) : ChatItem
}

class AiChatViewModel(private val c: AppContainer) : ViewModel() {
    val items = mutableStateListOf<ChatItem>()
    var topic by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // История для API — только дописывается, ответы модели кладутся целиком (toParam)
    private val history = mutableListOf<MessageParam>()
    private val startedAt = System.currentTimeMillis()
    private var sent = 0

    fun start(t: String) {
        topic = t
        send(START, visible = false)
    }

    fun send(text: String, visible: Boolean = true) {
        val t = topic ?: return
        if (text.isBlank() || loading) return
        error = null
        loading = true
        val userIndex = if (visible) items.size.also { items.add(ChatItem.Mine(text.trim())) } else -1
        history.add(AiRepository.userMessage(text.trim()))
        viewModelScope.launch {
            try {
                val (reply, msg) = c.tutor.chat(t, history)
                history.add(msg.toParam())
                if (userIndex >= 0) {
                    items[userIndex] = ChatItem.Mine(text.trim(), reply.correction, reply.explanationRu)
                    sent++
                }
                items.add(ChatItem.Theirs(reply))
                c.tts.speak(reply.reply)
            } catch (e: AiException) {
                history.removeAt(history.lastIndex) // неотвеченное сообщение убираем, чтобы можно было повторить
                if (userIndex >= 0) items.removeAt(userIndex)
                error = e.message
            } catch (e: Exception) {
                history.removeAt(history.lastIndex)
                if (userIndex >= 0) items.removeAt(userIndex)
                error = "Не удалось разобрать ответ. Попробуйте ещё раз."
            } finally {
                loading = false
            }
        }
    }

    fun speak(text: String) = c.tts.speak(text)

    fun addWord(en: String, ru: String) = viewModelScope.launch { c.study.addUserWord(en, ru) }

    override fun onCleared() {
        // Разговор засчитывается в шаг «Речь», если вы написали хотя бы одно сообщение
        if (sent > 0) {
            val n = sent
            c.appScope.launch { c.practice.log(PracticeKind.AI_CHAT, topic.orEmpty(), n, n, System.currentTimeMillis() - startedAt) }
        }
    }

    private companion object {
        const val START = "START"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiChatScreen(onClose: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: AiChatViewModel = viewModel(factory = viewModelFactory { initializer { AiChatViewModel(container) } })
    val listState = rememberLazyListState()
    LaunchedEffect(vm.items.size) { if (vm.items.isNotEmpty()) listState.animateScrollToItem(vm.items.lastIndex) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Закрыть") }
            Column {
                Text("Собеседник ИИ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    vm.topic ?: "Выберите тему разговора",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (vm.topic == null) {
            Text(
                "Пишите или говорите по-английски. Не знаете, как сказать, — пишите по-русски: собеседник подскажет. Ошибки будут разобраны под вашими сообщениями.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AI_TOPICS.forEach { t -> FilterChip(selected = false, onClick = { vm.start(t) }, label = { Text(t) }) }
            }
            return@Column
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vm.items) { item ->
                when (item) {
                    is ChatItem.Mine -> MineBubble(item)
                    is ChatItem.Theirs -> TheirBubble(item.reply, onSpeak = vm::speak, onAdd = vm::addWord)
                }
            }
            if (vm.loading) item { CircularProgressIndicator(Modifier.size(24.dp).padding(4.dp)) }
        }
        vm.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        InputBar(enabled = !vm.loading, onSend = { vm.send(it) })
    }
}

@Composable
private fun MineBubble(item: ChatItem.Mine) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier.widthIn(max = 320.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(item.text, style = MaterialTheme.typography.bodyLarge)
            if (item.correction.isNotBlank()) {
                Text("Лучше: ${item.correction}", style = MaterialTheme.typography.bodyMedium, color = Warm, fontWeight = FontWeight.Medium)
                if (item.explanation.isNotBlank()) {
                    Text(item.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TheirBubble(r: ChatReply, onSpeak: (String) -> Unit, onAdd: (String, String) -> Unit) {
    val added = remember { mutableStateListOf<String>() }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Column(
            Modifier.widthIn(max = 340.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.reply, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                IconButton(onClick = { onSpeak(r.reply) }) {
                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Озвучить", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(r.replyRu, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.newWords.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.newWords.forEach { w ->
                        val done = w.en in added
                        AssistChip(
                            onClick = { if (!done) { added.add(w.en); onAdd(w.en, w.ru) } },
                            label = { Text(if (done) "✓ ${w.en}" else "+ ${w.en} — ${w.ru}") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InputBar(enabled: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ваш ответ…") },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
            )
            IconButton(onClick = { onSend(text); text = "" }, enabled = enabled && text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Outlined.Send, "Отправить", tint = if (enabled) Success else MaterialTheme.colorScheme.outline)
            }
        }
        // Голосом: распознанный текст сразу уходит собеседнику
        MicButton { variants -> if (enabled) onSend(variants.first()) }
    }
}
