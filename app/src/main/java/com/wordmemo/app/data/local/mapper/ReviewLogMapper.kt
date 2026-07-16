package com.wordmemo.app.data.local.mapper

import com.wordmemo.app.data.local.entity.ReviewLogEntity
import com.wordmemo.app.domain.model.ReviewLog

fun ReviewLogEntity.toDomain(): ReviewLog = ReviewLog(
    id = id,
    cardId = cardId,
    rating = rating,
    reviewedAt = reviewedAt,
    durationMs = durationMs,
    stabilityBefore = stabilityBefore,
    difficultyBefore = difficultyBefore,
    stabilityAfter = stabilityAfter,
    difficultyAfter = difficultyAfter
)

fun ReviewLog.toEntity(): ReviewLogEntity = ReviewLogEntity(
    id = id,
    cardId = cardId,
    rating = rating,
    reviewedAt = reviewedAt,
    durationMs = durationMs,
    stabilityBefore = stabilityBefore,
    difficultyBefore = difficultyBefore,
    stabilityAfter = stabilityAfter,
    difficultyAfter = difficultyAfter
)
