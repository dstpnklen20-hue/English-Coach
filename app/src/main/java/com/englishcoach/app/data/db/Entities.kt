package com.englishcoach.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

// uid — стабильный идентификатор для будущей синхронизации (этап 6); id — локальный ключ Room.

/** Слово или фраза. source: "course" — встроенный курс, "user" — добавлено вручную, "import" — из файла/субтитров. */
@Entity(tableName = "words", indices = [Index("uid", unique = true), Index("level"), Index("topic")])
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val en: String,
    val ru: String,
    val transcription: String? = null,
    val example: String? = null,
    val exampleRu: String? = null,
    val topic: String,
    val level: String,
    val source: String = "course",
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Карточка повторения со состоянием FSRS. Одно слово — несколько карточек по направлениям. */
@Entity(
    tableName = "cards",
    foreignKeys = [ForeignKey(WordEntity::class, ["id"], ["wordId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("uid", unique = true), Index("wordId"), Index("due")],
)
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val wordId: Long,
    val direction: Int, // 0 — EN→RU, 1 — RU→EN, 2 — на слух
    val due: Long,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val state: Int = 0, // 0 новая, 1 изучение, 2 повторение, 3 переучивание
    val lastReview: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Журнал ответов: нужен для статистики и подстройки параметров FSRS. */
@Entity(
    tableName = "review_log",
    foreignKeys = [ForeignKey(CardEntity::class, ["id"], ["cardId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("uid", unique = true), Index("cardId"), Index("reviewedAt")],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val cardId: Long,
    val rating: Int, // 1 снова, 2 трудно, 3 хорошо, 4 легко
    val reviewedAt: Long,
    val durationMs: Long,
)
