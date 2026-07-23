package com.wordmemo.app.domain.pronunciation.repository

import com.wordmemo.app.domain.pronunciation.model.AssessmentRecord
import kotlinx.coroutines.flow.Flow

interface PronunciationRepository {

    fun observeBySentenceId(sentenceId: Long): Flow<List<AssessmentRecord>>

    fun observeAll(): Flow<List<AssessmentRecord>>

    suspend fun getById(id: Long): AssessmentRecord?

    suspend fun insert(assessment: AssessmentRecord): Long
}
