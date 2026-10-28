package com.englishcoach.app

import android.app.Application
import com.englishcoach.app.data.db.AppDatabase

/** Ручной DI: один контейнер зависимостей на приложение. */
class AppContainer(app: Application) {
    val db: AppDatabase by lazy { AppDatabase.create(app) }
}

class EnglishApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
