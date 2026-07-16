package com.wordmemo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["word_id"], unique = true), Index("due"), Index("state")]
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "state")
    val state: String = "New",
    @ColumnInfo(name = "stability")
    val stability: Double = 0.0,
    @ColumnInfo(name = "difficulty")
    val difficulty: Double = 5.0,
    @ColumnInfo(name = "due")
    val due: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "elapsed_days")
    val elapsedDays: Double = 0.0,
    @ColumnInfo(name = "scheduled_days")
    val scheduledDays: Double = 0.0,
    @ColumnInfo(name = "reps")
    val reps: Int = 0,
    @ColumnInfo(name = "lapses")
    val lapses: Int = 0,
    @ColumnInfo(name = "last_review")
    val lastReview: Long? = null
)
