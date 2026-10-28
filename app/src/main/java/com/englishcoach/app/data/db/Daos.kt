package com.englishcoach.app.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class CardWithWord(
    @Embedded val card: CardEntity,
    @Relation(parentColumn = "wordId", entityColumn = "id") val word: WordEntity,
)

/** Слово + сводка по его карточкам: maxReps = 0 — ещё не начато; minStability — насколько прочно запомнено. */
data class WordProgress(
    @Embedded val word: WordEntity,
    val maxReps: Int,
    val minStability: Double,
)

@Dao
interface WordDao {
    @Query("SELECT COUNT(*) FROM words")
    fun countFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: WordEntity): Long

    @Query(
        """SELECT w.*, MAX(c.reps) AS maxReps, MIN(c.stability) AS minStability
           FROM words w JOIN cards c ON c.wordId = w.id
           GROUP BY w.id ORDER BY w.id""",
    )
    fun progressFlow(): Flow<List<WordProgress>>

    /** Слова курса уровня, которые ещё ни разу не повторялись — для быстрой проверки «знаю / не знаю». */
    @Query(
        """SELECT w.* FROM words w WHERE w.level = :level AND w.source = 'course'
           AND NOT EXISTS (SELECT 1 FROM cards c WHERE c.wordId = w.id AND c.reps > 0) ORDER BY w.id""",
    )
    suspend fun untouched(level: String): List<WordEntity>

    /** Слова, которые чаще забываются: больше провалов, ниже стабильность. Только уже начатые. */
    @Query(
        """SELECT w.* FROM words w JOIN cards c ON c.wordId = w.id WHERE c.reps > 0
           GROUP BY w.id ORDER BY MAX(c.lapses) DESC, MIN(c.stability) ASC LIMIT :limit""",
    )
    suspend fun weakWords(limit: Int): List<WordEntity>

    @Query("SELECT * FROM words WHERE lower(en) = :en LIMIT 1")
    suspend fun findByEnglish(en: String): WordEntity?

    /** Фразы для аудирования: сначала примеры к уже знакомым словам, остальные — случайно. */
    @Query(
        """SELECT w.* FROM words w WHERE w.example IS NOT NULL
           ORDER BY EXISTS (SELECT 1 FROM cards c WHERE c.wordId = w.id AND c.reps > 0) DESC, RANDOM() LIMIT :limit""",
    )
    suspend fun listeningSentences(limit: Int): List<WordEntity>
}

// Новая карточка RU→EN доступна только после первого показа EN→RU того же слова:
// сначала узнаём слово, потом учимся его вспоминать.
private const val NEW_AVAILABLE = """c.state = 0 AND (c.direction = 0 OR EXISTS (
    SELECT 1 FROM cards c0 WHERE c0.wordId = c.wordId AND c0.direction = 0 AND c0.state != 0))"""

@Dao
interface CardDao {
    @Query("SELECT COUNT(*) FROM cards WHERE state != 0 AND due <= :now")
    fun dueCountFlow(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards c WHERE $NEW_AVAILABLE")
    fun newAvailableCountFlow(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM cards WHERE state != 0 AND due <= :now ORDER BY due LIMIT :limit")
    suspend fun dueCards(now: Long, limit: Int): List<CardWithWord>

    @Transaction
    @Query("SELECT * FROM cards c WHERE $NEW_AVAILABLE ORDER BY c.direction DESC, c.wordId LIMIT :limit")
    suspend fun newCards(limit: Int): List<CardWithWord>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(cards: List<CardEntity>): List<Long>

    @Update
    suspend fun update(card: CardEntity)

    @Query("SELECT * FROM cards WHERE wordId = :wordId")
    suspend fun forWord(wordId: Long): List<CardEntity>
}

@Dao
interface ReviewLogDao {
    @Insert
    suspend fun insert(log: ReviewLogEntity)

    /** Сколько карточек впервые показано начиная с dayStart — для дневного лимита новых. */
    @Query("SELECT COUNT(*) FROM (SELECT MIN(reviewedAt) AS firstAt FROM review_log GROUP BY cardId) WHERE firstAt >= :dayStart")
    fun introducedSinceFlow(dayStart: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM (SELECT MIN(reviewedAt) AS firstAt FROM review_log GROUP BY cardId) WHERE firstAt >= :dayStart")
    suspend fun introducedSince(dayStart: Long): Int

    /**
     * Итоги по дням (местное время): карточки + практика. Паузы не раздувают время:
     * один ответ на карточку — не дольше минуты, одно упражнение практики — не дольше 20 минут.
     */
    @Query(
        """SELECT day, SUM(ms) AS ms, SUM(answers) AS answers FROM (
               SELECT date(reviewedAt / 1000, 'unixepoch', 'localtime') AS day, MIN(durationMs, 60000) AS ms, 1 AS answers
               FROM review_log
               UNION ALL
               SELECT date(at / 1000, 'unixepoch', 'localtime') AS day, MIN(durationMs, 1200000) AS ms, 0 AS answers
               FROM activity_log
           ) GROUP BY day ORDER BY day DESC""",
    )
    fun dayTotalsFlow(): Flow<List<DayTotal>>
}

data class DayTotal(val day: String, val ms: Long, val answers: Int)

/** Лучший результат по уроку/тексту в процентах. */
data class BestScore(val itemId: String, val best: Int)

@Dao
interface ActivityDao {
    @Insert
    suspend fun insert(a: ActivityEntity)

    @Query("SELECT itemId, MAX(correct * 100 / total) AS best FROM activity_log WHERE kind = :kind AND total > 0 GROUP BY itemId")
    fun bestScoresFlow(kind: String): Flow<List<BestScore>>

    @Query("SELECT COUNT(*) FROM activity_log WHERE kind IN (:kinds) AND at >= :since")
    fun countSinceFlow(kinds: List<String>, since: Long): Flow<Int>
}
