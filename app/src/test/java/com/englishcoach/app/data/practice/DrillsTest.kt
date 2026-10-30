package com.englishcoach.app.data.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DrillsTest {
    @Test
    fun clozeSplitsTextAndGaps() {
        val tokens = Cloze.parse("I [work] in an office. We are [hungry|tired], so we eat.")
        val gaps = tokens.filterIsInstance<Cloze.Gap>()
        assertEquals(listOf("work", "hungry"), gaps.map { it.accept.first() })
        assertEquals(",", gaps[1].tail)
        assertEquals("I", (tokens[0] as Cloze.Word).text)
        assertTrue(Cloze.isRight(gaps[1], "Tired"))
        assertFalse(Cloze.isRight(gaps[0], "works"))
        assertFalse(Cloze.isRight(gaps[0], null))
        assertEquals(listOf("work", "hungry", "cold"), Cloze.bank("I [work]. [hungry|tired]!", listOf("cold")))
    }

    @Test
    fun tilesHidePunctuationAndCapitalLetter() {
        val p = Phrase(ru = "Где вокзал?", en = "Where is the station?", extra = listOf("are"))
        assertEquals(listOf("where", "is", "the", "station", "are"), Tiles.of(p))
        assertEquals("I", Tiles.of(Phrase("", "I can swim.")).first())
        assertTrue(Tiles.isRight(p, listOf("where", "is", "the", "station")))
        assertFalse(Tiles.isRight(p, listOf("where", "are", "the", "station")))
        val alt = Phrase("", "I fixed a bug yesterday.", alt = listOf("Yesterday I fixed a bug."))
        assertTrue(Tiles.isRight(alt, listOf("yesterday", "I", "fixed", "a", "bug")))
    }

    @Test
    fun matchScoreNeverNegative() {
        assertEquals(8, Match.score(8, 0))
        assertEquals(5, Match.score(8, 3))
        assertEquals(0, Match.score(8, 12))
    }

    @Test
    fun brokenSetIsRejected() {
        val bad = """{"sets": [{"id": "x", "kind": "cloze", "title": "t", "level": "A1", "text": "no gaps here"}]}"""
        assertTrue(runCatching { DrillFile.parse(bad) }.isFailure)
        val unknown = """{"sets": [{"id": "x", "kind": "quiz", "title": "t", "level": "A1", "items": []}]}"""
        assertTrue(runCatching { DrillFile.parse(unknown) }.isFailure)
    }

    /** Встроенные тренировки: разбираются, у каждой фразы эталон собирается из плиток, пары не повторяются. */
    @Test
    fun bundledDrillsAreConsistent() {
        val dir = File("src/main/assets/course")
        val all = listOf("a1", "a2", "b1", "b2").flatMap { DrillFile.parse(File(dir, "${it}_drills.json").readText()) }
        assertEquals("id наборов не должны повторяться", all.size, all.map { it.id }.toSet().size)
        PracticeKind.DRILLS.forEach { kind -> assertTrue("нет наборов $kind", all.any { it.kind == kind }) }
        all.filter { it.kind == PracticeKind.TRANSLATE }.flatMap { it.phrases }.forEach { p ->
            val tiles = Tiles.of(p)
            assertTrue(p.en, Tiles.isRight(p, tiles.take(tiles.size - p.extra.size)))
        }
        all.filter { it.kind == PracticeKind.MATCH }.forEach { d ->
            assertEquals(d.id, d.pairs.size, d.pairs.map { it.a }.toSet().size)
            assertEquals(d.id, d.pairs.size, d.pairs.map { it.b }.toSet().size)
        }
        all.filter { it.kind == PracticeKind.CLOZE }.forEach { d ->
            val words = Cloze.gaps(d.text)
            d.extra.forEach { assertFalse("${d.id}: лишнее слово $it есть среди пропусков", it in words) }
        }
    }
}
