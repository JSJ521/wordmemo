package com.wordmemo.app.domain.usecase.config

import com.wordmemo.app.domain.repository.AiRepository
import javax.inject.Inject

class TestConnectionUseCase @Inject constructor(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(apiKey: String, baseUrl: String, model: String): Boolean {
        return aiRepository.testConnection(apiKey, baseUrl, model)
    }
}
