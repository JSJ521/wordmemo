package com.wordmemo.app.domain.model

data class Flashcard(
    val id: Long = 0,
    val wordId: Long = 0,
    val state: String = "New",
    val stability: Double = 0.0,
    val difficulty: Double = 5.0,
    val due: Long = System.currentTimeMillis(),
    val elapsedDays: Double = 0.0,
    val scheduledDays: Double = 0.0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastReview: Long? = null
)
