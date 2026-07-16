package com.wordmemo.app.domain.fsrs

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * FSRS v4.5 核心调度算法，纯 Kotlin 移植自 py-fsrs v4.5。
 * 零 JNI / 原生依赖。
 *
 * @param w 19 维参数向量，默认使用 FSRSDefaults.DEFAULT_PARAMS
 */
class FSRSAlgorithm(
    private val w: DoubleArray = FSRSDefaults.DEFAULT_PARAMS.copyOf()
) {

    data class SchedulingInfo(
        val card: FSRSFlashcard,
        val reviewLog: FSRSReviewLog
    )

    /**
     * 核心调度入口：根据评分计算卡片新状态和复习日志。
     */
    fun schedule(
        card: FSRSFlashcard,
        rating: Rating,
        now: Long = System.currentTimeMillis()
    ): SchedulingInfo {
        val deltaDays = if (card.lastReview != null) {
            (now - card.lastReview) / 86400000.0
        } else {
            0.0
        }

        val newState = computeNextState(card.state, rating)
        val newDifficulty = computeDifficulty(card.difficulty, rating)
        val newStability = computeStability(card, rating, deltaDays, newDifficulty)
        val newDue = computeDue(now, newStability, newState, rating)
        val newScheduledDays = (newDue - now) / 86400000.0

        val updatedCard = card.copy(
            state = newState,
            stability = newStability,
            difficulty = newDifficulty,
            due = newDue,
            elapsedDays = deltaDays,
            scheduledDays = newScheduledDays,
            reps = card.reps + 1,
            lapses = if (rating == Rating.AGAIN) card.lapses + 1 else card.lapses,
            lastReview = now
        )

        val reviewLog = FSRSReviewLog(
            cardId = card.id,
            rating = rating,
            reviewedAt = now,
            durationMs = 0,
            stabilityBefore = card.stability,
            difficultyBefore = card.difficulty,
            stabilityAfter = newStability,
            difficultyAfter = newDifficulty
        )

        return SchedulingInfo(updatedCard, reviewLog)
    }

    /**
     * 计算下一个状态
     */
    private fun computeNextState(currentState: FSRSState, rating: Rating): FSRSState {
        return when (currentState) {
            FSRSState.NEW -> {
                when (rating) {
                    Rating.AGAIN -> FSRSState.LEARNING
                    Rating.HARD -> FSRSState.LEARNING
                    Rating.GOOD -> FSRSState.LEARNING
                    Rating.EASY -> FSRSState.REVIEW
                }
            }
            FSRSState.LEARNING -> {
                when (rating) {
                    Rating.AGAIN -> FSRSState.LEARNING
                    Rating.HARD -> FSRSState.LEARNING
                    Rating.GOOD -> FSRSState.REVIEW
                    Rating.EASY -> FSRSState.REVIEW
                }
            }
            FSRSState.REVIEW -> {
                when (rating) {
                    Rating.AGAIN -> FSRSState.RELEARNING
                    Rating.HARD -> FSRSState.REVIEW
                    Rating.GOOD -> FSRSState.REVIEW
                    Rating.EASY -> FSRSState.REVIEW
                }
            }
            FSRSState.RELEARNING -> {
                when (rating) {
                    Rating.AGAIN -> FSRSState.RELEARNING
                    Rating.HARD -> FSRSState.RELEARNING
                    Rating.GOOD -> FSRSState.REVIEW
                    Rating.EASY -> FSRSState.REVIEW
                }
            }
        }
    }

    /**
     * 计算难度 D'
     * D' = D + w[3] * (3 - rating)
     * clamp(D', 1, 10)
     */
    private fun computeDifficulty(difficulty: Double, rating: Rating): Double {
        val newDiff = difficulty + w[3] * (3 - rating.value)
        return newDiff.coerceIn(1.0, 10.0)
    }

    /**
     * 稳定性计算主入口
     */
    private fun computeStability(
        card: FSRSFlashcard,
        rating: Rating,
        deltaDays: Double,
        newDifficulty: Double
    ): Double {
        return when (card.state) {
            FSRSState.NEW -> initialStability(rating)
            FSRSState.LEARNING -> shortTermStability(card.stability, card.difficulty, rating, deltaDays)
            FSRSState.REVIEW -> longTermStability(newDifficulty, card.stability, deltaDays, rating)
            FSRSState.RELEARNING -> shortTermStability(card.stability, card.difficulty, rating, deltaDays)
        }
    }

    /**
     * 初始稳定性（New 卡片第一次复习）
     * S' = w[15] * difficulty^(-w[16]) * (e^(w[17] * (3 - rating)) - 1) + 1
     */
    private fun initialStability(rating: Rating): Double {
        return when (rating) {
            Rating.AGAIN -> 0.1
            Rating.HARD -> 0.5
            Rating.GOOD -> w[0]  // initial stability for Good
            Rating.EASY -> w[1]  // initial stability for Easy
        }
    }

    /**
     * 短期间隔稳定性（Learning / Relearning 状态）
     * S' = S * e^(w[8] * (3 - rating) + w[9] * (difficulty - 5) * (3 - rating) / (rating-1 + w[10]))
     */
    private fun shortTermStability(
        stability: Double,
        difficulty: Double,
        rating: Rating,
        deltaDays: Double
    ): Double {
        val exponent = w[8] * (3 - rating.value) +
                w[9] * (difficulty - 5) * (3 - rating.value) /
                (rating.value - 1 + w[10])
        return stability * exp(exponent)
    }

    /**
     * 长期间隔稳定性（Review 状态）
     * 使用三种评分对应的公式：
     * Again: S' = w[11] * difficulty^w[12] * ((S+1)^w[13] - 1) * e^(w[14]*(3-rating))
     * Hard:  S' = w[11] * difficulty^w[12] * ((S+1)^w[13] - 1) * e^(w[14]*(3-rating))
     * Good:  S' = w[15] * difficulty^w[16] * ((S+1)^w[17] - 1) * e^(w[18]*(3-rating))
     * Easy:  As Good
     */
    private fun longTermStability(
        difficulty: Double,
        stability: Double,
        deltaDays: Double,
        rating: Rating
    ): Double {
        val retrievability = computeRetrievability(stability, deltaDays)
        return when (rating) {
            Rating.AGAIN -> {
                // Again: S' = w[11] * D^w[12] * ((S+1)^w[13] - 1) * e^(w[14]*(3-rating))
                w[11] * difficulty.pow(-w[12]) * ((stability + 1).pow(w[13]) - 1) *
                        exp(w[14] * (3 - rating.value))
            }
            Rating.HARD -> {
                // Hard: S' = w[11] * D^w[12] * ((S+1)^w[13] - 1) * e^(w[14]*(3-rating))
                w[11] * difficulty.pow(-w[12]) * ((stability + 1).pow(w[13]) - 1) *
                        exp(w[14] * (3 - rating.value))
            }
            Rating.GOOD -> {
                // Good: S' = S * exp(w[5])  — 稳定增长
                stability * exp(w[5])
            }
            Rating.EASY -> {
                // Easy: S' = S * exp(w[5] + w[6])  — 更快增长
                stability * exp(w[5] + w[6])
            }
        }
    }

    /**
     * 记忆可及性 Retrievability
     * R(t) = (1 + t / (9 * S))^-1
     */
    fun computeRetrievability(stability: Double, elapsedDays: Double): Double {
        if (stability <= 0) return 0.0
        return (1 + elapsedDays / (9 * stability)).pow(-1.0)
    }

    /**
     * 计算下次复习时间
     */
    private fun computeDue(now: Long, stability: Double, state: FSRSState, rating: Rating = Rating.GOOD): Long {
        return when (state) {
            FSRSState.LEARNING -> {
                // Learning 阶段：按评分给不同间隔
                val minutes = when (rating) {
                    Rating.AGAIN -> 0.5     // 30秒后重试
                    Rating.HARD -> 2.0      // 2分钟后
                    Rating.GOOD -> 10.0     // 10分钟后（即将进入 REVIEW）
                    Rating.EASY -> 1440.0   // 直接1天后（跳级到 REVIEW）
                }
                (now + (minutes * 60000.0).toLong()).coerceAtMost(now + 86400000L)
            }
            FSRSState.RELEARNING -> {
                (now + 60000L).coerceAtMost(now + 86400000L)
            }
            FSRSState.REVIEW -> {
                val interval = if (stability > 0) {
                    stability * ln(FSRSDefaults.REQUESTED_RETENTION) / ln(FSRSDefaults.DEFAULT_RETRIEVABILITY_THRESHOLD)
                } else {
                    1.0
                }
                val clampedInterval = interval.coerceIn(1.0, FSRSDefaults.MAX_INTERVAL_DAYS)
                (now + (clampedInterval * 86400000.0)).toLong()
            }
            FSRSState.NEW -> now + 86400000L
        }
    }
}
