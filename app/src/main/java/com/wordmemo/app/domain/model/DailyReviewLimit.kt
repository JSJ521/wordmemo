package com.wordmemo.app.domain.model

data class DailyReviewLimit(
    val dailyLimit: Int = 13,
    val todayNewCount: Int = 0
) {
    val remaining: Int get() = (dailyLimit - todayNewCount).coerceAtLeast(0)
}
