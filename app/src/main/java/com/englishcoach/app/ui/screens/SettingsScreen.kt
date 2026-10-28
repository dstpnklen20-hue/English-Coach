package com.englishcoach.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishcoach.app.data.Settings
import com.englishcoach.app.reminders.Reminders
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.ComingSoon
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.components.ScreenColumn
import com.englishcoach.app.ui.theme.Palettes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val DAY_NAMES = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private val SESSION_OPTIONS = listOf(15, 20, 30, 45, 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onPlacement: () -> Unit) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = remember { c.settings.flow }.collectAsStateWithLifecycle(null).value ?: return

    fun update(transform: (Settings) -> Settings) = scope.launch {
        c.settings.update(transform)
        Reminders.schedule(context, c.settings.current())
    }

    var pickTime by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        update { it.copy(remindersOn = granted) }
    }

    ScreenColumn(title = "Настройки") {
        Section("Учёба")
        Panel {
            var words by remember(s.newWordsPerDay) { mutableFloatStateOf(s.newWordsPerDay.toFloat()) }
            Text("Новых слов в день: ${words.roundToInt()}", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = words,
                onValueChange = { words = it },
                onValueChangeFinished = { update { it.copy(newWordsPerDay = words.roundToInt()) } },
                valueRange = 3f..25f,
                steps = 21,
            )
            Text("Длительность сессии", style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SESSION_OPTIONS.forEach { m ->
                    FilterChip(selected = s.sessionMinutes == m, onClick = { update { it.copy(sessionMinutes = m) } }, label = { Text("$m мин") })
                }
            }
        }

        Section("Напоминания")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Напоминать о занятиях", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = s.remindersOn, onCheckedChange = { on ->
                    val needAsk = on && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    if (needAsk) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else update { it.copy(remindersOn = on) }
                })
            }
            if (s.remindersOn) {
                Row(Modifier.clip(MaterialTheme.shapes.small).clickable { pickTime = true }.padding(vertical = 8.dp)) {
                    Text("Время:  ", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "%02d:%02d".format(s.reminderHour, s.reminderMinute),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_NAMES.forEachIndexed { i, name ->
                        val day = i + 1
                        FilterChip(
                            selected = day in s.reminderDays,
                            onClick = { update { it.copy(reminderDays = if (day in it.reminderDays) it.reminderDays - day else it.reminderDays + day) } },
                            label = { Text(name) },
                        )
                    }
                }
                Text(
                    "Если цель дня уже выполнена, напоминание не придёт.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Section("Оформление")
        Panel {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Palettes.all.forEach { np ->
                    PaletteSwatch(np.name, np.palette.bg, np.palette.surface, np.palette.accent, selected = s.paletteId == np.id) {
                        update { it.copy(paletteId = np.id) }
                    }
                }
            }
        }

        Section("Стартовый тест")
        Panel {
            Text("Текущий уровень: ${s.level}", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = onPlacement) { Text(if (s.placementDone) "Пройти заново" else "Пройти тест") }
        }

        ComingSoon(5, "Ключ ИИ (хранится зашифрованно на устройстве).")
        ComingSoon(6, "Синхронизация телефона и планшета.")
    }

    if (pickTime) TimeDialog(s.reminderHour, s.reminderMinute, onDismiss = { pickTime = false }) { h, m ->
        pickTime = false
        update { it.copy(reminderHour = h, reminderMinute = m) }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun PaletteSwatch(name: String, bg: Long, surface: Long, accent: Long, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.size(56.dp, 40.dp).clip(MaterialTheme.shapes.small).background(Color(bg))
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    MaterialTheme.shapes.small,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(14.dp).background(Color(surface), CircleShape))
                Box(Modifier.size(14.dp).background(Color(accent), CircleShape))
            }
        }
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        text = { TimePicker(state = state) },
    )
}
