package com.wordmemo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fsrs_params")
data class FsrsParamsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "params_json")
    val paramsJson: String,
    @ColumnInfo(name = "review_count")
    val reviewCount: Int,
    @ColumnInfo(name = "optimized_at")
    val optimizedAt: Long = System.currentTimeMillis()
)
