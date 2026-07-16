package com.wordmemo.app.domain.model

data class AiTranslation(
    val word: String,
    val translation: String,
    val phonetic: String = "",
    val examples: List<String> = emptyList(),
    val usage: String? = null,
    val source: String = "AI 翻译"
)
