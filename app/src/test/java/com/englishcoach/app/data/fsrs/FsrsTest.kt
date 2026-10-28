package com.englishcoach.app.data.fsrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsTest {
    private val fsrs = Fsrs()
    private val t0 = 1_700_000_000_000L

    @Test
    fun retrievabilityAtStabilityIs90Percent() {
        assertEquals(0.9, fsrs.retrievability(10.0, 10.0), 1e-9)
    }

    @Test
    fun newCardGoodGoesToLearningForTenMinutes() {
        val m = fsrs.schedule(Memory(), Rating.Good, t0)
        assertEquals(CardState.LEARNING, m.state)
        assertEquals(t0 + 10 * Fsrs.MINUTE, m.due)
        assertEquals(1, m.reps)
    }

    @Test
    fun learningGoodGraduatesToReviewAtLeastOneDay() {
        val learning = fsrs.schedule(Memory(), Rating.Good, t0)
        val now = t0 + 10 * Fsrs.MINUTE
        val m = fsrs.schedule(learning, Rating.Good, now)
        assertEquals(CardState.REVIEW, m.state)
        assertTrue(m.due - now >= Fsrs.DAY)
    }

    @Test
    fun reviewButtonsAreOrderedAndAgainLapses() {
        var m = fsrs.schedule(Memory(), Rating.Good, t0)
        m = fsrs.schedule(m, Rating.Good, t0 + 10 * Fsrs.MINUTE)
        val now = m.due
        val hard = fsrs.schedule(m, Rating.Hard, now).due
        val good = fsrs.schedule(m, Rating.Good, now).due
        val easy = fsrs.schedule(m, Rating.Easy, now).due
        assertTrue(hard <= good && good < easy)

        val again = fsrs.schedule(m, Rating.Again, now)
        assertEquals(CardState.RELEARNING, again.state)
        assertEquals(1, again.lapses)
        assertTrue(again.stability <= m.stability)
    }

    @Test
    fun successfulReviewsGrowInterval() {
        var m = fsrs.schedule(Memory(), Rating.Good, t0)
        m = fsrs.schedule(m, Rating.Good, t0 + 10 * Fsrs.MINUTE)
        var prev = 0L
        repeat(4) {
            val now = m.due
            m = fsrs.schedule(m, Rating.Good, now)
            val interval = m.due - now
            assertTrue(interval > prev)
            prev = interval
        }
    }
}
