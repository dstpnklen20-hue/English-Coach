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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/** Всё, что касается учёбы слов: очередь карточек, сохранение ответов, дневной лимит новых, итоги дня. */
class StudyRepository(private val db: AppDatabase, val fsrs: Fsrs, private val settings: SettingsRepository) {

    fun dueCountFlow(now: Long): Flow<Int> = db.cardDao().dueCountFlow(now)

    /** Сколько новых карточек ещё можно показать сегодня (каждое слово — две карточки); отложенные темы не считаются. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun newTodayCountFlow(): Flow<Int> = settings.flow.flatMapLatest { s ->
        combine(
            db.cardDao().newAvailableCountFlow(s.laterTopics.toList()),
            db.reviewLogDao().introducedSinceFlow(dayStart()),
        ) { available, introduced -> minOf(available, (s.newWordsPerDay * 2 - introduced).coerceAtLeast(0)) }
    }

    fun dayTotalsFlow(): Flow<List<DayTotal>> = db.reviewLogDao().dayTotalsFlow()

    suspend fun todayStudyMs(): Long =
        dayTotalsFlow().first().firstOrNull { it.day == LocalDate.now().toString() }?.ms ?: 0

    /** Очередь на сессию: сначала карточки к повторению, затем новые в пределах лимита. */
    suspend fun loadQueue(now: Long): List<CardWithWord> {
        val due = db.cardDao().dueCards(now, MAX_REVIEWS)
        val s = settings.current()
        val newLimit = (s.newWordsPerDay * 2 - db.reviewLogDao().introducedSince(dayStart())).coerceAtLeast(0)
        return due + newCards(newLimit)
    }

    /** Новое состояние карточки по FSRS — без записи в базу, чтобы экран не ждал диск. */
    fun schedule(card: CardEntity, rating: Rating, now: Long): CardEntity =
        card.withMemory(fsrs.schedule(card.toMemory(), rating, now), now)

    /** Сохраняет пересчитанную карточку и запись в журнал. */
    suspend fun saveAnswer(updated: CardEntity, rating: Rating, now: Long, durationMs: Long) {
        db.withTransaction {
            db.cardDao().update(updated)
            db.reviewLogDao().insert(ReviewLogEntity(cardId = updated.id, rating = rating.value, reviewedAt = now, durationMs = durationMs))
        }
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

    /** Отметка «знаю это слово» (или её снятие — «вернуть в изучение»). Карточки и их прогресс FSRS не меняются. */
    suspend fun setKnown(wordId: Long, known: Boolean) = db.wordDao().setKnown(wordId, known, System.currentTimeMillis())

    suspend fun studiedCards(): List<CardWithWord> = db.cardDao().studiedCards()

    /** Новые карточки сверх уже взятых в очередь — на место слов, отмеченных «знаю» во время сессии. */
    suspend fun moreNewCards(exclude: Set<Long>, count: Int): List<CardWithWord> =
        if (count <= 0) emptyList() else newCards(exclude.size + count).filter { it.card.id !in exclude }.take(count)

    /** Новые карточки с учётом тем: «сначала» — первыми, «позже» — не берутся. */
    private suspend fun newCards(limit: Int): List<CardWithWord> {
        val s = settings.current()
        return db.cardDao().newCards(limit, s.firstTopics.toList(), s.laterTopics.toList())
    }

    suspend fun addUserWord(en: String, ru: String) {
        db.withTransaction {
            val word = WordEntity(en = en.trim(), ru = ru.trim(), topic = USER_TOPIC, level = "", source = "user")
            val id = db.wordDao().insert(word)
            if (id > 0) db.cardDao().insertAll(newCardsFor(id, word.uid))
        }
    }

    suspend fun knownWords(): Set<String> = db.wordDao().allEnglish().toSet()

    /** Импорт слов с карточками; уже имеющиеся (по английскому написанию) пропускаются. Вернёт число добавленных. */
    suspend fun addImported(words: List<ImportWord>): Int = db.withTransaction {
        var added = 0
        for (w in words) {
            val en = w.en.trim()
            if (en.isEmpty() || w.ru.isBlank() || db.wordDao().findByEnglish(en.lowercase()) != null) continue
            val word = WordEntity(en = en, ru = w.ru.trim(), example = w.example, topic = w.topic, level = "", source = "import")
            val id = db.wordDao().insert(word)
            if (id > 0) {
                db.cardDao().insertAll(newCardsFor(id, word.uid))
                added++
            }
        }
        added
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

data class ImportWord(val en: String, val ru: String, val example: String?, val topic: String)

fun CardEntity.toMemory() = Memory(state, stability, difficulty, due, lastReview, reps, lapses)

private fun CardEntity.withMemory(m: Memory, now: Long) = copy(
    state = m.state, stability = m.stability, difficulty = m.difficulty, due = m.due,
    lastReview = m.lastReview, reps = m.reps, lapses = m.lapses, updatedAt = now,
)
