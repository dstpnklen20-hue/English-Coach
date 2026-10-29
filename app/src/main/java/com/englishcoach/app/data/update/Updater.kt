package com.englishcoach.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.englishcoach.app.BuildConfig
import com.englishcoach.app.data.CourseSeeder
import com.englishcoach.app.data.content.CourseContent
import com.englishcoach.app.data.practice.PracticeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Описание новой версии: update.json в ветке updates репозитория. */
@Serializable
data class UpdateInfo(val versionCode: Int, val versionName: String, val apk: String, val sha256: String, val notes: String = "")

/**
 * Обновления с GitHub (ветка updates): новая версия приложения — скачать и предложить установить
 * (Android всегда спрашивает подтверждение); новые материалы курса — поставить сразу, без переустановки.
 */
class Updater(
    private val context: Context,
    private val content: CourseContent,
    private val practice: PracticeRepository,
    private val seeder: CourseSeeder,
) {
    sealed interface State {
        data object Idle : State
        data class Available(val info: UpdateInfo) : State
        data class Downloading(val info: UpdateInfo, val progress: Float) : State
        data class Ready(val info: UpdateInfo, val file: File) : State
        data class Failed(val info: UpdateInfo, val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val lock = Mutex()

    /**
     * Проверка приложения и материалов. Автоматически — не чаще раза в 6 часов и молча (нет сети — не беда);
     * по кнопке — сразу, а итог возвращается строкой для экрана.
     */
    suspend fun check(manual: Boolean = false): String? = lock.withLock {
        val now = System.currentTimeMillis()
        if (!manual && now - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_PERIOD_MS) return null
        try {
            val info = json.decodeFromString(UpdateInfo.serializer(), get("update.json"))
            val appNews = isNewer(info, BuildConfig.VERSION_CODE)
            if (appNews && _state.value !is State.Downloading && _state.value !is State.Ready) _state.value = State.Available(info)
            val courseNews = updateCourse()
            prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
            when {
                appNews -> "Доступна версия ${info.versionName}."
                courseNews -> "Материалы курса обновлены."
                else -> "У вас последняя версия."
            }
        } catch (e: Exception) {
            if (manual) "Не удалось проверить: ${e.message ?: e.javaClass.simpleName}" else null
        }
    }

    /** Новые уроки и слова: скачиваются, проверяются и подменяют текущие; слова добавляются в базу. */
    private suspend fun updateCourse(): Boolean {
        val remote = CourseContent.parseManifest(get("content/${CourseContent.MANIFEST}"))
        if (remote.version <= content.manifest.version || remote.minApp > BuildConfig.VERSION_CODE) return false
        content.install(remote, fetch = { get("content/$it") }) { m, read ->
            practice.validate(m.levels, read)
            seeder.validate(m.words, read)
        }
        practice.invalidate()
        seeder.seedIfNeeded()
        return true
    }

    /** Скачивает APK, сверяет контрольную сумму и сразу открывает установку. */
    suspend fun download(info: UpdateInfo) {
        _state.value = State.Downloading(info, 0f)
        try {
            val file = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
                val out = File(dir, "EnglishCoach-${info.versionName}.apk")
                val digest = MessageDigest.getInstance("SHA-256")
                open(info.apk).run {
                    val total = contentLengthLong.coerceAtLeast(1)
                    var done = 0L
                    inputStream.use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                digest.update(buf, 0, n)
                                done += n
                                _state.value = State.Downloading(info, (done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                val sum = digest.digest().joinToString("") { "%02x".format(it) }
                check(sum.equals(info.sha256, ignoreCase = true)) { "файл повреждён при загрузке, попробуйте ещё раз" }
                out
            }
            _state.value = State.Ready(info, file)
            install()
        } catch (e: Exception) {
            _state.value = State.Failed(info, e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Открывает системную установку. Если приложению ещё не разрешено ставить обновления — открывает
     * этот переключатель в настройках Android; после разрешения нужно нажать «Установить» ещё раз.
     */
    fun install() {
        val ready = _state.value as? State.Ready ?: return
        val pm = context.packageManager
        val intent = if (!pm.canRequestPackageInstalls()) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        } else {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", ready.file)
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_TYPE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        open(path).run { inputStream.bufferedReader().use { it.readText() } }
    }

    private fun open(path: String): HttpURLConnection {
        // Метка времени — чтобы не получить устаревшую копию из кэша CDN GitHub
        val conn = URL("$BASE_URL$path?t=${System.currentTimeMillis() / 60_000}").openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("сервер обновлений ответил ${conn.responseCode}")
        }
        return conn
    }

    companion object {
        const val BASE_URL = "https://raw.githubusercontent.com/dstpnklen20-hue/English-Coach/updates/"
        private const val APK_TYPE = "application/vnd.android.package-archive"
        private const val KEY_LAST_CHECK = "last_check"
        private const val CHECK_PERIOD_MS = 6 * 60 * 60_000L
        private val json = Json { ignoreUnknownKeys = true }

        fun isNewer(info: UpdateInfo, currentCode: Int) = info.versionCode > currentCode
    }
}
