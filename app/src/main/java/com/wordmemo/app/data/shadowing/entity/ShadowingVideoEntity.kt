package com.wordmemo.app.data.shadowing.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shadowing_videos",
    indices = [Index(value = ["created_at"], orders = [Index.Order.DESC])]
)
data class ShadowingVideoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    @ColumnInfo(name = "source_url")
    val sourceUrl: String? = null,
    @ColumnInfo(name = "file_path")
    val filePath: String,
    @ColumnInfo(name = "cover_path")
    val coverPath: String? = null,
    @ColumnInfo(name = "subtitle_path")
    val subtitlePath: String? = null,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long? = null,
    @ColumnInfo(name = "sentence_count")
    val sentenceCount: Int = 0,
    @ColumnInfo(name = "completed_count")
    val completedCount: Int = 0,
    @ColumnInfo(name = "last_practice_time")
    val lastPracticeTime: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
