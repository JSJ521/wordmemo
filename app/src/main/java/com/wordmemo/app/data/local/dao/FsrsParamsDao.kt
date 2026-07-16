package com.wordmemo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wordmemo.app.data.local.entity.FsrsParamsEntity

@Dao
interface FsrsParamsDao {
    @Query("SELECT * FROM fsrs_params ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): FsrsParamsEntity?

    @Insert
    suspend fun insert(params: FsrsParamsEntity): Long

    @Query("SELECT COUNT(*) FROM fsrs_params")
    suspend fun count(): Int
}
