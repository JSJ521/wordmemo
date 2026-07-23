package com.wordmemo.app.data.shadowing.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wordmemo.app.data.shadowing.entity.ShadowingVideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShadowingVideoDao {

    @Query("SELECT * FROM shadowing_videos ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ShadowingVideoEntity>>

    @Query("SELECT * FROM shadowing_videos WHERE id = :id")
    suspend fun getById(id: Long): ShadowingVideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: ShadowingVideoEntity): Long

    @Update
    suspend fun update(video: ShadowingVideoEntity)

    @Delete
    suspend fun delete(video: ShadowingVideoEntity)

    @Query("DELETE FROM shadowing_videos WHERE id = :id")
    suspend fun deleteById(id: Long)
}
