package com.wordmemo.app.domain.model

data class Word(
    val id: Long = 0,
    val english: String,
    val chinese: String,
    val phonetic: String = "",
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
