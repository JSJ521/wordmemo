package com.wordmemo.app.pronunciation

import com.wordmemo.app.domain.pronunciation.model.AssessmentRecord
import com.wordmemo.app.domain.pronunciation.model.PhonemeScore
import com.wordmemo.app.domain.pronunciation.repository.PronunciationRepository
import com.wordmemo.app.domain.pronunciation.service.PronunciationAssessor
import com.wordmemo.app.domain.pronunciation.usecase.AssessPronunciationUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

/**
 * AssessPronunciationUseCase 单元测试 — 验证发音测评业务编排。
 */
class AssessPronunciationUseCaseTest {

    private val pronunciationAssessor = mockk<PronunciationAssessor>()
    private val pronunciationRepository = mockk<PronunciationRepository>()
    private val useCase = AssessPronunciationUseCase(pronunciationAssessor, pronunciationRepository)

    @Test
    fun `成功测评时保存并返回带ID的记录`() = runBlocking {
        val assessment = AssessmentRecord(
            sentenceText = "Hello world",
            overallScore = 95,
            scoreLevel = "优秀",
            assessmentType = "shadowing",
            phonemeScores = listOf(
                PhonemeScore(phoneme = "h", gopScore = 0.9, colorTag = "green")
            )
        )

        coEvery {
            pronunciationAssessor.recordToAssessment(
                recordId = 1L,
                sentenceText = "Hello world",
                audioFilePath = "/tmp/audio.wav"
            )
        } returns assessment

        coEvery { pronunciationRepository.insert(assessment) } returns 42L

        val result = useCase(
            recordId = 1L,
            sentenceText = "Hello world",
            audioFilePath = "/tmp/audio.wav"
        )

        assertTrue(result.isSuccess)
        val saved = result.getOrNull()
        assertNotNull(saved)
        assertEquals("应返回包含数据库ID的record", 42L, saved!!.id)
        assertEquals(95, saved.overallScore)
        assertEquals(1, saved.phonemeScores.size)
    }

    @Test
    fun `测评异常时返回失败结果`() = runBlocking {
        coEvery {
            pronunciationAssessor.recordToAssessment(any(), any(), any())
        } throws RuntimeException("ASR服务不可用")

        val result = useCase(
            recordId = 1L,
            sentenceText = "Hello",
            audioFilePath = "/tmp/bad.wav"
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `插入失败时抛出异常并返回失败`() = runBlocking {
        val assessment = AssessmentRecord(
            sentenceText = "Test",
            overallScore = 80,
            scoreLevel = "良好",
            assessmentType = "shadowing"
        )

        coEvery {
            pronunciationAssessor.recordToAssessment(any(), any(), any())
        } returns assessment

        coEvery { pronunciationRepository.insert(any()) } throws RuntimeException("数据库写入失败")

        val result = useCase(
            recordId = 1L,
            sentenceText = "Test",
            audioFilePath = "/tmp/test.wav"
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `成功测评返回的phonemeScores完整`() = runBlocking {
        val phonemeScores = listOf(
            PhonemeScore(phoneme = "θ", gopScore = 0.3, colorTag = "red", correctionHint = "舌尖需伸出"),
            PhonemeScore(phoneme = "æ", gopScore = 0.7, colorTag = "yellow"),
            PhonemeScore(phoneme = "t", gopScore = 0.95, colorTag = "green")
        )

        val assessment = AssessmentRecord(
            sentenceText = "That",
            overallScore = 70,
            scoreLevel = "一般",
            assessmentType = "shadowing",
            phonemeScores = phonemeScores
        )

        coEvery {
            pronunciationAssessor.recordToAssessment(recordId = 1L, sentenceText = "That", audioFilePath = "/tmp/th.wav")
        } returns assessment

        coEvery { pronunciationRepository.insert(any()) } returns 100L

        val result = useCase(recordId = 1L, sentenceText = "That", audioFilePath = "/tmp/th.wav")

        assertTrue(result.isSuccess)
        val saved = result.getOrNull()
        assertNotNull(saved)
        assertEquals(3, saved!!.phonemeScores.size)
        assertEquals("θ", saved.phonemeScores[0].phoneme)
        assertEquals("舌尖需伸出", saved.phonemeScores[0].correctionHint)
        assertEquals(0.3, saved.phonemeScores[0].gopScore, 0.001)
    }

    @Test
    fun `空音素分数列表时也能保存`() = runBlocking {
        val assessment = AssessmentRecord(
            sentenceText = "Simple",
            overallScore = 95,
            scoreLevel = "优秀",
            assessmentType = "shadowing",
            phonemeScores = emptyList()
        )

        coEvery {
            pronunciationAssessor.recordToAssessment(any(), any(), any())
        } returns assessment

        coEvery { pronunciationRepository.insert(any()) } returns 200L

        val result = useCase(recordId = 2L, sentenceText = "Simple", audioFilePath = "/tmp/simple.wav")

        assertTrue(result.isSuccess)
        val saved = result.getOrNull()
        assertNotNull(saved)
        assertTrue(saved!!.phonemeScores.isEmpty())
    }
}
