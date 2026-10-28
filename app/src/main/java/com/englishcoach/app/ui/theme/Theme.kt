package com.englishcoach.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Тёмная минималистичная палитра: один холодный акцент + мятный для «успеха» + тёплый для серий/дедлайнов.
val Accent = Color(0xFF7C9CFF)
val Success = Color(0xFF5FD4A8)
val Warm = Color(0xFFFFB86B)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF0F1115),
    primaryContainer = Color(0xFF243055),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Success,
    onSecondary = Color(0xFF0F1115),
    tertiary = Warm,
    onTertiary = Color(0xFF0F1115),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE8EAF0),
    surface = Color(0xFF0F1115),
    onSurface = Color(0xFFE8EAF0),
    surfaceVariant = Color(0xFF1C2029),
    onSurfaceVariant = Color(0xFF9AA1B2),
    surfaceContainer = Color(0xFF161922),
    surfaceContainerHigh = Color(0xFF1C2029),
    outline = Color(0xFF2C313D),
    outlineVariant = Color(0xFF232733),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun EnglishTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, shapes = AppShapes, content = content)
}
