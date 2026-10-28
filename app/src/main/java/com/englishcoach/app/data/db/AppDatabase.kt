package com.englishcoach.app.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WordEntity::class, CardEntity::class, ReviewLogEntity::class, ActivityEntity::class],
    version = 2,
    exportSchema = true,
    // v2: таблица activity_log (практика) — Room сам создаёт её при обновлении, данные сохраняются
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun cardDao(): CardDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun activityDao(): ActivityDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "english.db").build()
    }
}
