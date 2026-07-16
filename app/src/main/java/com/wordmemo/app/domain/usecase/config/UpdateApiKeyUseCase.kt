package com.wordmemo.app.domain.usecase.config

import com.wordmemo.app.domain.model.AppConfig
import com.wordmemo.app.domain.repository.AiRepository
import javax.inject.Inject

class UpdateApiKeyUseCase @Inject constructor(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(apiKey: String) {
        // API Key 通过加密层存储
    }

    suspend fun testConnection(apiKey: String, baseUrl: String, model: String): Boolean {
        return aiRepository.testConnection(apiKey, baseUrl, model)
    }
}
