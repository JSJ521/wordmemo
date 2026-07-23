package com.wordmemo.app.domain.shadowing.model

data class ShadowingSentence(
    val id: Long = 0,
    val videoId: Long,
    val sentenceIndex: Int,
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val isMerged: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
