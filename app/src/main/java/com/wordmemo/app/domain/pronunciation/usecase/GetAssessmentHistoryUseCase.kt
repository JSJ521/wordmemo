package com.wordmemo.app.domain.pronunciation.usecase

import com.wordmemo.app.domain.pronunciation.model.AssessmentRecord
import com.wordmemo.app.domain.pronunciation.repository.PronunciationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAssessmentHistoryUseCase @Inject constructor(
    private val pronunciationRepository: PronunciationRepository
) {
    operator fun invoke(): Flow<List<AssessmentRecord>> =
        pronunciationRepository.observeAll()
}
