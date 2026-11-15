package com.englishcoach.app.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicsTest {
    private val all = listOf("Основа", "Главные глаголы", "Строительство", "Путешествия", "Университет", "Общение", "Игры", "Путешествия и переезд", "Стройка и инженерия")

    @Test
    fun modeIsExclusive() {
        var s = Topics.withMode(Settings(), "Игры", TopicMode.FIRST)
        assertEquals(TopicMode.FIRST, Topics.mode(s, "Игры"))
        s = Topics.withMode(s, "Игры", TopicMode.LATER)
        assertEquals(TopicMode.LATER, Topics.mode(s, "Игры"))
        assertTrue("Игры" !in s.firstTopics)
        s = Topics.withMode(s, "Игры", TopicMode.NORMAL)
        assertEquals(Settings(), s)
    }

    @Test
    fun studyPresetPutsBaseAndStudyFirstAndPostponesNarrowTopics() {
        val s = Topics.studyPreset(Settings(), all)
        assertEquals(setOf("Основа", "Главные глаголы", "Университет", "Общение"), s.firstTopics)
        assertEquals(setOf("Строительство", "Путешествия", "Путешествия и переезд", "Стройка и инженерия"), s.laterTopics)
        assertEquals(TopicMode.NORMAL, Topics.mode(s, "Игры"))
    }

    @Test
    fun presetKeepsOtherChoices() {
        val mine = Settings(firstTopics = setOf("Игры"), laterTopics = setOf("Университет"))
        val s = Topics.studyPreset(mine, all)
        assertTrue("Игры" in s.firstTopics)
        // Набор сильнее прежнего выбора по своим темам
        assertTrue("Университет" in s.firstTopics && "Университет" !in s.laterTopics)
    }

    @Test
    fun oldSyncRecordsWithoutTopicsStillRead() {
        val old = """{"newWordsPerDay":10,"sessionMinutes":30,"paletteId":"default","placementDone":true,"level":"A1"}"""
        val r = Json { ignoreUnknownKeys = true }.decodeFromString(SyncedSettings.serializer(), old)
        assertTrue(r.firstTopics.isEmpty() && r.laterTopics.isEmpty())
        // Порядок тем не считается изменением настроек
        assertEquals(
            SyncedSettings.of(Settings(firstTopics = setOf("b", "a"))),
            SyncedSettings.of(Settings(firstTopics = linkedSetOf("a", "b"))),
        )
    }
}
