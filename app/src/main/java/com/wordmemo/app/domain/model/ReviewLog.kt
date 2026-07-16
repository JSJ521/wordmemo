package com.wordmemo.app.domain.model

data class ReviewLog(
    val id: Long = 0,
    val cardId: Long = 0,
    val rating: Int = 3,
    val reviewedAt: Long = System.currentTimeMillis(),
    val durationMs: Int = 0,
    val stabilityBefore: Double = 0.0,
    val difficultyBefore: Double = 0.0,
    val stabilityAfter: Double = 0.0,
    val difficultyAfter: Double = 0.0
)
