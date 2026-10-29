package com.englishcoach.app.data

import android.content.Context
import androidx.room.withTransaction
import com.englishcoach.app.data.db.AppDatabase
import com.englishcoach.app.data.db.WordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Загружает встроенные пачки слов из assets/course в базу (один раз на пачку).
 * Строка слова: "en|транскрипция|перевод|пример|перевод примера".
 */
class CourseSeeder(private val context: Context, private val db: AppDatabase) {

    @Serializable
    private data class Pack(val level: String, val topics: List<Topic>)

    @Serializable
    private data class Topic(val name: String, val words: List<String>)

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("seed", Context.MODE_PRIVATE)
        for (file in PACKS) {
            if (prefs.getBoolean(file, false)) continue
            import(file)
            prefs.edit().putBoolean(file, true).apply()
        }
    }

    private suspend fun import(file: String) {
        val pack = json.decodeFromString<Pack>(context.assets.open(file).bufferedReader().use { it.readText() })
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

    companion object {
        // Новые карточки идут по порядку добавления: A1 → A2 → B1
        private val PACKS = listOf("course/a1_words.json", "course/a2_words.json", "course/b1_words.json")
    }
}
