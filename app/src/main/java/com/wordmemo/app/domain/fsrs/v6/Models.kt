package com.wordmemo.app.domain.fsrs.v6

enum class Rating(val value: Int) { Again(1), Hard(2), Good(3), Easy(4) }

enum class CardPhase(val value: Int) { Added(0), ReLearning(1), Review(2) }

data class Grade(
    val title: String = "",
    val durationMillis: Long = 0,
    val interval: Int = 0,
    val txt: String = "0",
    val choice: Rating = Rating.Good,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0
)

data class FSRSFlashCard(
    val id: Long = 0,
    var stability: Double = 2.5,
    var difficulty: Double = 2.5,
    var interval: Int = 0,
    var dueDate: Long = System.currentTimeMillis(),
    var reviewCount: Int = 0,
    var lastReview: Long = System.currentTimeMillis(),
    var phase: Int = 0
)
