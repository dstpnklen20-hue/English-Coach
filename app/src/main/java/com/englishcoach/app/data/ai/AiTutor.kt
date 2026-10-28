package com.englishcoach.app.data.ai

import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageParam
import com.englishcoach.app.data.SettingsRepository
import com.englishcoach.app.data.ai.AiRepository.Companion.arr
import com.englishcoach.app.data.ai.AiRepository.Companion.int
import com.englishcoach.app.data.ai.AiRepository.Companion.obj
import com.englishcoach.app.data.ai.AiRepository.Companion.str
import com.englishcoach.app.data.db.WordEntity
import com.englishcoach.app.data.practice.Exercise
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class NewWord(val en: String, val ru: String)

@Serializable
data class ChatReply(
    val reply: String,
    @SerialName("reply_ru") val replyRu: String,
    val correction: String,
    @SerialName("explanation_ru") val explanationRu: String,
    @SerialName("new_words") val newWords: List<NewWord>,
)

@Serializable
data class Mistake(val original: String, val fixed: String, @SerialName("explanation_ru") val explanationRu: String)

@Serializable
data class TextCheck(val corrected: String, val mistakes: List<Mistake>, @SerialName("comment_ru") val commentRu: String)

@Serializable
private data class ExerciseSet(val exercises: List<Exercise>)

/** Учебные сценарии поверх Claude: собеседник, проверка текста, упражнения по слабым словам. */
class AiTutor(private val ai: AiRepository, private val settings: SettingsRepository) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Ответ собеседника. history — вся переписка (append-only); вернёт разбор и сообщение для истории. */
    suspend fun chat(topic: String, history: List<MessageParam>): Pair<ChatReply, Message> {
        val level = settings.current().level
        val system = """
            You are a friendly English conversation partner for a Russian-speaking learner at CEFR level $level.
            Topic of the conversation: $topic.
            Reply in 1-3 short sentences of simple English that fits the level (${levelGuide(level)}), and end with one simple question to keep the conversation going.
            reply_ru: a natural Russian translation of your reply.
            Check the learner's last message. If it has mistakes, put a corrected, natural version in correction and a short plain-Russian explanation (1-2 sentences) in explanation_ru. If it is correct, leave both empty.
            If the learner writes in Russian, help: show in correction how to say it in English, and answer in English.
            If the learner's message is exactly START, greet the learner and ask the first question about the topic; leave correction empty.
            new_words: up to 3 words or phrases from your reply that may be new at this level, with Russian translations.
        """.trimIndent()
        val schema = obj(
            "reply" to str(),
            "reply_ru" to str(),
            "correction" to str(),
            "explanation_ru" to str(),
            "new_words" to arr(obj("en" to str(), "ru" to str())),
        )
        val msg = ai.request(system, history, schema)
        return json.decodeFromString<ChatReply>(AiRepository.textOf(msg)) to msg
    }

    suspend fun checkText(text: String): TextCheck {
        val level = settings.current().level
        val system = """
            You check short texts written by a Russian-speaking English learner at CEFR level $level.
            corrected: the text with the mistakes fixed. Keep the learner's meaning and simple style; do not rewrite it into advanced English.
            mistakes: every mistake with the original fragment, the fixed fragment and explanation_ru — a short, plain Russian explanation of the rule.
            comment_ru: 1-2 encouraging sentences in Russian about the main thing to work on.
            If there are no mistakes, return the text unchanged and an empty mistakes list.
        """.trimIndent()
        val schema = obj(
            "corrected" to str(),
            "mistakes" to arr(obj("original" to str(), "fixed" to str(), "explanation_ru" to str())),
            "comment_ru" to str(),
        )
        val msg = ai.request(system, listOf(AiRepository.userMessage(text)), schema)
        return json.decodeFromString(AiRepository.textOf(msg))
    }

    /** Упражнения на слова, которые чаще всего забываются. Формат — как у встроенных уроков. */
    suspend fun exercises(words: List<WordEntity>, count: Int = 6): List<Exercise> {
        val level = settings.current().level
        val list = words.joinToString("; ") { "${it.en} — ${it.ru}" }
        val system = """
            You create short exercises for a Russian-speaking English learner at CEFR level $level (${levelGuide(level)}).
            Topics the learner likes: technology, building, games, travel, sport.
            Exercise types:
            - choice: q is a sentence with a ___ gap, options has 3 choices, answer is the index of the correct option, accept is empty.
            - input: q is a sentence with a ___ gap, accept lists every correct answer, options is empty, answer is 0.
            - order: accept has one correct sentence of 4-7 words without final punctuation, q and options are empty, answer is 0.
            ru: the Russian translation of the full correct sentence.
            hint: a short plain-Russian tip about the rule, shown when the learner makes a mistake; do not reveal the answer.
            why: for choice, one short Russian explanation per option of why it is wrong (empty string for the correct option); for other types, an empty list.
            Mix the three types.
        """.trimIndent()
        val schema = obj(
            "exercises" to arr(
                obj(
                    "type" to mapOf("type" to "string", "enum" to listOf("choice", "input", "order")),
                    "q" to str(),
                    "options" to arr(str()),
                    "answer" to int(),
                    "accept" to arr(str()),
                    "ru" to str(),
                    "hint" to str(),
                    "why" to arr(str()),
                ),
            ),
        )
        val prompt = "Make $count exercises that practise these words the learner keeps forgetting: $list"
        val msg = ai.request(system, listOf(AiRepository.userMessage(prompt)), schema)
        return json.decodeFromString<ExerciseSet>(AiRepository.textOf(msg)).exercises
            .filter { valid(it) }
    }

    /** Отсекаем упражнения, которые нельзя показать (на случай редких промахов модели). */
    private fun valid(e: Exercise) = when (e.type) {
        "choice" -> e.options.size >= 2 && e.answer in e.options.indices
        "input" -> e.accept.isNotEmpty() && "___" in e.q
        "order" -> e.accept.firstOrNull()?.split(" ")?.size in 2..10
        else -> false
    }

    private fun levelGuide(level: String) = when (level) {
        "A1" -> "only the most common words, present simple, very short sentences"
        "A2" -> "common everyday words, present and past simple, short sentences"
        "B1" -> "everyday vocabulary, main tenses, sentences of medium length"
        else -> "natural everyday English, a wider vocabulary, varied grammar"
    }
}
