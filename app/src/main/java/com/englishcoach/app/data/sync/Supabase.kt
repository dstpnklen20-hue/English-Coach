package com.englishcoach.app.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Проект Supabase — тот же, что у «Тик Так» (вход той же почтой). Публичный ключ предназначен
 * для приложений: правила доступа на сервере пускают каждый аккаунт только к его записям.
 */
object SupabaseConfig {
    const val URL = "https://bjwpdcenqeckobvtpvwy.supabase.co"
    const val KEY = "sb_publishable_4KRzQLfSBYfhhr7CjLKdrw_dTrx72js"
    const val TABLE = "english_sync"
}

@Serializable
data class Session(
    val accessToken: String,
    val refreshToken: String,
    /** Секунды с 1970 г. */
    val expiresAt: Long,
    val userId: String,
    val email: String,
)

/** Запись на сервере: вид, uid, время изменения на устройстве и сама запись. */
data class RemoteRecord(
    val kind: String,
    val id: String,
    val updatedAt: Long,
    val data: JsonObject,
    val serverAt: Instant? = null,
)

/** Ошибка с сообщением для пользователя; authLost — нужно войти заново. */
class SyncException(message: String, val code: Int = 0, val authLost: Boolean = false) : Exception(message)

private class HttpResult(val code: Int, val body: String)

private val json = Json { ignoreUnknownKeys = true }

private suspend fun http(
    method: String,
    url: String,
    token: String?,
    body: String? = null,
    headers: Map<String, String> = emptyMap(),
): HttpResult = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("apikey", SupabaseConfig.KEY)
        conn.setRequestProperty("Authorization", "Bearer ${token ?: SupabaseConfig.KEY}")
        conn.setRequestProperty("Accept", "application/json")
        for ((k, v) in headers) conn.setRequestProperty(k, v)
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        HttpResult(code, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
    } catch (e: IOException) {
        throw SyncException("Нет связи с сервером. Проверьте интернет.")
    } finally {
        conn.disconnect()
    }
}

/** Ответ Supabase с ошибкой → сообщение по-русски. */
private fun failure(r: HttpResult): SyncException {
    val o = runCatching { json.parseToJsonElement(r.body).jsonObject }.getOrNull()
    fun field(name: String) = o?.get(name)?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }.orEmpty()
    val code = field("error_code").ifEmpty { field("error") }
    val raw = field("msg").ifEmpty { field("message") }.ifEmpty { field("error_description") }.ifEmpty { "HTTP ${r.code}" }
    val message = when {
        code == "invalid_credentials" || raw.contains("Invalid login credentials") -> "Неверная почта или пароль."
        code == "email_not_confirmed" -> "Почта не подтверждена: откройте письмо от Supabase, нажмите ссылку и войдите."
        code == "user_already_exists" || raw.contains("already registered") -> "Такой аккаунт уже есть — нажмите «Войти»."
        code == "weak_password" || raw.contains("Password should be") -> "Пароль слишком простой: нужно не меньше 6 символов."
        code.contains("rate_limit") -> "Слишком много попыток. Подождите немного."
        r.code == 404 || raw.contains(SupabaseConfig.TABLE) && raw.contains("does not exist") ->
            "На сервере ещё нет таблицы: выполните supabase/schema.sql в Supabase (SQL Editor)."
        else -> "Ошибка сервера: $raw"
    }
    val authLost = r.code == 401 || code in setOf("refresh_token_not_found", "refresh_token_already_used", "session_not_found")
    return SyncException(message, r.code, authLost)
}

/** Вход по почте и паролю (Supabase Auth) — тот же аккаунт, что в «Тик Так». */
object SupabaseAuth {
    private fun sessionFrom(body: String): Session {
        val o = json.parseToJsonElement(body).jsonObject
        val user = o.getValue("user").jsonObject
        val expiresAt = o["expires_at"]?.jsonPrimitive?.longOrNull
            ?: (System.currentTimeMillis() / 1000 + (o["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600))
        return Session(
            accessToken = o.getValue("access_token").jsonPrimitive.content,
            refreshToken = o.getValue("refresh_token").jsonPrimitive.content,
            expiresAt = expiresAt,
            userId = user.getValue("id").jsonPrimitive.content,
            email = user["email"]?.jsonPrimitive?.content.orEmpty(),
        )
    }

    private fun credentials(email: String, password: String) =
        buildJsonObject { put("email", email.trim()); put("password", password) }.toString()

    suspend fun signIn(email: String, password: String): Session {
        val r = http("POST", "${SupabaseConfig.URL}/auth/v1/token?grant_type=password", null, credentials(email, password))
        if (r.code !in 200..299) throw failure(r)
        return sessionFrom(r.body)
    }

    /** null — проект просит подтвердить почту: войти можно после ссылки из письма. */
    suspend fun signUp(email: String, password: String): Session? {
        val r = http("POST", "${SupabaseConfig.URL}/auth/v1/signup", null, credentials(email, password))
        if (r.code !in 200..299) throw failure(r)
        return if (r.body.contains("access_token")) sessionFrom(r.body) else null
    }

    suspend fun refresh(session: Session): Session {
        val body = buildJsonObject { put("refresh_token", session.refreshToken) }.toString()
        val r = http("POST", "${SupabaseConfig.URL}/auth/v1/token?grant_type=refresh_token", null, body)
        if (r.code !in 200..299) {
            throw if (r.code in 400..499) SyncException("Сеанс истёк — войдите снова.", r.code, authLost = true) else failure(r)
        }
        return sessionFrom(r.body)
    }

    suspend fun signOut(session: Session) {
        runCatching { http("POST", "${SupabaseConfig.URL}/auth/v1/logout", session.accessToken) }
    }
}

/** Таблица english_sync через REST (PostgREST). */
class SupabaseApi(private val session: suspend (forceRefresh: Boolean) -> Session) {
    private val base = "${SupabaseConfig.URL}/rest/v1/${SupabaseConfig.TABLE}"

    /** Один повтор со свежим токеном, если сервер сказал, что старый истёк. */
    private suspend fun call(method: String, url: String, body: String? = null, headers: Map<String, String> = emptyMap()): HttpResult {
        var r = http(method, url, session(false).accessToken, body, headers)
        if (r.code == 401) r = http(method, url, session(true).accessToken, body, headers)
        if (r.code !in 200..299) throw failure(r)
        return r
    }

    suspend fun upsert(records: List<RemoteRecord>) {
        if (records.isEmpty()) return
        val userId = session(false).userId
        val body = buildJsonArray {
            records.forEach { rec ->
                add(
                    buildJsonObject {
                        put("user_id", userId)
                        put("kind", rec.kind)
                        put("id", rec.id)
                        put("updated_at", rec.updatedAt)
                        put("deleted", false)
                        put("data", rec.data)
                    },
                )
            }
        }
        call("POST", "$base?on_conflict=user_id,kind,id", body.toString(), mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))
    }

    /** Записи, сохранённые на сервере после since (по серверному времени), по порядку. */
    suspend fun pull(since: Instant?, limit: Int): List<RemoteRecord> {
        val userId = session(false).userId
        val filter = since?.let { "&server_updated_at=gt.${enc(it.toString())}" }.orEmpty()
        val r = call(
            "GET",
            "$base?select=kind,id,updated_at,data,server_updated_at&user_id=eq.$userId$filter&order=server_updated_at.asc&limit=$limit",
        )
        return (json.parseToJsonElement(r.body) as JsonArray).map { e: JsonElement ->
            val o = e.jsonObject
            RemoteRecord(
                kind = o.getValue("kind").jsonPrimitive.content,
                id = o.getValue("id").jsonPrimitive.content,
                updatedAt = o.getValue("updated_at").jsonPrimitive.long,
                data = o.getValue("data").jsonObject,
                serverAt = OffsetDateTime.parse(o.getValue("server_updated_at").jsonPrimitive.content).toInstant(),
            )
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
