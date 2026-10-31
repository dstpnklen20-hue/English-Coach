package com.englishcoach.app.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.englishcoach.app.data.CourseSeeder
import com.englishcoach.app.data.SettingsRepository
import com.englishcoach.app.data.StudyRepository
import com.englishcoach.app.data.SyncedSettings
import com.englishcoach.app.data.ai.SecretStore
import com.englishcoach.app.data.db.ActivityEntity
import com.englishcoach.app.data.db.AppDatabase
import com.englishcoach.app.data.db.CardEntity
import com.englishcoach.app.data.db.ReviewLogEntity
import com.englishcoach.app.data.db.WordEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.time.Instant

// Записи на сервере ссылаются друг на друга по uid: локальные id на телефоне и планшете разные.

@Serializable
data class WordDto(
    val uid: String, val en: String, val ru: String, val transcription: String? = null, val example: String? = null,
    val exampleRu: String? = null, val topic: String, val level: String, val source: String, val updatedAt: Long,
)

@Serializable
data class CardDto(
    val uid: String, val wordUid: String, val direction: Int, val due: Long, val stability: Double, val difficulty: Double,
    val reps: Int, val lapses: Int, val state: Int, val lastReview: Long? = null, val updatedAt: Long,
)

@Serializable
data class ReviewDto(val uid: String, val cardUid: String, val rating: Int, val reviewedAt: Long, val durationMs: Long)

/** Отметка «знаю» у слова (любого, и курса тоже): uid слова, отмечено ли, когда изменено. */
@Serializable
data class KnownDto(val uid: String, val known: Boolean, val at: Long)

@Serializable
data class ActivityDto(
    val uid: String, val kind: String, val itemId: String, val correct: Int, val total: Int, val durationMs: Long, val at: Long,
)

object Kind {
    const val WORD = "word"
    const val CARD = "card"
    const val REVIEW = "review"
    const val ACTIVITY = "activity"
    const val SETTINGS = "settings"
    /** Появилось в 1.3.0; старые версии такие записи пропускают. */
    const val KNOWN = "known"

    /** Порядок применения: слово раньше своих карточек и отметки, карточка раньше своих ответов. */
    val ORDER = listOf(WORD, KNOWN, CARD, REVIEW, ACTIVITY, SETTINGS)
}

/**
 * Синхронизация телефона и планшета через Supabase (таблица english_sync).
 * Отправляем изменённое с прошлой отправки, забираем сохранённое на сервере после прошлого получения;
 * при совпадении побеждает запись, изменённая позже (то же правило проверяет сервер).
 */
class SyncRepository(
    context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val seeder: CourseSeeder,
) {
    data class Status(val email: String? = null, val lastSyncAt: Long = 0, val running: Boolean = false, val error: String? = null)

    private val json = Json { ignoreUnknownKeys = true }
    private val secrets = SecretStore(context)
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val api = SupabaseApi(::freshSession)

    private val _status = MutableStateFlow(Status(email = session()?.email, lastSyncAt = prefs.getLong(LAST_SYNC, 0)))
    val status: StateFlow<Status> = _status

    suspend fun signIn(email: String, password: String) = start(SupabaseAuth.signIn(email, password))

    /** false — нужно подтвердить почту по ссылке из письма, затем войти. */
    suspend fun signUp(email: String, password: String): Boolean {
        val s = SupabaseAuth.signUp(email, password) ?: return false
        start(s)
        return true
    }

    suspend fun signOut() {
        session()?.let { SupabaseAuth.signOut(it) }
        saveSession(null)
        _status.value = Status()
    }

    /** Синхронизировать сейчас; без входа ничего не делает. Повторные вызовы ждут предыдущий. */
    suspend fun sync() {
        if (session() == null) return
        mutex.withLock {
            _status.value = _status.value.copy(running = true, error = null)
            try {
                seeder.seedIfNeeded() // карточки курса должны существовать до применения прогресса с сервера
                push()
                pull()
                val now = System.currentTimeMillis()
                prefs.edit().putLong(LAST_SYNC, now).apply()
                _status.value = _status.value.copy(running = false, lastSyncAt = now)
            } catch (e: SyncException) {
                if (e.authLost) saveSession(null)
                _status.value = _status.value.copy(running = false, error = e.message, email = session()?.email)
            }
        }
    }

    private suspend fun start(s: Session) {
        saveSession(s)
        // Новый вход — отправить всё и забрать всё
        prefs.edit().remove(LAST_PUSH).remove(LAST_PULL).apply()
        _status.value = Status(email = s.email)
        sync()
    }

    private suspend fun push() {
        val since = (prefs.getLong(LAST_PUSH, 0) - OVERLAP_MS).coerceAtLeast(0)
        val startedAt = System.currentTimeMillis()
        val out = mutableListOf<RemoteRecord>()
        db.wordDao().ownChangedSince(since).forEach { w ->
            out += record(Kind.WORD, w.uid, w.updatedAt, WordDto(w.uid, w.en, w.ru, w.transcription, w.example, w.exampleRu, w.topic, w.level, w.source, w.updatedAt))
        }
        db.wordDao().knownChangedSince(since).forEach { w ->
            out += record(Kind.KNOWN, w.uid, w.knownAt, KnownDto(w.uid, w.known, w.knownAt))
        }
        db.cardDao().touchedSince(since).forEach { (c, w) ->
            out += record(Kind.CARD, c.uid, c.updatedAt, CardDto(c.uid, w.uid, c.direction, c.due, c.stability, c.difficulty, c.reps, c.lapses, c.state, c.lastReview, c.updatedAt))
        }
        db.reviewLogDao().since(since).forEach { (r, cardUid) ->
            out += record(Kind.REVIEW, r.uid, r.reviewedAt, ReviewDto(r.uid, cardUid, r.rating, r.reviewedAt, r.durationMs))
        }
        db.activityDao().since(since).forEach { a ->
            out += record(Kind.ACTIVITY, a.uid, a.at, ActivityDto(a.uid, a.kind, a.itemId, a.correct, a.total, a.durationMs, a.at))
        }
        val s = settings.current()
        if (s.syncedUpdatedAt > 0 && s.syncedUpdatedAt >= since) {
            out += record(Kind.SETTINGS, Kind.SETTINGS, s.syncedUpdatedAt, SyncedSettings.of(s))
        }
        out.chunked(PAGE).forEach { api.upsert(it) }
        prefs.edit().putLong(LAST_PUSH, startedAt).apply()
    }

    private suspend fun pull() {
        var since = prefs.getString(LAST_PULL, null)?.let { Instant.parse(it).minusSeconds(60) }
        while (true) {
            val page = api.pull(since, PAGE)
            if (page.isEmpty()) return
            db.withTransaction {
                page.sortedBy { Kind.ORDER.indexOf(it.kind) }.forEach { apply(it) }
            }
            val last = page.mapNotNull { it.serverAt }.maxOrNull() ?: return
            prefs.edit().putString(LAST_PULL, last.toString()).apply()
            if (page.size < PAGE) return
            since = last
        }
    }

    private suspend fun apply(r: RemoteRecord) {
        when (r.kind) {
            Kind.WORD -> {
                val d = json.decodeFromJsonElement<WordDto>(r.data)
                val local = db.wordDao().byUid(d.uid)
                if (local == null) {
                    val id = db.wordDao().insert(
                        WordEntity(uid = d.uid, en = d.en, ru = d.ru, transcription = d.transcription, example = d.example,
                            exampleRu = d.exampleRu, topic = d.topic, level = d.level, source = d.source, updatedAt = d.updatedAt),
                    )
                    if (id > 0) db.cardDao().insertAll(StudyRepository.newCardsFor(id, d.uid))
                } else if (newer(d.updatedAt, local.updatedAt)) {
                    db.wordDao().update(
                        local.copy(en = d.en, ru = d.ru, transcription = d.transcription, example = d.example,
                            exampleRu = d.exampleRu, topic = d.topic, level = d.level, updatedAt = d.updatedAt),
                    )
                }
            }
            Kind.KNOWN -> {
                val d = json.decodeFromJsonElement<KnownDto>(r.data)
                val word = db.wordDao().byUid(d.uid) ?: return
                if (newer(d.at, word.knownAt)) db.wordDao().setKnown(word.id, d.known, d.at)
            }
            Kind.CARD -> {
                val d = json.decodeFromJsonElement<CardDto>(r.data)
                val word = db.wordDao().byUid(d.wordUid) ?: return
                val local = db.cardDao().byUid(d.uid)
                val merged = (local ?: CardEntity(uid = d.uid, wordId = word.id, direction = d.direction, due = d.due)).copy(
                    due = d.due, stability = d.stability, difficulty = d.difficulty, reps = d.reps, lapses = d.lapses,
                    state = d.state, lastReview = d.lastReview, updatedAt = d.updatedAt,
                )
                when {
                    local == null -> db.cardDao().insert(merged)
                    newer(d.updatedAt, local.updatedAt) -> db.cardDao().update(merged)
                }
            }
            Kind.REVIEW -> {
                val d = json.decodeFromJsonElement<ReviewDto>(r.data)
                val card = db.cardDao().byUid(d.cardUid) ?: return
                db.reviewLogDao().insertIgnore(ReviewLogEntity(uid = d.uid, cardId = card.id, rating = d.rating, reviewedAt = d.reviewedAt, durationMs = d.durationMs))
            }
            Kind.ACTIVITY -> {
                val d = json.decodeFromJsonElement<ActivityDto>(r.data)
                db.activityDao().insertIgnore(ActivityEntity(uid = d.uid, kind = d.kind, itemId = d.itemId, correct = d.correct, total = d.total, durationMs = d.durationMs, at = d.at))
            }
            Kind.SETTINGS -> {
                if (newer(r.updatedAt, settings.current().syncedUpdatedAt)) {
                    settings.applySynced(json.decodeFromJsonElement(r.data), r.updatedAt)
                }
            }
        }
    }

    private inline fun <reified T> record(kind: String, id: String, updatedAt: Long, dto: T) =
        RemoteRecord(kind, id, updatedAt, json.encodeToJsonElement(dto).jsonObject)

    private suspend fun freshSession(force: Boolean): Session {
        val s = session() ?: throw SyncException("Войдите в аккаунт синхронизации.", authLost = true)
        if (!force && s.expiresAt - 60 > System.currentTimeMillis() / 1000) return s
        return SupabaseAuth.refresh(s).also { saveSession(it) }
    }

    private fun session(): Session? = secrets.get(SESSION)?.let { runCatching { json.decodeFromString<Session>(it) }.getOrNull() }

    private fun saveSession(s: Session?) {
        if (s == null) secrets.remove(SESSION) else secrets.put(SESSION, json.encodeToString(Session.serializer(), s))
    }

    companion object {
        private const val SESSION = "supabase_session"
        private const val LAST_PUSH = "last_push"
        private const val LAST_PULL = "last_pull"
        private const val LAST_SYNC = "last_sync"
        private const val PAGE = 500
        /** Отправляем с небольшим нахлёстом: изменение в момент отправки не потеряется. */
        private const val OVERLAP_MS = 10_000L

        /** Правило слияния: применяем запись с сервера, только если она изменена позже локальной. */
        fun newer(remote: Long, local: Long) = remote > local
    }
}
