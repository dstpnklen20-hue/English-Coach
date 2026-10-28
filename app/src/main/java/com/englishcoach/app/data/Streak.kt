package com.englishcoach.app.data

import com.englishcoach.app.data.db.DayTotal
import java.time.LocalDate

/** Серия дней подряд, в которые занимались хотя бы MIN_MS. Сегодня ещё не засчитан — серия не обрывается. */
object Streak {
    const val MIN_MS = 5 * 60_000L

    fun compute(days: List<DayTotal>, today: LocalDate): Int {
        val active = days.filter { it.ms >= MIN_MS }.map { LocalDate.parse(it.day) }.toSet()
        var d = if (today in active) today else today.minusDays(1)
        var n = 0
        while (d in active) {
            n++
            d = d.minusDays(1)
        }
        return n
    }
}
