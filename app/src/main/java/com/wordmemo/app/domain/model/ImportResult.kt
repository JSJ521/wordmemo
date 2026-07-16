package com.wordmemo.app.domain.model

data class ImportResult(
    val totalInput: Int = 0,
    val successCount: Int = 0,
    val errorCount: Int = 0,
    val errors: List<String> = emptyList()
)
