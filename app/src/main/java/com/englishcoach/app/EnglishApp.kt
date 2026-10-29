package com.englishcoach.app

import android.app.Application
import com.englishcoach.app.data.CourseSeeder
import com.englishcoach.app.data.SettingsRepository
import com.englishcoach.app.data.StudyRepository
import com.englishcoach.app.data.Tts
import com.englishcoach.app.data.ai.AiRepository
import com.englishcoach.app.data.ai.AiTutor
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
import kotlinx.coroutines.launch

/** Ручной DI: один контейнер зависимостей на приложение. */
class AppContainer(val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: AppDatabase by lazy { AppDatabase.create(app) }
    val settings: SettingsRepository by lazy { SettingsRepository(app) }
    val study: StudyRepository by lazy { StudyRepository(db, Fsrs(), settings) }
    val tts: Tts by lazy { Tts(app) }
    val seeder: CourseSeeder by lazy { CourseSeeder(app, db) }
    val practice: PracticeRepository by lazy { PracticeRepository(app, db) }
    val ai: AiRepository by lazy { AiRepository(app, settings) }
    val tutor: AiTutor by lazy { AiTutor(ai, settings) }
    val sync: SyncRepository by lazy { SyncRepository(app, db, settings, seeder) }
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
        // Синхронизация при открытии и при сворачивании приложения (если выполнен вход)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.appScope.launch { container.sync.sync() }
            }

            override fun onStop(owner: LifecycleOwner) {
                container.appScope.launch { container.sync.sync() }
            }
        })
    }
}
