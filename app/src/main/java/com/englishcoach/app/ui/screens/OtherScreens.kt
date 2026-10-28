package com.englishcoach.app.ui.screens

import androidx.compose.runtime.Composable
import com.englishcoach.app.ui.components.ComingSoon
import com.englishcoach.app.ui.components.ScreenColumn

// Заглушки разделов. Каждая будет заменена своим экраном на соответствующем этапе.

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
        ComingSoon(2, "Напоминания: время, дни, отключение. Длительность сессии, новых слов в день.")
        ComingSoon(5, "Ключ ИИ (хранится зашифрованно на устройстве).")
        ComingSoon(6, "Синхронизация телефона и планшета.")
    }
}
