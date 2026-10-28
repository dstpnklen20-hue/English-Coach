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
}
