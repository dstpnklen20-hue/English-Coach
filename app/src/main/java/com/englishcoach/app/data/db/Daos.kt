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
        """SELECT w.* FROM words w WHERE w.level = :level AND w.source = 'course' AND w.known = 0
           AND NOT EXISTS (SELECT 1 FROM cards c WHERE c.wordId = w.id AND c.reps > 0) ORDER BY w.id""",
    )
    suspend fun untouched(level: String): List<WordEntity>

    /** Слова, которые чаще забываются: больше провалов, ниже стабильность. Только уже начатые. */
    @Query(
        """SELECT w.* FROM words w JOIN cards c ON c.wordId = w.id WHERE c.reps > 0 AND w.known = 0
           GROUP BY w.id ORDER BY MAX(c.lapses) DESC, MIN(c.stability) ASC LIMIT :limit""",
    )
    suspend fun weakWords(limit: Int): List<WordEntity>

    /** Все известные слова (в нижнем регистре) — чтобы не предлагать их при импорте. */
    @Query("SELECT lower(en) FROM words")
    suspend fun allEnglish(): List<String>

    // --- синхронизация ---
    @Query("SELECT * FROM words WHERE uid = :uid LIMIT 1")
    suspend fun byUid(uid: String): WordEntity?

    /** Свои и импортированные слова, изменённые с момента since (слова курса одинаковы везде — их не шлём). */
    @Query("SELECT * FROM words WHERE source != 'course' AND updatedAt >= :since")
    suspend fun ownChangedSince(since: Long): List<WordEntity>

    @Update
    suspend fun update(word: WordEntity)

    /** Поставить или снять «знаю»; updatedAt не трогаем — отметка синхронизируется отдельной записью. */
    @Query("UPDATE words SET known = :known, knownAt = :at WHERE id = :id")
    suspend fun setKnown(id: Long, known: Boolean, at: Long)

    @Query("SELECT * FROM words WHERE knownAt > 0 AND knownAt >= :since")
    suspend fun knownChangedSince(since: Long): List<WordEntity>

    @Query("SELECT * FROM words WHERE lower(en) = :en LIMIT 1")
    suspend fun findByEnglish(en: String): WordEntity?

    /** Фразы для аудирования: сначала примеры к уже знакомым словам, остальные — случайно. */
    @Query(
        """SELECT w.* FROM words w WHERE w.example IS NOT NULL
           ORDER BY EXISTS (SELECT 1 FROM cards c WHERE c.wordId = w.id AND c.reps > 0) DESC, RANDOM() LIMIT :limit""",
    )
    suspend fun listeningSentences(limit: Int): List<WordEntity>
}

/** Слово не отмечено «знаю» — только такие карточки попадают в повторение и в новые. */
private const val NOT_KNOWN = "c.wordId NOT IN (SELECT id FROM words WHERE known = 1)"

// Новая карточка RU→EN доступна только после первого показа EN→RU того же слова:
// сначала узнаём слово, потом учимся его вспоминать.
private const val NEW_AVAILABLE = """c.state = 0 AND $NOT_KNOWN AND (c.direction = 0 OR EXISTS (
    SELECT 1 FROM cards c0 WHERE c0.wordId = c.wordId AND c0.direction = 0 AND c0.state != 0))"""

@Dao
interface CardDao {
    @Query("SELECT COUNT(*) FROM cards c WHERE c.state != 0 AND c.due <= :now AND $NOT_KNOWN")
    fun dueCountFlow(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards c WHERE $NEW_AVAILABLE")
    fun newAvailableCountFlow(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM cards c WHERE c.state != 0 AND c.due <= :now AND $NOT_KNOWN ORDER BY c.due LIMIT :limit")
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

    /** Сколько слов в запасе: начатые (хотя бы одна карточка повторена) и отмеченные «знаю» — для цели. */
    @Query("SELECT COUNT(*) FROM words w WHERE w.known = 1 OR EXISTS (SELECT 1 FROM cards c WHERE c.wordId = w.id AND c.reps > 0)")
    fun startedWordsFlow(): Flow<Int>

    // --- синхронизация ---
    @Query("SELECT * FROM cards WHERE uid = :uid LIMIT 1")
    suspend fun byUid(uid: String): CardEntity?

    /** Карточки с прогрессом, изменённые с since. Нетронутые одинаковы на всех устройствах — их не шлём. */
    @Transaction
    @Query("SELECT * FROM cards WHERE reps > 0 AND updatedAt >= :since")
    suspend fun touchedSince(since: Long): List<CardWithWord>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(card: CardEntity): Long
}

/** Ответ из журнала + uid карточки (на другом устройстве у неё другой локальный id). */
data class ReviewWithCard(@Embedded val log: ReviewLogEntity, val cardUid: String)

@Dao
interface ReviewLogDao {
    @Insert
    suspend fun insert(log: ReviewLogEntity)

    /** Для синхронизации: запись с таким uid уже есть — пропускаем. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(log: ReviewLogEntity): Long

    @Query("SELECT r.*, c.uid AS cardUid FROM review_log r JOIN cards c ON c.id = r.cardId WHERE r.reviewedAt >= :since")
    suspend fun since(since: Long): List<ReviewWithCard>

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(a: ActivityEntity): Long

    @Query("SELECT * FROM activity_log WHERE at >= :since")
    suspend fun since(since: Long): List<ActivityEntity>

    @Query("SELECT itemId, MAX(correct * 100 / total) AS best FROM activity_log WHERE kind = :kind AND total > 0 GROUP BY itemId")
    fun bestScoresFlow(kind: String): Flow<List<BestScore>>

    @Query("SELECT itemId, MAX(correct * 100 / total) AS best FROM activity_log WHERE kind IN (:kinds) AND total > 0 GROUP BY itemId")
    fun bestScoresFlow(kinds: List<String>): Flow<List<BestScore>>

    @Query("SELECT COUNT(*) FROM activity_log WHERE kind IN (:kinds) AND at >= :since")
    fun countSinceFlow(kinds: List<String>, since: Long): Flow<Int>
}
