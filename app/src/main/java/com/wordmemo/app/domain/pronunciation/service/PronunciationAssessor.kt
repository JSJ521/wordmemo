package com.wordmemo.app.domain.pronunciation.service

import com.wordmemo.app.domain.pronunciation.model.AssessmentRecord
import com.wordmemo.app.domain.pronunciation.model.PhonemeScore
import javax.inject.Inject

data class TranscriptionResult(
    val text: String,
    val language: String = "en",
    val confidence: Float = 0f,
    val wordTimings: List<WordTiming>? = null
)

data class WordTiming(
    val word: String,
    val startTime: Float,
    val endTime: Float,
    val confidence: Float = 0f
)

data class PronunciationAssessment(
    val overallScore: Int,
    val scoreLevel: String,
    val accuracyScore: Int = 0,
    val fluencyScore: Int = 0,
    val completenessScore: Int = 0,
    val transcribedText: String? = null,
    val diagnosticReport: String? = null,
    val correctionSuggestions: String? = null,
    val phonemeScores: List<PhonemeScore> = emptyList()
)

class PronunciationAssessor @Inject constructor() {

    /**
     * v1: DeepSeek API 文本级发音差异分析
     * v2: 本地 whisper.cpp 音素级测评
     */
    suspend fun assessPronunciation(
        sentenceText: String?,
        targetText: String,
        recordedText: String,
        wordTimings: List<WordTiming>? = null
    ): PronunciationAssessment {
        // v1 骨架实现 — 后续接入DeepSeek API
        val overallScore = if (targetText.equals(recordedText, ignoreCase = true)) 95 else 75
        val scoreLevel = when {
            overallScore >= 90 -> "优秀"
            overallScore >= 75 -> "良好"
            overallScore >= 60 -> "一般"
            else -> "需改进"
        }

        return PronunciationAssessment(
            overallScore = overallScore,
            scoreLevel = scoreLevel,
            accuracyScore = overallScore,
            fluencyScore = if (overallScore >= 80) 85 else 65,
            completenessScore = if (overallScore >= 80) 90 else 70,
            transcribedText = recordedText,
            diagnosticReport = null,
            correctionSuggestions = null,
            phonemeScores = emptyList()
        )
    }

    /**
     * v1: DeepSeek API 在线ASR转录
     * v2: 本地 whisper.cpp
     */
    suspend fun transcribeAudio(
        audioFilePath: String,
        language: String = "en"
    ): TranscriptionResult {
        // v1 骨架实现 — 后续接入真实ASR
        return TranscriptionResult(
            text = "",
            language = language,
            confidence = 0f
        )
    }

    suspend fun recordToAssessment(
        recordId: Long,
        sentenceText: String,
        audioFilePath: String
    ): AssessmentRecord {
        // v1 骨架: 先用占位转录+测评
        val transcription = transcribeAudio(audioFilePath)
        val assessment = assessPronunciation(
            sentenceText = sentenceText,
            targetText = sentenceText,
            recordedText = transcription.text.ifEmpty { sentenceText }
        )

        return AssessmentRecord(
            recordId = recordId,
            sentenceText = sentenceText,
            transcribedText = assessment.transcribedText,
            overallScore = assessment.overallScore,
            scoreLevel = assessment.scoreLevel,
            diagnosticReport = assessment.diagnosticReport,
            correctionSuggestions = assessment.correctionSuggestions,
            assessmentType = "shadowing",
            phonemeScores = assessment.phonemeScores
        )
    }
}
