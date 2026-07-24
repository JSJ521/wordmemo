package com.wordmemo.app.data.shadowing.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wordmemo.app.data.shadowing.entity.ReadingProgressEntity

@Dao
interface ReadingProgressDao {

    @Query("SELECT * FROM reading_progress WHERE bookKey = :bookKey")
    suspend fun getProgress(bookKey: String): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: ReadingProgressEntity)

    @Query("DELETE FROM reading_progress WHERE bookKey = :bookKey")
    suspend fun deleteProgress(bookKey: String)
}
