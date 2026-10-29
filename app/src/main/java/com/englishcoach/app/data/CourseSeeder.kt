package com.englishcoach.app.data

import android.content.Context
import androidx.room.withTransaction
import com.englishcoach.app.data.content.CourseContent
import com.englishcoach.app.data.db.AppDatabase
import com.englishcoach.app.data.db.WordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Загружает пачки слов курса в базу: при первом запуске и каждый раз, когда приходит новая версия материалов.
 * Строка слова: "en|транскрипция|перевод|пример|перевод примера".
 */
class CourseSeeder(private val context: Context, private val content: CourseContent, private val db: AppDatabase) {

    @Serializable
    private data class Pack(val level: String, val topics: List<Topic>)

    @Serializable
    private data class Topic(val name: String, val words: List<String>)

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        lock.withLock {
            val prefs = context.getSharedPreferences("seed", Context.MODE_PRIVATE)
            val m = content.manifest
            if (prefs.getInt(KEY_VERSION, 0) >= m.version) return@withLock
            // Повторная загрузка безопасна: uid слова детерминирован, имеющиеся слова пропускаются (IGNORE).
            // Новые карточки идут по порядку пачек: A1 → A2 → B1 → B2
            for (file in m.words) import(parse(content.text(file)))
            prefs.edit().putInt(KEY_VERSION, m.version).apply()
        }
    }

    /** Проверка скачанных пачек теми же классами; бросает исключение, если файл битый. */
    fun validate(files: List<String>, read: (String) -> String) {
        files.forEach { f -> parse(read(f)).topics.flatMap { it.words }.forEach { require(it.split('|').size >= 3) { "$f: $it" } } }
    }

    private fun parse(text: String): Pack = json.decodeFromString(text)

    private suspend fun import(pack: Pack) {
        db.withTransaction {
            for (topic in pack.topics) {
                for (line in topic.words) {
                    val p = line.split('|')
                    // Детерминированный uid: повторный импорт и синхронизация не создают дублей
                    val uid = "course:${pack.level}:${p[0]}"
                    val id = db.wordDao().insert(
                        WordEntity(
                            uid = uid, en = p[0], transcription = p.getOrNull(1), ru = p[2],
                            example = p.getOrNull(3), exampleRu = p.getOrNull(4),
                            topic = topic.name, level = pack.level,
                        ),
                    )
                    if (id > 0) db.cardDao().insertAll(StudyRepository.newCardsFor(id, uid))
                }
            }
        }
    }

    private companion object {
        /** Версия материалов, слова которой уже загружены (раньше — флаг на каждый файл). */
        const val KEY_VERSION = "content_version"
    }
}
