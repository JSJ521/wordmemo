package com.wordmemo.app.domain.pronunciation.model

data class AssessmentRecord(
    val id: Long = 0,
    val recordId: Long? = null,
    val sentenceId: Long? = null,
    val sentenceText: String,
    val transcribedText: String? = null,
    val overallScore: Int,
    val scoreLevel: String,
    val diagnosticReport: String? = null,
    val correctionSuggestions: String? = null,
    val assessmentType: String = "shadowing",
    val createdAt: Long = System.currentTimeMillis(),
    val phonemeScores: List<PhonemeScore> = emptyList()
)
