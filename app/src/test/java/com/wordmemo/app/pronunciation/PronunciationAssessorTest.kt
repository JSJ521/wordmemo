package com.wordmemo.app.pronunciation

import com.wordmemo.app.domain.pronunciation.service.PronunciationAssessor
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

/**
 * PronunciationAssessor 单元测试 — 验证发音测评打分逻辑。
 */
class PronunciationAssessorTest {

    private val assessor = PronunciationAssessor()

    @Test
    fun `完全匹配返回95分`() = runBlocking {
        val assessment = assessor.assessPronunciation(
            sentenceText = "Hello world",
            targetText = "Hello world",
            recordedText = "Hello world"
        )

        assertEquals(95, assessment.overallScore)
        assertEquals("优秀", assessment.scoreLevel)
        assertEquals(95, assessment.accuracyScore)
    }

    @Test
    fun `大小写差异视为匹配`() = runBlocking {
        val assessment = assessor.assessPronunciation(
            sentenceText = "hello world",
            targetText = "Hello World",
            recordedText = "hello world"
        )

        assertEquals("大小写差异不应影响评分", 95, assessment.overallScore)
        assertEquals("优秀", assessment.scoreLevel)
    }

    @Test
    fun `不匹配返回75分`() = runBlocking {
        val assessment = assessor.assessPronunciation(
            sentenceText = "Hello world",
            targetText = "Hello world",
            recordedText = "Hallo wereld"
        )

        assertEquals(75, assessment.overallScore)
        assertEquals("良好", assessment.scoreLevel)
    }

    @Test
    fun `完全不匹配返回75分`() = runBlocking {
        val assessment = assessor.assessPronunciation(
            sentenceText = "The quick brown fox",
            targetText = "The quick brown fox",
            recordedText = "Something completely different"
        )

        assertEquals(75, assessment.overallScore)
    }

    @Test
    fun `transcribeAudio 返回占位结果`() = runBlocking {
        val result = assessor.transcribeAudio("/tmp/audio.wav", "en")

        assertNotNull(result)
        assertEquals("en", result.language)
        assertEquals("", result.text)
        assertEquals(0f, result.confidence, 0.001f)
    }

    @Test
    fun `recordToAssessment 整合转录和测评`() = runBlocking {
        val record = assessor.recordToAssessment(
            recordId = 1L,
            sentenceText = "Hello world",
            audioFilePath = "/tmp/audio.wav"
        )

        assertNotNull(record)
        assertEquals(1L, record.recordId)
        assertEquals("Hello world", record.sentenceText)
        assertTrue("评分应为95或75", record.overallScore in listOf(95, 75))
        assertEquals("shadowing", record.assessmentType)
    }

    @Test
    fun `高分时fluencyScore为85`() = runBlocking {
        val assessment = assessor.assessPronunciation(
            sentenceText = "Perfect match",
            targetText = "Perfect match",
            recordedText = "Perfect match"
        )

        assertEquals(95, assessment.overallScore)
        assertTrue("高分时fluency大于80", assessment.fluencyScore >= 80)
    }

    @Test
    fun `低分时completenessScore降低`() = runBlocking {
        val assessment = assessor.assessPronunciation(
            sentenceText = "Different text",
            targetText = "Different text",
            recordedText = "Wrong text"
        )

        assertEquals(75, assessment.overallScore)
        assertEquals("低分时completeness为70", 70, assessment.completenessScore)
    }
}
