package com.wordmemo.app.data.shadowing.mapper

import com.wordmemo.app.data.shadowing.entity.ShadowingRecordEntity
import com.wordmemo.app.data.shadowing.entity.ShadowingSentenceEntity
import com.wordmemo.app.data.shadowing.entity.ShadowingVideoEntity
import com.wordmemo.app.domain.shadowing.model.ShadowingRecord
import com.wordmemo.app.domain.shadowing.model.ShadowingSentence
import com.wordmemo.app.domain.shadowing.model.ShadowingVideo

fun ShadowingVideoEntity.toDomain(): ShadowingVideo = ShadowingVideo(
    id = id,
    title = title,
    sourceType = sourceType,
    sourceUrl = sourceUrl,
    filePath = filePath,
    coverPath = coverPath,
    durationMs = durationMs,
    fileSizeBytes = fileSizeBytes,
    sentenceCount = sentenceCount,
    completedCount = completedCount,
    lastPracticeTime = lastPracticeTime,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ShadowingVideo.toEntity(): ShadowingVideoEntity = ShadowingVideoEntity(
    id = id,
    title = title,
    sourceType = sourceType,
    sourceUrl = sourceUrl,
    filePath = filePath,
    coverPath = coverPath,
    durationMs = durationMs,
    fileSizeBytes = fileSizeBytes,
    sentenceCount = sentenceCount,
    completedCount = completedCount,
    lastPracticeTime = lastPracticeTime,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ShadowingSentenceEntity.toDomain(): ShadowingSentence = ShadowingSentence(
    id = id,
    videoId = videoId,
    sentenceIndex = sentenceIndex,
    text = text,
    startTimeMs = startTimeMs,
    endTimeMs = endTimeMs,
    isMerged = isMerged,
    createdAt = createdAt
)

fun ShadowingSentence.toEntity(): ShadowingSentenceEntity = ShadowingSentenceEntity(
    id = id,
    videoId = videoId,
    sentenceIndex = sentenceIndex,
    text = text,
    startTimeMs = startTimeMs,
    endTimeMs = endTimeMs,
    isMerged = isMerged,
    createdAt = createdAt
)

fun ShadowingRecordEntity.toDomain(): ShadowingRecord = ShadowingRecord(
    id = id,
    videoId = videoId,
    sentenceId = sentenceId,
    audioFilePath = audioFilePath,
    durationMs = durationMs,
    createdAt = createdAt,
    pronunciationStatus = pronunciationStatus
)

fun ShadowingRecord.toEntity(): ShadowingRecordEntity = ShadowingRecordEntity(
    id = id,
    videoId = videoId,
    sentenceId = sentenceId,
    audioFilePath = audioFilePath,
    durationMs = durationMs,
    createdAt = createdAt,
    pronunciationStatus = pronunciationStatus
)
