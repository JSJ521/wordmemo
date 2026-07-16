package com.wordmemo.app.domain.model

data class Group(
    val id: Long = 0,
    val name: String,
    val color: String = "#2196F3",
    val createdAt: Long = System.currentTimeMillis()
)
