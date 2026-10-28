package com.englishcoach.app.ui.screens

import androidx.compose.runtime.Composable
import com.englishcoach.app.ui.components.ComingSoon
import com.englishcoach.app.ui.components.ScreenColumn

// Заглушки разделов. Каждая будет заменена своим экраном на соответствующем этапе.

@Composable
fun ProgressScreen() {
    ScreenColumn(title = "Прогресс") {
        ComingSoon(8, "Статистика, уровни по навыкам, цели с дедлайнами и достижения.")
    }
}
