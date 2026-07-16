package com.wordmemo.app.domain.usecase.review

import com.wordmemo.app.domain.fsrs.FSRSDefaults
import com.wordmemo.app.domain.repository.ReviewRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

sealed class DailyLimitResult {
    data class Allowed(val remaining: Int) : DailyLimitResult()
    data class Rejected(val reason: String) : DailyLimitResult()
}

class CheckDailyLimitUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository
) {
    private var dailyLimit: Int = FSRSDefaults.DEFAULT_DAILY_REVIEW_LIMIT
    private var optimizeThreshold: Int = FSRSDefaults.DEFAULT_OPTIMIZE_THRESHOLD

    suspend operator fun invoke(): DailyLimitResult {
        val todayNew = reviewRepository.countTodayNewCards()
        return if (todayNew < dailyLimit) {
            DailyLimitResult.Allowed(dailyLimit - todayNew)
        } else {
            DailyLimitResult.Rejected("已达每日上限 $dailyLimit 条")
        }
    }

    fun getDailyLimit(): Int = dailyLimit

    fun setDailyLimit(limit: Int) {
        dailyLimit = limit.coerceIn(0, 999)
    }

    fun getOptimizeThreshold(): Int = optimizeThreshold

    fun setOptimizeThreshold(threshold: Int) {
        optimizeThreshold = threshold.coerceAtLeast(5)
    }
}
