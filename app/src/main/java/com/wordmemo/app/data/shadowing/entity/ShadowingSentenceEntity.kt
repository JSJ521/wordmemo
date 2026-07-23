package com.wordmemo.app.data.shadowing.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shadowing_sentences",
    foreignKeys = [
        ForeignKey(
            entity = ShadowingVideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["video_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["video_id", "sentence_index"])]
)
data class ShadowingSentenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "video_id")
    val videoId: Long,
    @ColumnInfo(name = "sentence_index")
    val sentenceIndex: Int,
    @ColumnInfo(name = "text")
    val text: String,
    @ColumnInfo(name = "start_time_ms")
    val startTimeMs: Long,
    @ColumnInfo(name = "end_time_ms")
    val endTimeMs: Long,
    @ColumnInfo(name = "is_merged")
    val isMerged: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
