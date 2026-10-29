package com.englishcoach.app.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImporterTest {
    @Test
    fun parsesPairsWithDifferentSeparators() {
        val text = "castle — замок\nsword - меч\nshield;щит\narmor\tброня\n#deck:Games\n"
        val pairs = Importer.parsePairs(text)
        assertEquals(listOf("castle", "sword", "shield", "armor"), pairs.map { it.en })
        assertEquals("броня", pairs.last().ru)
        assertTrue(Importer.looksLikePairs(text))
    }

    @Test
    fun plainTextIsNotPairs() {
        assertFalse(Importer.looksLikePairs("The knight entered the castle.\nHe was tired after the long road."))
    }

    @Test
    fun subtitlesGiveUnknownWordsWithoutTimingsNamesAndKnownWords() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,000
            <i>Arthur, the castle is under siege!</i>

            2
            00:00:03,500 --> 00:00:05,000
            We need more soldiers to defend the castle.
        """.trimIndent()
        val words = Importer.extractWords(srt, known = setOf("need"))
        val list = words.map { it.word }
        assertEquals("castle", list.first()) // встречается дважды — первым
        assertTrue("siege" in list && "soldiers" in list && "defend" in list)
        assertFalse("arthur" in list) // имя: ни разу со строчной
        assertFalse("need" in list) // уже известно
        assertFalse(list.any { it.contains(":") || it.all(Char::isDigit) })
        assertEquals("Arthur, the castle is under siege!", words.first().context)
    }

    @Test
    fun knownWordFormsAreSkipped() {
        assertFalse("plays" in Importer.extractWords("He plays chess and builds houses.", known = setOf("play")).map { it.word })
    }
}
