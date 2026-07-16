package com.wordmemo.app.domain.usecase.config

import com.wordmemo.app.domain.usecase.review.CheckDailyLimitUseCase
import javax.inject.Inject

class UpdateDailyReviewLimitUseCase @Inject constructor(
    private val checkDailyLimit: CheckDailyLimitUseCase
) {
    operator fun invoke(limit: Int) {
        checkDailyLimit.setDailyLimit(limit)
    }
}
