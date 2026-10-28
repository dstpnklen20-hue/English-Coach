package com.englishcoach.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishcoach.app.ui.theme.Palettes
import com.englishcoach.app.ui.AppRoot
import com.englishcoach.app.ui.LocalAppContainer
import com.englishcoach.app.ui.theme.EnglishTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Тема всегда тёмная, поэтому значки системных панелей всегда светлые (не зависят от темы телефона)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val container = (application as EnglishApp).container
        setContent {
            val settings by container.settings.flow.collectAsStateWithLifecycle(null)
            EnglishTheme(palette = Palettes.byId(settings?.paletteId)) {
                // Surface задаёт фон и цвет текста/иконок по умолчанию для всех экранов
                Surface(color = MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(LocalAppContainer provides container) {
                        AppRoot()
                    }
                }
            }
        }
    }
}
