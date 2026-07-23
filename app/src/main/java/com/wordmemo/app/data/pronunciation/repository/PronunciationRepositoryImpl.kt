package com.wordmemo.app.data.pronunciation.repository

import com.wordmemo.app.data.pronunciation.dao.AssessmentRecordDao
import com.wordmemo.app.data.pronunciation.dao.PhonemeScoreDao
import com.wordmemo.app.data.pronunciation.mapper.toDomain
import com.wordmemo.app.data.pronunciation.mapper.toEntity
import com.wordmemo.app.domain.pronunciation.model.AssessmentRecord
import com.wordmemo.app.domain.pronunciation.repository.PronunciationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PronunciationRepositoryImpl @Inject constructor(
    private val assessmentRecordDao: AssessmentRecordDao,
    private val phonemeScoreDao: PhonemeScoreDao
) : PronunciationRepository {

    override fun observeBySentenceId(sentenceId: Long): Flow<List<AssessmentRecord>> =
        assessmentRecordDao.observeBySentenceId(sentenceId).map { entities ->
            entities.map { entity ->
                val scores = phonemeScoreDao.getByAssessmentId(entity.id)
                entity.toDomain(scores.map { it.toDomain() })
            }
        }

    override fun observeAll(): Flow<List<AssessmentRecord>> =
        assessmentRecordDao.observeAll().map { entities ->
            entities.map { entity ->
                val scores = phonemeScoreDao.getByAssessmentId(entity.id)
                entity.toDomain(scores.map { it.toDomain() })
            }
        }

    override suspend fun getById(id: Long): AssessmentRecord? {
        val entity = assessmentRecordDao.getById(id) ?: return null
        val scores = phonemeScoreDao.getByAssessmentId(id)
        return entity.toDomain(scores.map { it.toDomain() })
    }

    override suspend fun insert(assessment: AssessmentRecord): Long {
        val assessmentId = assessmentRecordDao.insert(assessment.toEntity())
        if (assessment.phonemeScores.isNotEmpty()) {
            phonemeScoreDao.insertBatch(
                assessment.phonemeScores.map { it.copy(assessmentId = assessmentId).toEntity() }
            )
        }
        return assessmentId
    }
}
