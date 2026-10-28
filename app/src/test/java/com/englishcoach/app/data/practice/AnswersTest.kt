package com.englishcoach.app.data.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswersTest {
    @Test
    fun ignoresCasePunctuationAndContractions() {
        assertTrue(Answers.matches("  I DON'T understand!", listOf("I do not understand")))
        assertTrue(Answers.matches("Doesn’t", listOf("doesn't")))
        assertTrue(Answers.matches("can not", listOf("can't")))
        assertFalse(Answers.matches("", listOf("")))
        assertFalse(Answers.matches("is", listOf("are")))
    }

    @Test
    fun wordDiffMarksMissingWords() {
        val diff = Answers.wordDiff("i like game", "I like video games.")
        assertEquals(listOf(true, true, false, false), diff.map { it.second })
    }

    @Test
    fun stemsFindBaseForm() {
        assertTrue("play" in Answers.stems("plays"))
        assertTrue("like" in Answers.stems("liked"))
        assertTrue("city" in Answers.stems("cities"))
        assertTrue("measure" in Answers.stems("measures"))
    }
}
