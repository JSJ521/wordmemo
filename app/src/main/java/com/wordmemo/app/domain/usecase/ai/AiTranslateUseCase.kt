package com.wordmemo.app.domain.usecase.ai

import com.wordmemo.app.domain.model.AiTranslation
import com.wordmemo.app.domain.repository.AiRepository
import javax.inject.Inject

class AiTranslateUseCase @Inject constructor(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(word: String): AiTranslation {
        return aiRepository.translate(word)
    }
}
