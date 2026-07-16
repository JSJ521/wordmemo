package com.wordmemo.app.domain.model

data class Stats(
    val totalWords: Int = 0,
    val dueCards: Int = 0,
    val masteredWords: Int = 0,
    val totalReviews: Int = 0,
    val todayReviews: Int = 0,
    val todayNewCards: Int = 0,
    val dailyReviewLimit: Int = 13,
    val fsrsOptimized: Boolean = false,
    val lastOptimizedAt: Long? = null,
    val dailyHistory: List<DailyStats> = emptyList()
)

data class DailyStats(
    val date: String,
    val reviews: Int = 0,
    val newCards: Int = 0,
    val mastered: Int = 0
)
