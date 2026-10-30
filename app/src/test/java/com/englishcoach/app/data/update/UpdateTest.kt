package com.englishcoach.app.data.update

import com.englishcoach.app.data.content.CourseContent
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateTest {
    @Test
    fun updateInfoParsesAndComparesByVersionCode() {
        val info = Json { ignoreUnknownKeys = true }.decodeFromString(
            UpdateInfo.serializer(),
            """{"versionCode": 4, "versionName": "1.2.0", "apk": "EnglishCoach.apk", "sha256": "ab", "extra": 1}""",
        )
        assertEquals("1.2.0", info.versionName)
        assertEquals("", info.notes)
        assertTrue(Updater.isNewer(info, 3))
        assertFalse(Updater.isNewer(info, 4))
    }

    @Test
    fun manifestListsAllCourseFiles() {
        val m = CourseContent.parseManifest("""{"version": 2, "levels": ["a1", "b1"], "words": ["a1_words.json"]}""")
        assertEquals(0, m.minApp)
        assertEquals(
            listOf(
                "a1_grammar.json", "a1_reading.json", "a1_dialogs.json", "a1_drills.json",
                "b1_grammar.json", "b1_reading.json", "b1_dialogs.json", "b1_drills.json", "a1_words.json",
            ),
            m.files,
        )
    }

    @Test
    fun onlySimpleFileNamesAreAccepted() {
        assertTrue(CourseContent.isSafeName("b2_words.json"))
        assertFalse(CourseContent.isSafeName("../databases/app.json"))
        assertFalse(CourseContent.isSafeName("sub/a1_words.json"))
        assertFalse(CourseContent.isSafeName("A1.json"))
    }

    @Test
    fun bundledManifestMatchesCourseFiles() {
        // Тесты запускаются из папки app/: встроенный манифест должен ссылаться только на существующие файлы
        val dir = File("src/main/assets/course")
        val m = CourseContent.parseManifest(File(dir, CourseContent.MANIFEST).readText())
        m.files.forEach { assertTrue("нет файла $it", File(dir, it).exists()) }
    }
}
