package com.englishcoach.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    val newWordsPerDay: Int = 10,
    val sessionMinutes: Int = 30,
    val paletteId: String = "default",
    val remindersOn: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val reminderDays: Set<Int> = (1..7).toSet(), // DayOfWeek: 1 — понедельник … 7 — воскресенье
    val placementDone: Boolean = false,
    val level: String = "A1",
    val aiModel: String = "claude-sonnet-5-5",
    /** Сервер-посредник вместо api.anthropic.com (у продавцов ключей — ANTHROPIC_BASE_URL); пусто — напрямую. */
    val aiServer: String = "",
    /** Ключ в заголовке Authorization: Bearer (ANTHROPIC_AUTH_TOKEN) вместо x-api-key. */
    val aiBearer: Boolean = false,
    /** Когда менялись учебные настройки, которые синхронизируются между устройствами. */
    val syncedUpdatedAt: Long = 0,
)

/**
 * Настройки, общие для телефона и планшета. Напоминания и ИИ остаются у каждого устройства своими:
 * например, на планшете можно не включать напоминания.
 */
@kotlinx.serialization.Serializable
data class SyncedSettings(
    val newWordsPerDay: Int,
    val sessionMinutes: Int,
    val paletteId: String,
    val placementDone: Boolean,
    val level: String,
) {
    companion object {
        fun of(s: Settings) = SyncedSettings(s.newWordsPerDay, s.sessionMinutes, s.paletteId, s.placementDone, s.level)
    }
}

/** Настройки пользователя. Синхронизация с планшетом — этап 6. */
class SettingsRepository(private val context: Context) {
    private object K {
        val newWords = intPreferencesKey("new_words_per_day")
        val sessionMinutes = intPreferencesKey("session_minutes")
        val palette = stringPreferencesKey("palette")
        val remindersOn = booleanPreferencesKey("reminders_on")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val reminderDays = stringSetPreferencesKey("reminder_days")
        val placementDone = booleanPreferencesKey("placement_done")
        val level = stringPreferencesKey("level")
        val aiModel = stringPreferencesKey("ai_model")
        val aiServer = stringPreferencesKey("ai_server")
        val aiBearer = booleanPreferencesKey("ai_bearer")
        val syncedUpdatedAt = longPreferencesKey("synced_updated_at")
    }

    val flow: Flow<Settings> = context.dataStore.data.map(::fromPrefs)

    suspend fun current(): Settings = flow.first()

    /** Применить учебные настройки с другого устройства (с их временем изменения). */
    suspend fun applySynced(remote: SyncedSettings, updatedAt: Long) = update(stamp = false) {
        it.copy(
            newWordsPerDay = remote.newWordsPerDay,
            sessionMinutes = remote.sessionMinutes,
            paletteId = remote.paletteId,
            placementDone = remote.placementDone,
            level = remote.level,
            syncedUpdatedAt = updatedAt,
        )
    }

    suspend fun update(stamp: Boolean = true, transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val old = fromPrefs(p)
            var s = transform(old)
            // Изменились общие настройки — отметим время, чтобы отправить их на другие устройства
            if (stamp && SyncedSettings.of(s) != SyncedSettings.of(old)) s = s.copy(syncedUpdatedAt = System.currentTimeMillis())
            p[K.newWords] = s.newWordsPerDay
            p[K.sessionMinutes] = s.sessionMinutes
            p[K.palette] = s.paletteId
            p[K.remindersOn] = s.remindersOn
            p[K.reminderHour] = s.reminderHour
            p[K.reminderMinute] = s.reminderMinute
            p[K.reminderDays] = s.reminderDays.map { it.toString() }.toSet()
            p[K.placementDone] = s.placementDone
            p[K.level] = s.level
            p[K.aiModel] = s.aiModel
            p[K.aiServer] = s.aiServer
            p[K.aiBearer] = s.aiBearer
            p[K.syncedUpdatedAt] = s.syncedUpdatedAt
        }
    }

    private fun fromPrefs(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            newWordsPerDay = p[K.newWords] ?: d.newWordsPerDay,
            sessionMinutes = p[K.sessionMinutes] ?: d.sessionMinutes,
            paletteId = p[K.palette] ?: d.paletteId,
            remindersOn = p[K.remindersOn] ?: d.remindersOn,
            reminderHour = p[K.reminderHour] ?: d.reminderHour,
            reminderMinute = p[K.reminderMinute] ?: d.reminderMinute,
            reminderDays = p[K.reminderDays]?.map { it.toInt() }?.toSet() ?: d.reminderDays,
            placementDone = p[K.placementDone] ?: d.placementDone,
            level = p[K.level] ?: d.level,
            aiModel = p[K.aiModel] ?: d.aiModel,
            aiServer = p[K.aiServer] ?: d.aiServer,
            aiBearer = p[K.aiBearer] ?: d.aiBearer,
            syncedUpdatedAt = p[K.syncedUpdatedAt] ?: d.syncedUpdatedAt,
        )
    }
}
