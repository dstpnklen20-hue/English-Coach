package com.englishcoach.app.data.practice

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Уровень сложности одного и того же задания. */
enum class Difficulty(val label: String, val about: String) {
    EASY("Лёгкий", "меньше заданий, без ловушек, с подсказками"),
    NORMAL("Обычный", "как задумано"),
    HARD("Сложный", "ввод с клавиатуры, больше ловушек, на время"),
    ;

    fun next(): Difficulty? = entries.getOrNull(ordinal + 1)
}

/** Автоповышение: столько результатов подряд от [GOOD_PERCENT]% поднимают уровень на ступень. */
object AutoLevel {
    const val STREAK = 3
    const val GOOD_PERCENT = 90

    /** Новая серия и новый уровень после результата; raised — уровень только что повышен. */
    data class Step(val streak: Int, val level: Difficulty, val raised: Boolean)

    fun after(level: Difficulty, streak: Int, correct: Int, total: Int): Step {
        val good = total > 0 && correct * 100 >= total * GOOD_PERCENT
        val s = if (good) streak + 1 else 0
        val up = level.next()
        return if (s >= STREAK && up != null) Step(0, up, true) else Step(if (up == null) 0 else s, level, false)
    }
}

/**
 * Выбранный уровень для каждого вида задания (translate, cloze, match, reply, group) и серия хороших результатов.
 * Хранится на устройстве: это настройка удобства, а не прогресс.
 */
class DifficultyStore(context: Context) {
    private val prefs = context.getSharedPreferences("difficulty", Context.MODE_PRIVATE)
    private val _levels = MutableStateFlow(load())
    val levels: StateFlow<Map<String, Difficulty>> = _levels

    fun get(kind: String): Difficulty = _levels.value[kind] ?: Difficulty.NORMAL

    fun set(kind: String, level: Difficulty) {
        prefs.edit().putString(kind, level.name).putInt(streakKey(kind), 0).apply()
        _levels.value = _levels.value + (kind to level)
    }

    /** Учесть результат; вернёт новый уровень, если он повысился. */
    fun record(kind: String, correct: Int, total: Int): Difficulty? {
        val step = AutoLevel.after(get(kind), prefs.getInt(streakKey(kind), 0), correct, total)
        prefs.edit().putInt(streakKey(kind), step.streak).apply()
        if (!step.raised) return null
        prefs.edit().putString(kind, step.level.name).apply()
        _levels.value = _levels.value + (kind to step.level)
        return step.level
    }

    private fun load(): Map<String, Difficulty> = prefs.all.mapNotNull { (k, v) ->
        if (k.endsWith(STREAK_SUFFIX)) null else (v as? String)?.let { name -> Difficulty.entries.firstOrNull { it.name == name }?.let { k to it } }
    }.toMap()

    private fun streakKey(kind: String) = kind + STREAK_SUFFIX

    private companion object {
        const val STREAK_SUFFIX = ":streak"
    }
}
