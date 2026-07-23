package com.wordmemo.app.pronunciation

import com.wordmemo.app.domain.pronunciation.model.AssessmentRecord
import com.wordmemo.app.domain.pronunciation.repository.PronunciationRepository
import com.wordmemo.app.domain.pronunciation.service.PronunciationAssessor
import com.wordmemo.app.domain.pronunciation.usecase.GetAssessmentHistoryUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

/**
 * GetAssessmentHistoryUseCase 单元测试。
 */
class GetAssessmentHistoryUseCaseTest {

    @Test
    fun `invoke 委托给 repository observeAll`() = runBlocking {
        val repository = mockk<PronunciationRepository>()
        val useCase = GetAssessmentHistoryUseCase(repository)

        val history = listOf(
            AssessmentRecord(sentenceText = "Hello", overallScore = 95, scoreLevel = "优秀", assessmentType = "shadowing"),
            AssessmentRecord(sentenceText = "World", overallScore = 75, scoreLevel = "良好", assessmentType = "shadowing")
        )

        coEvery { repository.observeAll() } returns flowOf(history)

        val result = useCase().first()
        assertEquals(2, result.size)
        assertEquals(95, result[0].overallScore)
        assertEquals("Hello", result[0].sentenceText)
    }

    @Test
    fun `无历史记录时返回空列表`() = runBlocking {
        val repository = mockk<PronunciationRepository>()
        val useCase = GetAssessmentHistoryUseCase(repository)

        coEvery { repository.observeAll() } returns flowOf(emptyList())

        val result = useCase().first()
        assertTrue(result.isEmpty())
    }
}
