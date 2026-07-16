package com.wordmemo.app.domain.usecase.ai

import com.wordmemo.app.domain.model.AiRelation
import com.wordmemo.app.domain.repository.AiRepository
import javax.inject.Inject

class GetRelatedWordsUseCase @Inject constructor(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(word: String): List<AiRelation> {
        return aiRepository.getRelatedWords(word)
    }
}
