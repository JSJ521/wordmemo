package com.wordmemo.app.data.pronunciation.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wordmemo.app.data.shadowing.entity.ShadowingRecordEntity
import com.wordmemo.app.data.shadowing.entity.ShadowingSentenceEntity

@Entity(
    tableName = "assessment_records",
    foreignKeys = [
        ForeignKey(
            entity = ShadowingRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["record_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ShadowingSentenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sentence_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["record_id"]),
        Index(value = ["sentence_id"]),
        Index(value = ["created_at"], orders = [Index.Order.DESC])
    ]
)
data class AssessmentRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "record_id")
    val recordId: Long? = null,
    @ColumnInfo(name = "sentence_id")
    val sentenceId: Long? = null,
    @ColumnInfo(name = "sentence_text")
    val sentenceText: String,
    @ColumnInfo(name = "transcribed_text")
    val transcribedText: String? = null,
    @ColumnInfo(name = "overall_score")
    val overallScore: Int,
    @ColumnInfo(name = "score_level")
    val scoreLevel: String,
    @ColumnInfo(name = "diagnostic_report")
    val diagnosticReport: String? = null,
    @ColumnInfo(name = "correction_suggestions")
    val correctionSuggestions: String? = null,
    @ColumnInfo(name = "assessment_type")
    val assessmentType: String = "shadowing",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
