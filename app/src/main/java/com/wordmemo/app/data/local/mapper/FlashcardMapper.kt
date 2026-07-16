package com.wordmemo.app.data.local.mapper

import com.wordmemo.app.data.local.entity.FlashcardEntity
import com.wordmemo.app.domain.model.Flashcard

fun FlashcardEntity.toDomain(): Flashcard = Flashcard(
    id = id,
    wordId = wordId,
    state = state,
    stability = stability,
    difficulty = difficulty,
    due = due,
    elapsedDays = elapsedDays,
    scheduledDays = scheduledDays,
    reps = reps,
    lapses = lapses,
    lastReview = lastReview
)

fun Flashcard.toEntity(): FlashcardEntity = FlashcardEntity(
    id = id,
    wordId = wordId,
    state = state,
    stability = stability,
    difficulty = difficulty,
    due = due,
    elapsedDays = elapsedDays,
    scheduledDays = scheduledDays,
    reps = reps,
    lapses = lapses,
    lastReview = lastReview
)
