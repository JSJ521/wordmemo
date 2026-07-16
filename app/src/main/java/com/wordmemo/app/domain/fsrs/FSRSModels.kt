package com.wordmemo.app.domain.fsrs

/**
 * FSRS 算法中的卡片模型（纯 Kotlin 域模型，非 Room Entity）。
 */
data class FSRSFlashcard(
    val id: Long = 0,
    val wordId: Long = 0,
    val state: FSRSState = FSRSState.NEW,
    val stability: Double = 0.0,
    val difficulty: Double = 5.0,
    val due: Long = System.currentTimeMillis(),
    val elapsedDays: Double = 0.0,
    val scheduledDays: Double = 0.0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastReview: Long? = null
)

/**
 * 复习日志（纯 Kotlin 域模型）。
 */
data class FSRSReviewLog(
    val id: Long = 0,
    val cardId: Long = 0,
    val rating: Rating = Rating.GOOD,
    val reviewedAt: Long = System.currentTimeMillis(),
    val durationMs: Int = 0,
    val stabilityBefore: Double = 0.0,
    val difficultyBefore: Double = 0.0,
    val stabilityAfter: Double = 0.0,
    val difficultyAfter: Double = 0.0
)
