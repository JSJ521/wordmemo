package com.wordmemo.app.data.pronunciation.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "phoneme_scores",
    foreignKeys = [
        ForeignKey(
            entity = AssessmentRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["assessment_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["assessment_id"])]
)
data class PhonemeScoreEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "assessment_id")
    val assessmentId: Long,
    @ColumnInfo(name = "phoneme")
    val phoneme: String,
    @ColumnInfo(name = "position_in_word")
    val positionInWord: Int? = null,
    @ColumnInfo(name = "word_index")
    val wordIndex: Int? = null,
    @ColumnInfo(name = "gop_score")
    val gopScore: Double,
    @ColumnInfo(name = "color_tag")
    val colorTag: String,
    @ColumnInfo(name = "correction_hint")
    val correctionHint: String? = null
)
