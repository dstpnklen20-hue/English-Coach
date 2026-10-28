package com.englishcoach.app.data

import androidx.room.withTransaction
import com.englishcoach.app.data.db.AppDatabase
import com.englishcoach.app.data.db.CardEntity
import com.englishcoach.app.data.db.CardWithWord
import com.englishcoach.app.data.db.DayTotal
import com.englishcoach.app.data.db.ReviewLogEntity
import com.englishcoach.app.data.db.WordEntity
import com.englishcoach.app.data.fsrs.Fsrs
import com.englishcoach.app.data.fsrs.Memory
import com.englishcoach.app.data.fsrs.Rating
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/** Всё, что касается учёбы слов: очередь карточек, сохранение ответов, дневной лимит новых, итоги дня. */
class StudyRepository(private val db: AppDatabase, val fsrs: Fsrs, private val settings: SettingsRepository) {

    fun dueCountFlow(now: Long): Flow<Int> = db.cardDao().dueCountFlow(now)

    /** Сколько новых карточек ещё можно показать сегодня (каждое слово — две карточки). */
    fun newTodayCountFlow(): Flow<Int> = combine(
        db.cardDao().newAvailableCountFlow(),
        db.reviewLogDao().introducedSinceFlow(dayStart()),
        settings.flow,
    ) { available, introduced, s -> minOf(available, (s.newWordsPerDay * 2 - introduced).coerceAtLeast(0)) }

    fun dayTotalsFlow(): Flow<List<DayTotal>> = db.reviewLogDao().dayTotalsFlow()

    suspend fun todayStudyMs(): Long =
        dayTotalsFlow().first().firstOrNull { it.day == LocalDate.now().toString() }?.ms ?: 0

    /** Очередь на сессию: сначала карточки к повторению, затем новые в пределах лимита. */
    suspend fun loadQueue(now: Long): List<CardWithWord> {
        val due = db.cardDao().dueCards(now, MAX_REVIEWS)
        val limit = settings.current().newWordsPerDay * 2
        val newLimit = (limit - db.reviewLogDao().introducedSince(dayStart())).coerceAtLeast(0)
        return due + db.cardDao().newCards(newLimit)
    }

    /** Пересчитывает карточку по FSRS, сохраняет её и запись в журнал. Возвращает обновлённую карточку. */
    suspend fun answer(card: CardEntity, rating: Rating, now: Long, durationMs: Long): CardEntity {
        val updated = card.withMemory(fsrs.schedule(card.toMemory(), rating, now), now)
        db.withTransaction {
            db.cardDao().update(updated)
            db.reviewLogDao().insert(ReviewLogEntity(cardId = card.id, rating = rating.value, reviewedAt = now, durationMs = durationMs))
        }
        return updated
    }

    suspend fun wordsForCheck(level: String): List<WordEntity> = db.wordDao().untouched(level)

    /**
     * «Знаю это слово» из стартовой проверки: обе карточки сразу уходят на повторение через ~2 недели.
     * В журнал не пишется — иначе съест дневной лимит новых.
     */
    suspend fun markKnown(wordId: Long, now: Long) {
        val m = fsrs.schedule(Memory(), Rating.Easy, now)
        db.withTransaction {
            db.cardDao().forWord(wordId).forEach { db.cardDao().update(it.withMemory(m, now)) }
        }
    }

    suspend fun addUserWord(en: String, ru: String) {
        db.withTransaction {
            val word = WordEntity(en = en.trim(), ru = ru.trim(), topic = USER_TOPIC, level = "", source = "user")
            val id = db.wordDao().insert(word)
            if (id > 0) db.cardDao().insertAll(newCardsFor(id, word.uid))
        }
    }

    companion object {
        private const val MAX_REVIEWS = 500
        const val USER_TOPIC = "Мои слова"

        fun newCardsFor(wordId: Long, wordUid: String) = listOf(0, 1).map { dir ->
            CardEntity(uid = "$wordUid:$dir", wordId = wordId, direction = dir, due = 0)
        }

        fun dayStart(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}

fun CardEntity.toMemory() = Memory(state, stability, difficulty, due, lastReview, reps, lapses)

private fun CardEntity.withMemory(m: Memory, now: Long) = copy(
    state = m.state, stability = m.stability, difficulty = m.difficulty, due = m.due,
    lastReview = m.lastReview, reps = m.reps, lapses = m.lapses, updatedAt = now,
)
