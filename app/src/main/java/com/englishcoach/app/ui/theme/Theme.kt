package com.englishcoach.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils

/** Палитра в стиле приложения «Тик Так»: тема задаётся пятью цветами, остальное выводится смешиванием. Пресеты — в Palettes. */
data class Palette(
    val bg: Long,
    val surface: Long,
    val text: Long,
    val accent: Long,
    val onAccent: Long,
)

// Смысловые цвета обучения — общие для всех палитр
val Success = Color(0xFF5FD38D)
val Warm = Color(0xFFFFB86B)

private fun blend(a: Long, b: Long, ratio: Float) = Color(ColorUtils.blendARGB(a.toInt(), b.toInt(), ratio))

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun EnglishTheme(palette: Palette = Palettes.all.first().palette, content: @Composable () -> Unit) {
    val bg = Color(palette.bg)
    val surface = Color(palette.surface)
    val text = Color(palette.text)
    val accent = Color(palette.accent)
    val container = blend(palette.accent, palette.bg, 0.78f)
    val raised = blend(palette.surface, palette.bg, 0.5f)
    val secondaryText = blend(palette.text, palette.bg, 0.35f)
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color(palette.onAccent),
        primaryContainer = container,
        onPrimaryContainer = text,
        secondary = accent,
        onSecondary = Color(palette.onAccent),
        secondaryContainer = container,
        onSecondaryContainer = text,
        tertiary = Warm,
        onTertiary = bg,
        background = bg,
        onBackground = text,
        surface = bg,
        onSurface = text,
        surfaceVariant = surface,
        onSurfaceVariant = secondaryText,
        surfaceContainerLowest = bg,
        surfaceContainerLow = raised,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface,
        surfaceTint = accent,
        outline = blend(palette.text, palette.bg, 0.7f),
        outlineVariant = blend(palette.text, palette.bg, 0.85f),
    )
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
}
