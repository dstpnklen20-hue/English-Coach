package com.englishcoach.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.components.ComingSoon
import com.englishcoach.app.ui.components.Panel
import com.englishcoach.app.ui.components.ScreenColumn

// Заглушки разделов этапа 0. Каждая будет заменена своим экраном на соответствующем этапе.

@Composable
fun WordsScreen() {
    val db = LocalAppContainer.current.db
    val count by remember { db.wordDao().countFlow() }.collectAsStateWithLifecycle(0)
    ScreenColumn(title = "Слова", subtitle = "Карточки с интервальным повторением (FSRS)") {
        Panel {
            Text("В словаре", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$count", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        ComingSoon(1, "Карточки EN→RU и RU→EN, озвучка, первые слова A1 по вашим темам.")
    }
}

@Composable
fun PracticeScreen() {
    ScreenColumn(title = "Практика") {
        ComingSoon(3, "Грамматика: короткие уроки и упражнения.")
        ComingSoon(3, "Чтение: тексты по вашим темам, перевод слова по нажатию.")
        ComingSoon(3, "Аудирование: послушать и записать фразу.")
        ComingSoon(4, "Произношение: повторить вслух, приложение проверит.")
        ComingSoon(4, "Диалоги: сценарии (аэропорт, магазин, стройка, игровой чат).")
        ComingSoon(5, "Собеседник ИИ: свободный разговор и разбор ошибок.")
    }
}

@Composable
fun ProgressScreen() {
    ScreenColumn(title = "Прогресс") {
        ComingSoon(8, "Статистика, уровни по навыкам, цели с дедлайнами и достижения.")
    }
}

@Composable
fun SettingsScreen() {
    ScreenColumn(title = "Настройки") {
        ComingSoon(2, "Напоминания: время, дни, отключение. Длительность сессии.")
        ComingSoon(5, "Ключ ИИ (хранится зашифрованно на устройстве).")
        ComingSoon(6, "Синхронизация телефона и планшета.")
    }
}
