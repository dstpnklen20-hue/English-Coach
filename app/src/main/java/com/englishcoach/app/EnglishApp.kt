package com.englishcoach.app

import android.app.Application
import com.englishcoach.app.data.CourseSeeder
import com.englishcoach.app.data.SettingsRepository
import com.englishcoach.app.data.StudyRepository
import com.englishcoach.app.data.Tts
import com.englishcoach.app.data.ai.AiRepository
import com.englishcoach.app.data.ai.AiTutor
import com.englishcoach.app.data.content.CourseContent
import com.englishcoach.app.data.update.Updater
import com.englishcoach.app.data.db.AppDatabase
import com.englishcoach.app.data.fsrs.Fsrs
import com.englishcoach.app.data.practice.PracticeRepository
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.englishcoach.app.data.sync.SyncRepository
import com.englishcoach.app.reminders.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Ручной DI: один контейнер зависимостей на приложение. */
class AppContainer(val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: AppDatabase by lazy { AppDatabase.create(app) }
    val settings: SettingsRepository by lazy { SettingsRepository(app) }
    val study: StudyRepository by lazy { StudyRepository(db, Fsrs(), settings) }
    val tts: Tts by lazy { Tts(app) }
    val content: CourseContent by lazy { CourseContent(app) }
    val seeder: CourseSeeder by lazy { CourseSeeder(app, content, db) }
    val practice: PracticeRepository by lazy { PracticeRepository(content, db) }
    val updater: Updater by lazy { Updater(app, content, practice, seeder) }
    val ai: AiRepository by lazy { AiRepository(app, settings) }
    val tutor: AiTutor by lazy { AiTutor(ai, settings) }
    val sync: SyncRepository by lazy { SyncRepository(app, db, settings, seeder) }
    /** Текст, присланный через «Поделиться», — его подхватывает экран импорта. */
    val pendingShare = MutableStateFlow<String?>(null)
}

class EnglishApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Reminders.createChannel(this)
        container.appScope.launch {
            container.seeder.seedIfNeeded()
            // Будильник напоминания мог потеряться (обновление приложения) — ставим заново
            Reminders.schedule(this@EnglishApp, container.settings.current())
        }
        // Материалы практики разбираем заранее, в фоне — «Практика» и уроки открываются без задержки
        container.appScope.launch {
            container.practice.grammarLessons()
            container.practice.readingTexts()
            container.practice.dialogs()
        }
        // Синхронизация при открытии и при сворачивании приложения (если выполнен вход)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.appScope.launch { container.sync.sync() }
                // Новая версия и новые материалы с GitHub — не чаще раза в 6 часов
                container.appScope.launch { container.updater.check() }
            }

            override fun onStop(owner: LifecycleOwner) {
                container.appScope.launch { container.sync.sync() }
            }
        })
    }
}
