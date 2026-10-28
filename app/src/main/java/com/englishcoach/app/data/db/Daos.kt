package com.englishcoach.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT COUNT(*) FROM words")
    fun countFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(words: List<WordEntity>): List<Long>
}

@Dao
interface CardDao {
    @Query("SELECT COUNT(*) FROM cards WHERE state != 0 AND due <= :now")
    fun dueCountFlow(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards WHERE state = 0")
    fun newCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(cards: List<CardEntity>): List<Long>
}
