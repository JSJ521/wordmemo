package com.wordmemo.app.domain.usecase.config

import com.wordmemo.app.domain.model.AppConfig
import com.wordmemo.app.domain.usecase.review.CheckDailyLimitUseCase
import javax.inject.Inject

class GetAppConfigUseCase @Inject constructor(
    private val checkDailyLimit: CheckDailyLimitUseCase
) {
    fun getDailyLimit(): Int = checkDailyLimit.getDailyLimit()
}
