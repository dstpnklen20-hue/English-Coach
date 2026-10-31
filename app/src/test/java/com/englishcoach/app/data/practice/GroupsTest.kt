package com.englishcoach.app.data.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class GroupsTest {
    private val learn = GroupItem(ru = "учить", forms = listOf("learn", "learnt/learned", "learnt/learned"), example = "I learned to swim.")
    private val go = GroupItem(ru = "идти", forms = listOf("go", "went", "gone"), example = "We went to the beach.")
    private val verbs = WordGroup("v", "Глаголы", "", "A1", listOf("forms", "gap", "order"), listOf(learn, go, GroupItem(ru = "видеть", forms = listOf("see", "saw", "seen"), example = "I saw it.")))

    @Test
    fun formsAcceptEveryVariant() {
        val ex = Groups.exercise(verbs, learn, "forms", hard = false)
        assertTrue(Answers.matches("learned learnt", ex.accept))
        assertTrue(Answers.matches("learnt, learnt", ex.accept))
        assertFalse(Answers.matches("learn learned", ex.accept))
    }

    @Test
    fun gapTrapsAreNeverAlsoRight() {
        repeat(20) { seed ->
            val ex = Groups.exercise(verbs, learn, "gap", hard = false, random = Random(seed))
            assertEquals("I ___ to swim.", ex.q)
            // learnt — тоже верная форма, её нельзя давать как «неверный» вариант
            assertFalse(ex.options.filterIndexed { i, _ -> i != ex.answer }.any { it == "learnt" || it == "learned" })
        }
        val wentEx = Groups.exercise(verbs, go, "gap", hard = false, random = Random(1))
        assertEquals("went", wentEx.options[wentEx.answer])
        assertTrue(wentEx.options.all { it in listOf("went", "go", "gone", "goed") })
        assertEquals(listOf("learnt", "learned"), Groups.exercise(verbs, learn, "gap", hard = true).accept)
    }

    @Test
    fun regularPastIsTheTypicalMistake() {
        assertEquals("goed", Groups.regularPast("go"))
        assertEquals("buyed", Groups.regularPast("buy"))
        assertEquals("flied", Groups.regularPast("fly"))
        assertEquals("maked", Groups.regularPast("make"))
    }

    @Test
    fun weakWordsComeFirst() {
        val net = mapOf("v:learn" to 3, "v:go" to -1, "v:see" to 2)
        val picked = Groups.pick(verbs, net, count = 1, random = Random(7))
        assertEquals("go", picked.single().key)
        assertEquals(2, Groups.mastered(verbs, net))
    }

    @Test
    fun autoLevelRaisesAfterAGoodStreak() {
        var step = AutoLevel.Step(0, Difficulty.EASY, false)
        repeat(2) { step = AutoLevel.after(step.level, step.streak, 9, 10) }
        assertEquals(Difficulty.EASY, step.level)
        step = AutoLevel.after(step.level, step.streak, 10, 10)
        assertTrue(step.raised)
        assertEquals(Difficulty.NORMAL, step.level)
        // Плохой результат обнуляет серию; на сложном расти некуда
        assertEquals(0, AutoLevel.after(Difficulty.NORMAL, 2, 5, 10).streak)
        assertFalse(AutoLevel.after(Difficulty.HARD, 2, 10, 10).raised)
        assertNull(Difficulty.HARD.next())
    }

    /** Встроенные группы: разбираются, и для каждого слова получается каждое упражнение группы на любом уровне. */
    @Test
    fun bundledGroupsBuildEveryExercise() {
        val groups = Groups.parse(File("src/main/assets/course/${Groups.FILE}").readText())
        assertTrue(groups.size >= 4)
        assertEquals(groups.size, groups.map { it.id }.toSet().size)
        groups.forEach { g ->
            assertEquals(g.id, g.items.size, g.items.map { it.key }.toSet().size)
            g.items.forEach { item ->
                g.modes.forEach { mode ->
                    listOf(false, true).forEach { hard ->
                        val ex = Groups.exercise(g, item, mode, hard, Random(1))
                        if (ex.type == "choice") {
                            assertTrue("${g.id}/${item.key}/$mode: мало вариантов", ex.options.size >= 2)
                            assertEquals("${g.id}/${item.key}: повтор вариантов", ex.options.size, ex.options.toSet().size)
                        } else {
                            assertTrue("${g.id}/${item.key}/$mode: нет ответа", ex.accept.isNotEmpty())
                        }
                    }
                }
            }
        }
    }
}
