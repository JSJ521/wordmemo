package com.wordmemo.app.domain.shadowing.model

data class ShadowingRecord(
    val id: Long = 0,
    val videoId: Long,
    val sentenceId: Long,
    val audioFilePath: String,
    val durationMs: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val pronunciationStatus: Int = 0
)
