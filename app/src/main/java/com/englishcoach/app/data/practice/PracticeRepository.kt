package com.englishcoach.app.data.practice

import android.content.Context
import com.englishcoach.app.data.StudyRepository
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
 */
@Serializable
data class Exercise(
    val type: String,
    val q: String = "",
    val options: List<String> = emptyList(),
    val answer: Int = 0,
    val accept: List<String> = emptyList(),
    val ru: String? = null,
)

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
data class Dialog(val id: String, val title: String, val topic: String, val intro: String, val lines: List<DialogLine>)

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

    /** Шаг «Практика» сессии дня — понимание; шаг «Речь» — говорение. */
    val PRACTICE_STEP = listOf(GRAMMAR, READING, LISTENING)
    val SPEECH_STEP = listOf(SPEAKING, DIALOG)
}

/** Материалы практики из assets/course и учёт пройденного. */
class PracticeRepository(private val context: Context, private val db: AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true }
    private var grammar: List<GrammarLesson>? = null
    private var reading: ReadingBank? = null
    private var dialogs: List<Dialog>? = null

    suspend fun grammarLessons(): List<GrammarLesson> =
        grammar ?: json.decodeFromString<GrammarBank>(asset("course/a1_grammar.json")).lessons.also { grammar = it }

    suspend fun readingTexts(): List<ReadingText> = readingBank().texts

    suspend fun dialogs(): List<Dialog> =
        dialogs ?: json.decodeFromString<DialogBank>(asset("course/a1_dialogs.json")).dialogs.also { dialogs = it }

    fun bestScores(kind: String): Flow<List<BestScore>> = db.activityDao().bestScoresFlow(kind)

    /** Сколько упражнений данных видов сделано сегодня. */
    fun doneTodayFlow(kinds: List<String>): Flow<Int> = db.activityDao().countSinceFlow(kinds, StudyRepository.dayStart())

    suspend fun log(kind: String, itemId: String, correct: Int, total: Int, durationMs: Long) =
        db.activityDao().insert(
            ActivityEntity(kind = kind, itemId = itemId, correct = correct, total = total, durationMs = durationMs, at = System.currentTimeMillis()),
        )

    suspend fun listeningSentences(limit: Int): List<WordEntity> = db.wordDao().listeningSentences(limit)

    /** Перевод слова из текста: словарь текста → общий словарь → слова курса (с учётом окончаний). */
    suspend fun translate(word: String, glossary: Map<String, String>): String? {
        val common = readingBank().common
        for (w in Answers.stems(word)) {
            glossary[w]?.let { return it }
            common[w]?.let { return it }
            db.wordDao().findByEnglish(w)?.let { return it.ru }
        }
        return null
    }

    /** Есть ли слово (в любой форме) среди слов курса или своих — тогда добавлять его в карточки не нужно. */
    suspend fun inDictionary(word: String): Boolean = Answers.stems(word).any { db.wordDao().findByEnglish(it) != null }

    private suspend fun readingBank(): ReadingBank =
        reading ?: json.decodeFromString<ReadingBank>(asset("course/a1_reading.json")).also { reading = it }

    private suspend fun asset(path: String): String = withContext(Dispatchers.IO) {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }
}
