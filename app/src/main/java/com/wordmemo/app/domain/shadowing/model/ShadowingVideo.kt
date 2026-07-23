package com.wordmemo.app.domain.shadowing.model

data class ShadowingVideo(
    val id: Long = 0,
    val title: String,
    val sourceType: String,
    val sourceUrl: String? = null,
    val filePath: String,
    val coverPath: String? = null,
    val subtitlePath: String? = null,
    val durationMs: Long,
    val fileSizeBytes: Long? = null,
    val sentenceCount: Int = 0,
    val completedCount: Int = 0,
    val lastPracticeTime: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
