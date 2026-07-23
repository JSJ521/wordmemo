package com.wordmemo.app.domain.pronunciation.usecase

import com.wordmemo.app.domain.pronunciation.model.AssessmentRecord
import com.wordmemo.app.domain.pronunciation.repository.PronunciationRepository
import com.wordmemo.app.domain.pronunciation.service.PronunciationAssessor
import javax.inject.Inject

class AssessPronunciationUseCase @Inject constructor(
    private val pronunciationAssessor: PronunciationAssessor,
    private val pronunciationRepository: PronunciationRepository
) {
    suspend operator fun invoke(
        recordId: Long,
        sentenceText: String,
        audioFilePath: String
    ): Result<AssessmentRecord> {
        return try {
            val assessment = pronunciationAssessor.recordToAssessment(
                recordId = recordId,
                sentenceText = sentenceText,
                audioFilePath = audioFilePath
            )
            val id = pronunciationRepository.insert(assessment)
            Result.success(assessment.copy(id = id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
