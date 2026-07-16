package com.wordmemo.app.domain.usecase.ai

import com.wordmemo.app.domain.model.AiMnemonic
import com.wordmemo.app.domain.repository.AiRepository
import javax.inject.Inject

class GenerateMnemonicsUseCase @Inject constructor(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(word: String): List<AiMnemonic> {
        return aiRepository.generateMnemonics(word)
    }
}
