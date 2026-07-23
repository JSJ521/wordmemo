package com.wordmemo.app.data.shadowing.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shadowing_records",
    foreignKeys = [
        ForeignKey(
            entity = ShadowingVideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["video_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ShadowingSentenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sentence_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sentence_id"]),
        Index(value = ["video_id"])
    ]
)
data class ShadowingRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "video_id")
    val videoId: Long,
    @ColumnInfo(name = "sentence_id")
    val sentenceId: Long,
    @ColumnInfo(name = "audio_file_path")
    val audioFilePath: String,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "pronunciation_status")
    val pronunciationStatus: Int = 0
)
