package com.wordmemo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "review_logs",
    foreignKeys = [
        ForeignKey(
            entity = FlashcardEntity::class,
            parentColumns = ["id"],
            childColumns = ["card_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("card_id")]
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "card_id")
    val cardId: Long,
    @ColumnInfo(name = "rating")
    val rating: Int = 3,
    @ColumnInfo(name = "reviewed_at")
    val reviewedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "duration_ms")
    val durationMs: Int = 0,
    @ColumnInfo(name = "stability_before")
    val stabilityBefore: Double = 0.0,
    @ColumnInfo(name = "difficulty_before")
    val difficultyBefore: Double = 0.0,
    @ColumnInfo(name = "stability_after")
    val stabilityAfter: Double = 0.0,
    @ColumnInfo(name = "difficulty_after")
    val difficultyAfter: Double = 0.0
)
