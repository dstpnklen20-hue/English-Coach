package com.englishcoach.app.data.practice

import com.englishcoach.app.data.StudyRepository
import com.englishcoach.app.data.content.CourseContent
import com.englishcoach.app.data.db.ActivityEntity
import com.englishcoach.app.data.db.AppDatabase
import com.englishcoach.app.data.db.BestScore
import com.englishcoach.app.data.db.WordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Упражнение. type: "choice" — выбрать вариант (options, answer — индекс);
 * "input" — вписать слово вместо ___ (accept — допустимые ответы);
 * "order" — собрать фразу из слов (accept[0] — эталон, слова перемешиваются сами).
 * Подсказки при ошибке: why[i] — почему вариант options[i] неверен (для choice), hint — правило в целом.
 */
@Serializable
data class Exercise(
    val type: String,
    val q: String = "",
    val options: List<String> = emptyList(),
    val answer: Int = 0,
    val accept: List<String> = emptyList(),
    val ru: String? = null,
    val hint: String? = null,
    val why: List<String> = emptyList(),
) {
    /** Подсказка к конкретной ошибке: объяснение выбранного варианта, иначе общее правило. */
    fun hintFor(option: Int?): String? =
        option?.let { why.getOrNull(it) }?.takeIf { it.isNotBlank() } ?: hint?.takeIf { it.isNotBlank() }
}

@Serializable
data class GrammarLesson(
    val id: String,
    val title: String,
    val level: String,
    val theory: List<String>,
    val examples: List<List<String>>, // [английский, перевод]
    val exercises: List<Exercise>,
)

@Serializable
data class ReadingText(
    val id: String,
    val title: String,
    val topic: String,
    val level: String,
    val text: String,
    val glossary: Map<String, String> = emptyMap(),
    val questions: List<Exercise>,
)

/** Реплика диалога. who: "them" — собеседник (en, ru); "me" — ваш ответ (hint, options[0] — верный, ru). */
@Serializable
data class DialogLine(
    val who: String,
    val en: String = "",
    val ru: String = "",
    val hint: String = "",
    val options: List<String> = emptyList(),
)

@Serializable
data class Dialog(val id: String, val title: String, val topic: String, val intro: String, val lines: List<DialogLine>, val level: String = "A1")

@Serializable
private data class DialogBank(val dialogs: List<Dialog>)

@Serializable
private data class GrammarBank(val lessons: List<GrammarLesson>)

@Serializable
private data class ReadingBank(val common: Map<String, String>, val texts: List<ReadingText>)

object PracticeKind {
    const val GRAMMAR = "grammar"
    const val READING = "reading"
    const val LISTENING = "listening"
    const val SPEAKING = "speaking"
    const val DIALOG = "dialog"
    const val AI_CHAT = "ai_chat"
    const val AI_WRITING = "ai_writing"
    const val AI_EXERCISES = "ai_exercises"

    /** Шаг «Практика» сессии дня — понимание и письмо; шаг «Речь» — говорение и общение. */
    val PRACTICE_STEP = listOf(GRAMMAR, READING, LISTENING, AI_WRITING, AI_EXERCISES)
    val SPEECH_STEP = listOf(SPEAKING, DIALOG, AI_CHAT)
}

/** Материалы практики (встроенные или скачанные — см. [CourseContent]) и учёт пройденного. */
class PracticeRepository(private val content: CourseContent, private val db: AppDatabase) {
    private class Bank(val grammar: List<GrammarLesson>, val reading: ReadingBank, val dialogs: List<Dialog>)

    // Заполняется из фонового потока (предзагрузка при старте), читается с главного
    @Volatile private var bank: Bank? = null

    // Файлы курса читаются и разбираются один раз и в фоне: разбор JSON на главном потоке подтормаживал экраны
    private suspend fun bank(): Bank = bank ?: withContext(Dispatchers.IO) { parse(content.manifest.levels, content::text) }.also { bank = it }

    suspend fun grammarLessons(): List<GrammarLesson> = bank().grammar

    suspend fun readingTexts(): List<ReadingText> = bank().reading.texts

    suspend fun dialogs(): List<Dialog> = bank().dialogs

    /** Уже загруженные материалы (или пусто) — без ожидания, для первого кадра экрана. */
    fun cachedGrammar(): List<GrammarLesson> = bank?.grammar.orEmpty()
    fun cachedTexts(): List<ReadingText> = bank?.reading?.texts.orEmpty()
    fun cachedDialogs(): List<Dialog> = bank?.dialogs.orEmpty()

    /** Проверка скачанного набора теми же классами, что при чтении; бросает исключение, если файл битый. */
    fun validate(levels: List<String>, read: (String) -> String) {
        parse(levels, read)
    }

    /** Материалы обновились — при следующем обращении перечитать. */
    fun invalidate() {
        bank = null
    }

    private fun parse(levels: List<String>, read: (String) -> String) = Bank(
        grammar = levels.flatMap { json.decodeFromString<GrammarBank>(read("${it}_grammar.json")).lessons },
        // Тексты всех уровней и их общие словари — одним банком
        reading = levels.map { json.decodeFromString<ReadingBank>(read("${it}_reading.json")) }
            .reduce { a, b -> ReadingBank(a.common + b.common, a.texts + b.texts) },
        dialogs = levels.flatMap { json.decodeFromString<DialogBank>(read("${it}_dialogs.json")).dialogs },
    )

    fun bestScores(kind: String): Flow<List<BestScore>> = db.activityDao().bestScoresFlow(kind)

    /** Сколько упражнений данных видов сделано сегодня. */
    fun doneTodayFlow(kinds: List<String>): Flow<Int> = db.activityDao().countSinceFlow(kinds, StudyRepository.dayStart())

    suspend fun log(kind: String, itemId: String, correct: Int, total: Int, durationMs: Long) =
        db.activityDao().insert(
            ActivityEntity(kind = kind, itemId = itemId, correct = correct, total = total, durationMs = durationMs, at = System.currentTimeMillis()),
        )

    suspend fun listeningSentences(limit: Int): List<WordEntity> = db.wordDao().listeningSentences(limit)

    suspend fun weakWords(limit: Int): List<WordEntity> = db.wordDao().weakWords(limit)

    /** Перевод слова из текста: словарь текста → общий словарь → слова курса (с учётом окончаний). */
    suspend fun translate(word: String, glossary: Map<String, String>): String? {
        val common = bank().reading.common
        for (w in Answers.stems(word)) {
            glossary[w]?.let { return it }
            common[w]?.let { return it }
            db.wordDao().findByEnglish(w)?.let { return it.ru }
        }
        return null
    }

    /** Есть ли слово (в любой форме) среди слов курса или своих — тогда добавлять его в карточки не нужно. */
    suspend fun inDictionary(word: String): Boolean = Answers.stems(word).any { db.wordDao().findByEnglish(it) != null }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
