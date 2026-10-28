package com.englishcoach.app.data

import com.englishcoach.app.data.db.DayTotal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakTest {
    private val today = LocalDate.of(2026, 10, 28)
    private fun day(offset: Long, minutes: Long) = DayTotal(today.minusDays(offset).toString(), minutes * 60_000, 10)

    @Test
    fun countsConsecutiveDaysIncludingToday() {
        assertEquals(3, Streak.compute(listOf(day(0, 10), day(1, 6), day(2, 30), day(4, 30)), today))
    }

    @Test
    fun todayNotDoneYetKeepsStreak() {
        assertEquals(2, Streak.compute(listOf(day(0, 2), day(1, 10), day(2, 10)), today))
    }

    @Test
    fun gapBreaksStreak() {
        assertEquals(0, Streak.compute(listOf(day(2, 10), day(3, 10)), today))
    }
}
