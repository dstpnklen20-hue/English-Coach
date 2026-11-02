package com.englishcoach.app.data.practice

import com.englishcoach.app.data.db.CardEntity
import com.englishcoach.app.data.db.CardWithWord
import com.englishcoach.app.data.db.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RecapTest {
    private val day = 1_000L

    /** Слово с двумя карточками; у второй по умолчанию — те же показатели, что у первой. */
    private fun word(
        id: Long,
        stability: Double = 5.0,
        lapses: Int = 0,
        reps: Int = 1,
        lastReview: Long? = 10,
        known: Boolean = false,
    ): List<CardWithWord> {
        val w = WordEntity(id = id, en = "w$id", ru = "с$id", topic = "t", level = "A1", known = known)
        return listOf(0, 1).map { dir ->
            CardWithWord(
                CardEntity(id = id * 10 + dir, wordId = id, direction = dir, due = 0, stability = stability, lapses = lapses, reps = reps, lastReview = lastReview),
                w,
            )
        }
    }

    private val cards = word(1, lastReview = 2_000) + // сегодня, учу
        word(2, stability = 30.0, lapses = 2) + // выучено, но забывалось
        word(3, lapses = 5) + // учу, самое трудное
        word(4, stability = 0.0, reps = 0, lastReview = null, known = true) // «знаю» до первого показа

    @Test
    fun countsBySource() {
        val c = Recap.counts(cards, day)
        assertEquals(1, c[Recap.Source.TODAY])
        assertEquals(2, c[Recap.Source.LEARNING])
        assertEquals(2, c[Recap.Source.HARD])
        assertEquals(1, c[Recap.Source.LEARNED])
        assertEquals(1, c[Recap.Source.KNOWN])
        assertEquals(4, c[Recap.Source.ALL])
    }

    @Test
    fun oneCardPerWordAndCountRespected() {
        val all = Recap.pick(cards, Recap.Source.ALL, day, null, Random(1))
        assertEquals(4, all.size)
        assertEquals(4, all.map { it.word.id }.toSet().size)
        assertEquals(2, Recap.pick(cards, Recap.Source.ALL, day, 2, Random(1)).size)
    }

    @Test
    fun knownWordWithoutReviewsStartsFromEnglish() {
        val w = Recap.pick(cards, Recap.Source.KNOWN, day, null, Random(1)).single()
        assertEquals(4L, w.word.id)
        assertEquals(0, w.card.direction)
    }

    @Test
    fun hardestWordsAreTakenFirst() {
        assertEquals(3L, Recap.pick(cards, Recap.Source.HARD, day, 1, Random(2)).single().word.id)
    }

    @Test
    fun forgottenWordReturnsAndCountsOnlyFirstTry() {
        val s = RecapSession(word(1).take(1) + word(2).take(1))
        val first = s.current!!.card.id
        s.answer(remembered = false)
        assertEquals(2, s.remaining)
        s.answer(remembered = true)
        assertEquals(first, s.current!!.card.id)
        s.answer(remembered = true)
        assertNull(s.current)
        assertEquals(1, s.correct)
        assertEquals(2, s.total)
        assertEquals(2, s.answeredCount)
    }

    @Test
    fun droppedWordLeavesTheScore() {
        val s = RecapSession(word(1).take(1) + word(2).take(1))
        s.answer(remembered = true)
        s.dropCurrent()
        assertTrue(s.current == null)
        assertEquals(1, s.total)
        assertEquals(1, s.correct)
    }
}
