package com.englishcoach.app.data.content

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Материалы курса (слова, уроки, тексты, диалоги): встроенные в приложение (assets/course) или скачанные
 * обновления (files/course) — берётся то, у чего версия выше. Так новые уроки приходят без переустановки.
 */
class CourseContent(private val context: Context) {

    /** Описание набора материалов: course/manifest.json. minApp — с какой версии приложения он подходит. */
    @Serializable
    data class Manifest(val version: Int, val minApp: Int = 0, val levels: List<String>, val words: List<String>) {
        /** Все файлы набора, кроме самого описания. */
        val files: List<String> get() = levels.flatMap { l -> PARTS.map { "${l}_$it.json" } } + words + COMMON
    }

    private val dir = File(context.filesDir, "course")
    private val bundled: Manifest by lazy { parseManifest(context.assets.open("course/$MANIFEST").bufferedReader().use { it.readText() }) }
    @Volatile private var downloaded: Manifest? = runCatching { parseManifest(File(dir, MANIFEST).readText()) }.getOrNull()

    /** Действующий набор: скачанный, если он новее встроенного. */
    val manifest: Manifest get() = downloaded?.takeIf { it.version > bundled.version } ?: bundled

    fun text(name: String): String =
        if (manifest !== bundled) File(dir, name).readText()
        else context.assets.open("course/$name").bufferedReader().use { it.readText() }

    /**
     * Ставит скачанный набор: файлы пишутся во временную папку, проверяются [validate] (разбор теми же
     * классами, что в приложении) и только потом заменяют действующие — битое обновление не сломает курс.
     */
    suspend fun install(remote: Manifest, fetch: suspend (String) -> String, validate: (Manifest, (String) -> String) -> Unit) =
        withContext(Dispatchers.IO) {
            require(remote.files.all(::isSafeName)) { "Недопустимое имя файла в обновлении" }
            val tmp = File(context.filesDir, "course_new").apply { deleteRecursively(); mkdirs() }
            for (name in remote.files) File(tmp, name).writeText(fetch(name))
            validate(remote) { File(tmp, it).readText() }
            File(tmp, MANIFEST).writeText(json.encodeToString(Manifest.serializer(), remote))
            val old = File(context.filesDir, "course_old").apply { deleteRecursively() }
            if (dir.exists()) dir.renameTo(old)
            check(tmp.renameTo(dir)) { "Не удалось заменить материалы курса" }
            old.deleteRecursively()
            downloaded = remote
        }

    companion object {
        const val MANIFEST = "manifest.json"
        private val PARTS = listOf("grammar", "reading", "dialogs", "drills")
        /** Общие для всех уровней файлы: группы слов. */
        private val COMMON = listOf("groups.json")
        private val json = Json { ignoreUnknownKeys = true }

        fun parseManifest(text: String): Manifest = json.decodeFromString(Manifest.serializer(), text)

        /** Только простые имена вида a1_words.json — без папок и «..». */
        fun isSafeName(name: String) = Regex("[a-z0-9_]+\\.json").matches(name)
    }
}
