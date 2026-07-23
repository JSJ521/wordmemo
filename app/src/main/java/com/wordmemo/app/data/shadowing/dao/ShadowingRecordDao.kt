package com.wordmemo.app.data.shadowing.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wordmemo.app.data.shadowing.entity.ShadowingRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShadowingRecordDao {

    @Query("SELECT * FROM shadowing_records WHERE sentence_id = :sentenceId ORDER BY created_at DESC")
    fun observeBySentenceId(sentenceId: Long): Flow<List<ShadowingRecordEntity>>

    @Query("SELECT * FROM shadowing_records WHERE sentence_id = :sentenceId ORDER BY created_at DESC")
    suspend fun getBySentenceId(sentenceId: Long): List<ShadowingRecordEntity>

    @Query("SELECT * FROM shadowing_records WHERE video_id = :videoId ORDER BY created_at DESC")
    suspend fun getByVideoId(videoId: Long): List<ShadowingRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ShadowingRecordEntity): Long

    @Delete
    suspend fun delete(record: ShadowingRecordEntity)

    @Query("DELETE FROM shadowing_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM shadowing_records WHERE video_id = :videoId")
    suspend fun deleteByVideoId(videoId: Long)
}
