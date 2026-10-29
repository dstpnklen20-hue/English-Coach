package com.englishcoach.app.data.progress

import com.englishcoach.app.data.db.DayTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressTest {
    private val start = LocalDate.of(2026, 11, 1)
    private val goal = Goal("A2", target = 1000, deadline = start.plusDays(100), startDay = start, startWords = 0)

    @Test
    fun onTrackWhenAheadOfPlanAndPaceIsEnough() {
        // Через 10 дней по плану 100 слов; у нас 120, темп 10/день = 70/нед, нужно ~69/нед
        val s = GoalPlan.status(goal, started = 120, newWordsPerDay = 10, today = start.plusDays(10))
        assertEquals(100, s.expectedNow)
        assertTrue(s.onTrack)
        assertNull(s.suggestPerDay)
    }

    @Test
    fun behindSuggestsHigherPace() {
        val s = GoalPlan.status(goal, started = 50, newWordsPerDay = 5, today = start.plusDays(30))
        assertFalse(s.onTrack)
        assertEquals(14, s.suggestPerDay) // осталось 950 слов на 70 дней → 14 в день
        assertNull(s.realisticDate)
    }

    @Test
    fun impossibleDeadlineGivesRealisticDate() {
        val s = GoalPlan.status(goal, started = 0, newWordsPerDay = 10, today = start.plusDays(90))
        assertEquals(GoalPlan.MAX_PER_DAY, s.suggestPerDay)
        assertNotNull(s.realisticDate) // 1000 слов за 10 дней не успеть
    }

    @Test
    fun bestStreakFindsLongestRun() {
        val d = { day: String, min: Long -> DayTotal(day, min * 60_000, 1) }
        val days = listOf(d("2026-10-01", 10), d("2026-10-02", 10), d("2026-10-03", 10), d("2026-10-05", 10), d("2026-10-06", 1))
        assertEquals(3, bestStreak(days))
    }
}
