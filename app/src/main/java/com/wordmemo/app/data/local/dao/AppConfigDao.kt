package com.wordmemo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wordmemo.app.data.local.entity.AppConfigEntity

@Dao
interface AppConfigDao {
    @Query("SELECT * FROM app_config WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): AppConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setValue(config: AppConfigEntity)

    @Query("DELETE FROM app_config WHERE `key` = :key")
    suspend fun deleteValue(key: String)

    @Query("SELECT * FROM app_config")
    suspend fun getAll(): List<AppConfigEntity>
}
