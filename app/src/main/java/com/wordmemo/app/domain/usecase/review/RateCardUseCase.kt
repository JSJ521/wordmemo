package com.wordmemo.app.domain.usecase.review

import com.wordmemo.app.domain.fsrs.FSRSAlgorithm
import com.wordmemo.app.domain.fsrs.FSRSOptimizer
import com.wordmemo.app.domain.fsrs.Rating
import com.wordmemo.app.domain.model.Flashcard
import com.wordmemo.app.domain.model.ReviewLog
import com.wordmemo.app.domain.repository.ReviewRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class RateCardResult(
    val updatedCard: Flashcard,
    val reviewLog: ReviewLog
)

@Singleton
class RateCardUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository,
    private val fsrsAlgorithm: FSRSAlgorithm,
    private val fsrsOptimizer: FSRSOptimizer,
    private val checkDailyLimit: CheckDailyLimitUseCase
) {
    private val optimizeScope = CoroutineScope(Dispatchers.Default)

    suspend operator fun invoke(
        card: Flashcard,
        rating: Int,
        now: Long = System.currentTimeMillis()
    ): RateCardResult {
        val fsrsRating = Rating.fromValue(rating)
        val fsrsCard = card.toFSRSFlashcard()

        val schedulingInfo = fsrsAlgorithm.schedule(fsrsCard, fsrsRating, now)
        val updatedCard = schedulingInfo.card.toFlashcard()
        val reviewLog = schedulingInfo.reviewLog.toReviewLog()

        reviewRepository.saveCard(updatedCard)
        reviewRepository.saveReviewLog(reviewLog)

        // 异步触发参数优化（如果达阈值）
        optimizeScope.launch {
            val recentLogs = reviewRepository.getRecentLogs(
                minCount = checkDailyLimit.getOptimizeThreshold()
            )
            // 手动优化由设置页完成，这里不再自动触发
        }

        return RateCardResult(updatedCard, reviewLog)
    }
}

// ── 扩展函数：域模型 ↔ FSRS 模型互转 ──

fun Flashcard.toFSRSFlashcard(): com.wordmemo.app.domain.fsrs.FSRSFlashcard {
    return com.wordmemo.app.domain.fsrs.FSRSFlashcard(
        id = id,
        wordId = wordId,
        state = com.wordmemo.app.domain.fsrs.FSRSState.fromValue(state),
        stability = stability,
        difficulty = difficulty,
        due = due,
        elapsedDays = elapsedDays,
        scheduledDays = scheduledDays,
        reps = reps,
        lapses = lapses,
        lastReview = lastReview
    )
}

fun com.wordmemo.app.domain.fsrs.FSRSFlashcard.toFlashcard(): Flashcard {
    return Flashcard(
        id = id,
        wordId = wordId,
        state = state.value,
        stability = stability,
        difficulty = difficulty,
        due = due,
        elapsedDays = elapsedDays,
        scheduledDays = scheduledDays,
        reps = reps,
        lapses = lapses,
        lastReview = lastReview
    )
}

fun ReviewLog.toFSRSReviewLog(): com.wordmemo.app.domain.fsrs.FSRSReviewLog {
    return com.wordmemo.app.domain.fsrs.FSRSReviewLog(
        id = id,
        cardId = cardId,
        rating = Rating.fromValue(rating),
        reviewedAt = reviewedAt,
        durationMs = durationMs,
        stabilityBefore = stabilityBefore,
        difficultyBefore = difficultyBefore,
        stabilityAfter = stabilityAfter,
        difficultyAfter = difficultyAfter
    )
}

fun com.wordmemo.app.domain.fsrs.FSRSReviewLog.toReviewLog(): ReviewLog {
    return ReviewLog(
        id = id,
        cardId = cardId,
        rating = rating.value,
        reviewedAt = reviewedAt,
        durationMs = durationMs,
        stabilityBefore = stabilityBefore,
        difficultyBefore = difficultyBefore,
        stabilityAfter = stabilityAfter,
        difficultyAfter = difficultyAfter
    )
}
