package com.wordmemo.app.data.shadowing.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wordmemo.app.data.shadowing.entity.ShadowingSentenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShadowingSentenceDao {

    @Query("SELECT * FROM shadowing_sentences WHERE video_id = :videoId ORDER BY start_time_ms ASC")
    fun observeByVideoId(videoId: Long): Flow<List<ShadowingSentenceEntity>>

    @Query("SELECT * FROM shadowing_sentences WHERE video_id = :videoId ORDER BY start_time_ms ASC")
    suspend fun getByVideoId(videoId: Long): List<ShadowingSentenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(sentences: List<ShadowingSentenceEntity>): List<Long>

    @Update
    suspend fun update(sentence: ShadowingSentenceEntity)

    @Query("DELETE FROM shadowing_sentences WHERE video_id = :videoId")
    suspend fun deleteByVideoId(videoId: Long)
}
