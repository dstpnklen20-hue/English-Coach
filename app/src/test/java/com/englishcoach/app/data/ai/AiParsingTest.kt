package com.englishcoach.app.data.ai

import com.englishcoach.app.data.practice.Exercise
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ответы модели приходят JSON по схеме — проверяем, что имена полей совпадают с моделями приложения. */
class AiParsingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesChatReply() {
        val r = json.decodeFromString<ChatReply>(
            """{"reply":"Nice! What game do you play?","reply_ru":"Здорово! В какую игру ты играешь?",
               "correction":"I like games.","explanation_ru":"После like нужен -s во множественном числе.",
               "new_words":[{"en":"nice","ru":"здорово"}]}""",
        )
        assertEquals("I like games.", r.correction)
        assertEquals("nice", r.newWords.single().en)
    }

    @Test
    fun parsesJsonWrappedLikeChatAnswer() {
        // Так отвечает модель, если посредник не включил строгий JSON-режим
        val raw = "Sure! Here is my answer:\n```json\n{\"reply\":\"I'm fine, thanks!\",\"reply_ru\":\"Хорошо, спасибо!\"}\n```"
        val r = AiRepository.decode<ChatReply>(raw)
        assertEquals("I'm fine, thanks!", r.reply)
        assertEquals("", r.correction) // нет поля — пустая строка, а не ошибка
        assertEquals(0, r.newWords.size)
    }

    @Test
    fun parsesTextCheck() {
        val r = json.decodeFromString<TextCheck>(
            """{"corrected":"Yesterday I went to work.","mistakes":[{"original":"go","fixed":"went","explanation_ru":"Прошедшее время."}],
               "comment_ru":"Хорошо!"}""",
        )
        assertEquals("went", r.mistakes.single().fixed)
    }

    @Test
    fun parsesExerciseInSameFormatAsCourse() {
        val e = json.decodeFromString<Exercise>("""{"type":"choice","q":"I ___ a phone.","options":["have","has","having"],"answer":0,"accept":[],"ru":"У меня есть телефон."}""")
        assertEquals("have", e.options[e.answer])
    }
}
