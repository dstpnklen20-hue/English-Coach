package com.englishcoach.app.data.progress

import com.englishcoach.app.data.Streak
import com.englishcoach.app.data.db.DayTotal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max

/** Примерный словарный запас уровня (общепринятые грубые оценки) — подсказка при выборе цели. */
val LEVEL_WORDS = linkedMapOf("A2" to 1000, "B1" to 2000, "B2" to 3500)

/**
 * Цель: target слов к deadline. Начало — startWords слов на дату startDay (когда цель поставлена).
 * Счёт ведётся по начатым словам (хотя бы раз повторены) — это то, что зависит от темпа занятий.
 */
data class Goal(val level: String, val target: Int, val deadline: LocalDate, val startDay: LocalDate, val startWords: Int)

/** Цель из настроек (null — не поставлена). */
fun com.englishcoach.app.data.Settings.goal(): Goal? =
    if (goalDeadline <= 0 || goalWords <= 0) null
    else Goal(goalLevel, goalWords, LocalDate.ofEpochDay(goalDeadline), LocalDate.ofEpochDay(goalStartDay), goalStartWords)

data class GoalStatus(
    val daysLeft: Long,
    /** Сколько слов по плану должно быть к сегодняшнему дню (равномерно от начала до срока). */
    val expectedNow: Int,
    /** Сколько новых слов в неделю нужно, чтобы успеть. */
    val needPerWeek: Int,
    /** Сколько слов в неделю даёт текущая настройка «новых слов в день». */
    val plannedPerWeek: Int,
    val onTrack: Boolean,
    /** Сколько новых слов в день поставить, чтобы успеть (null — уже успеваете). */
    val suggestPerDay: Int?,
    /** Если даже 25 слов в день не хватит — реальная дата при этом темпе. */
    val realisticDate: LocalDate?,
    val reached: Boolean,
)

object GoalPlan {
    const val MAX_PER_DAY = 25

    fun status(goal: Goal, started: Int, newWordsPerDay: Int, today: LocalDate): GoalStatus {
        val left = (goal.target - started).coerceAtLeast(0)
        val daysLeft = ChronoUnit.DAYS.between(today, goal.deadline).coerceAtLeast(0)
        val total = ChronoUnit.DAYS.between(goal.startDay, goal.deadline).coerceAtLeast(1)
        val passed = ChronoUnit.DAYS.between(goal.startDay, today).coerceIn(0, total)
        val expected = goal.startWords + ((goal.target - goal.startWords) * passed / total).toInt()
        val weeksLeft = max(daysLeft, 1) / 7.0
        val needPerWeek = ceil(left / max(weeksLeft, 1.0 / 7)).toInt()
        val planned = newWordsPerDay * 7
        val needPerDay = ceil(left / max(daysLeft, 1).toDouble()).toInt()
        val reached = left == 0
        val onTrack = reached || (started >= expected && planned >= needPerWeek)
        return GoalStatus(
            daysLeft = daysLeft,
            expectedNow = expected,
            needPerWeek = needPerWeek,
            plannedPerWeek = planned,
            onTrack = onTrack,
            suggestPerDay = if (onTrack) null else needPerDay.coerceIn(3, MAX_PER_DAY),
            realisticDate = if (!reached && needPerDay > MAX_PER_DAY) today.plusDays(ceil(left / MAX_PER_DAY.toDouble()).toLong()) else null,
            reached = reached,
        )
    }
}

/** Лучшая серия дней подряд (с занятиями не меньше Streak.MIN_MS). */
fun bestStreak(days: List<DayTotal>): Int {
    val active = days.filter { it.ms >= Streak.MIN_MS }.map { LocalDate.parse(it.day) }.sorted()
    var best = 0
    var run = 0
    var prev: LocalDate? = null
    for (d in active) {
        run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
        best = max(best, run)
        prev = d
    }
    return best
}

/** Сводка для достижений. */
data class Stats(
    val answers: Int,
    val totalMinutes: Int,
    val streak: Int,
    val bestStreak: Int,
    val knownWords: Int,
    val startedWords: Int,
    val grammarPassed: Int,
    val grammarTotal: Int,
    val dialogsPassed: Int,
    val aiChats: Int,
    val importedWords: Int,
    val drillsPassed: Int = 0,
)

data class Achievement(val title: String, val description: String, val unlocked: Boolean)

object Achievements {
    fun compute(s: Stats): List<Achievement> = listOf(
        Achievement("Первый шаг", "Ответить на первую карточку", s.answers > 0),
        Achievement("Неделя без пропусков", "Серия 7 дней", s.bestStreak >= 7),
        Achievement("Месяц привычки", "Серия 30 дней", s.bestStreak >= 30),
        Achievement("100 слов в работе", "Начать учить 100 слов", s.startedWords >= 100),
        Achievement("Первые 50 выученных", "50 слов держатся в памяти 3+ недели", s.knownWords >= 50),
        Achievement("Грамматика A1", "Пройти все уроки A1 на 70%+", s.grammarTotal > 0 && s.grammarPassed >= s.grammarTotal),
        Achievement("Разговорился", "Пройти диалог без большого числа ошибок", s.dialogsPassed > 0),
        Achievement("Живой собеседник", "Поговорить с ИИ", s.aiChats > 0),
        Achievement("Свой словарь", "Импортировать 20 слов", s.importedWords >= 20),
        Achievement("Набил руку", "Пройти 10 тренировок на 70%+", s.drillsPassed >= 10),
        Achievement("10 часов английского", "Заниматься в сумме 10 часов", s.totalMinutes >= 600),
    )
}
