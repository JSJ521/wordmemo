package com.wordmemo.app.domain.pronunciation.model

data class PhonemeScore(
    val id: Long = 0,
    val assessmentId: Long = 0,
    val phoneme: String,
    val positionInWord: Int? = null,
    val wordIndex: Int? = null,
    val gopScore: Double,
    val colorTag: String,
    val correctionHint: String? = null
)
