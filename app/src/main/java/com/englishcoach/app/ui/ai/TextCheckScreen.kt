package com.englishcoach.app.ui.ai

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.englishcoach.app.data.ai.AiException
import com.englishcoach.app.data.ai.TextCheck
import com.englishcoach.app.data.practice.PracticeKind
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.practice.PracticeFrame
import com.englishcoach.app.ui.theme.Success
import com.englishcoach.app.ui.theme.Warm
import kotlinx.coroutines.launch

/** Проверка текста: пишете по-английски → исправленная версия и разбор ошибок по-русски. */
@Composable
fun TextCheckScreen(onClose: () -> Unit) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<TextCheck?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val startedAt = remember { System.currentTimeMillis() }

    PracticeFrame(progress = null, onClose = onClose) {
        Text("Проверка текста", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Напишите несколько предложений по-английски: о своём дне, работе, игре, поездке. ИИ исправит ошибки и объяснит их по-русски.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; result = null },
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            placeholder = { Text("Yesterday I go to work and…") },
            shape = MaterialTheme.shapes.medium,
        )
        if (loading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            val r = c.tutor.checkText(text.trim())
                            result = r
                            c.practice.log(PracticeKind.AI_WRITING, "text", 1, 1, System.currentTimeMillis() - startedAt)
                        } catch (e: AiException) {
                            error = e.message
                        } catch (e: Exception) {
                            error = "Не удалось разобрать ответ. Попробуйте ещё раз."
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Проверить") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        result?.let { r ->
            Panel {
                Text("Исправленный текст", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(r.corrected, style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = { c.tts.speak(r.corrected) }) { Text("Послушать") }
            }
            if (r.mistakes.isEmpty()) {
                Text("Ошибок нет — отлично!", style = MaterialTheme.typography.titleMedium, color = Success)
            }
            r.mistakes.forEach { m ->
                Panel {
                    Text("${m.original}  →  ${m.fixed}", style = MaterialTheme.typography.titleMedium, color = Warm)
                    Text(m.explanationRu, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(r.commentRu, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
