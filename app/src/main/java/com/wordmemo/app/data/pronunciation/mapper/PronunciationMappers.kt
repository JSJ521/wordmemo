package com.wordmemo.app.data.pronunciation.mapper

import com.wordmemo.app.data.pronunciation.entity.AssessmentRecordEntity
import com.wordmemo.app.data.pronunciation.entity.PhonemeScoreEntity
import com.wordmemo.app.domain.pronunciation.model.AssessmentRecord
import com.wordmemo.app.domain.pronunciation.model.PhonemeScore

fun AssessmentRecordEntity.toDomain(phonemeScores: List<PhonemeScore> = emptyList()): AssessmentRecord =
    AssessmentRecord(
        id = id,
        recordId = recordId,
        sentenceId = sentenceId,
        sentenceText = sentenceText,
        transcribedText = transcribedText,
        overallScore = overallScore,
        scoreLevel = scoreLevel,
        diagnosticReport = diagnosticReport,
        correctionSuggestions = correctionSuggestions,
        assessmentType = assessmentType,
        createdAt = createdAt,
        phonemeScores = phonemeScores
    )

fun AssessmentRecord.toEntity(): AssessmentRecordEntity = AssessmentRecordEntity(
    id = id,
    recordId = recordId,
    sentenceId = sentenceId,
    sentenceText = sentenceText,
    transcribedText = transcribedText,
    overallScore = overallScore,
    scoreLevel = scoreLevel,
    diagnosticReport = diagnosticReport,
    correctionSuggestions = correctionSuggestions,
    assessmentType = assessmentType,
    createdAt = createdAt
)

fun PhonemeScoreEntity.toDomain(): PhonemeScore = PhonemeScore(
    id = id,
    assessmentId = assessmentId,
    phoneme = phoneme,
    positionInWord = positionInWord,
    wordIndex = wordIndex,
    gopScore = gopScore,
    colorTag = colorTag,
    correctionHint = correctionHint
)

fun PhonemeScore.toEntity(): PhonemeScoreEntity = PhonemeScoreEntity(
    id = id,
    assessmentId = assessmentId,
    phoneme = phoneme,
    positionInWord = positionInWord,
    wordIndex = wordIndex,
    gopScore = gopScore,
    colorTag = colorTag,
    correctionHint = correctionHint
)
