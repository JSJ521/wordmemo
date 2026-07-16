package com.wordmemo.app.domain.model

data class AiMnemonic(
    val id: Long = 0,
    val wordId: Long = 0,
    val method: String, // 谐音/词根/故事/联想
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)
