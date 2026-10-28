package com.englishcoach.app.ui.theme

import androidx.core.graphics.ColorUtils

data class NamedPalette(val id: String, val name: String, val palette: Palette)

/** Тёмные варианты пресетов из «Тик Так». */
object Palettes {
    private fun p(id: String, name: String, bg: Long, surface: Long, text: Long, accent: Long): NamedPalette {
        val a = accent or 0xFF000000
        // Текст на кнопках акцента: тёмный на светлом акценте, белый на тёмном
        val onAccent = if (ColorUtils.calculateLuminance(a.toInt()) > 0.4) 0xFF141218 else 0xFFFFFFFF
        return NamedPalette(id, name, Palette(bg or 0xFF000000, surface or 0xFF000000, text or 0xFF000000, a, onAccent))
    }

    val all = listOf(
        p("default", "Стандартная", 0x15131A, 0x2B2833, 0xE6E1E9, 0x5B8CFF),
        p("obsidian", "Обсидиан", 0x1E1E1E, 0x2A2A2A, 0xDADADA, 0xA78BFA),
        p("nord", "Нордик", 0x2E3440, 0x3B4252, 0xECEFF4, 0x88C0D0),
        p("dracula", "Дракула", 0x282A36, 0x343746, 0xF8F8F2, 0xBD93F9),
        p("solarized", "Солярис", 0x002B36, 0x073642, 0x93A1A1, 0x2AA198),
        p("gruvbox", "Грувбокс", 0x282828, 0x3C3836, 0xEBDBB2, 0xFABD2F),
        p("tokyo", "Токио", 0x1A1B26, 0x24283B, 0xC0CAF5, 0x7AA2F7),
        p("mocha", "Мокка-Пастель", 0x1E1E2E, 0x313244, 0xCDD6F4, 0xCBA6F7),
        p("forest", "Лес", 0x12201A, 0x1E3229, 0xDDEBE2, 0x5FD38D),
        p("ocean", "Океан", 0x0B1C2C, 0x132F45, 0xD8E8F5, 0x4FC3F7),
        p("sunset", "Закат", 0x22141A, 0x3A222B, 0xF7E3E0, 0xFF7A59),
        p("rose", "Роза", 0x24151D, 0x3A2130, 0xF8E1EA, 0xFF6B9D),
        p("coffee", "Кофе", 0x1F1712, 0x33261E, 0xEBDDD0, 0xD2A679),
        p("mono", "Монохром", 0x000000, 0x161616, 0xFFFFFF, 0xE0E0E0),
        p("win11", "Windows 11", 0x202020, 0x2C2C2C, 0xFFFFFF, 0x60CDFF),
        p("midnight", "Полночь", 0x0A0F1F, 0x121A33, 0xE1E7FF, 0x6C8CFF),
    )

    fun byId(id: String?): Palette = (all.firstOrNull { it.id == id } ?: all.first()).palette
}
