package com.wordmemo.app.domain.fsrs

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * FSRS 参数优化器。
 * 基于用户的复习历史，通过随机搜索找到损失最小的 w[] 参数组合。
 *
 * 优化目标：最小化预测保留率与实际回忆之间的对数损失。
 */
class FSRSOptimizer {

    data class ReviewData(
        val elapsedDays: Double,     // 距上次复习的天数
        val stability: Double,        // 复习时的稳定度
        val rating: Int,              // 1=Again 2=Hard 3=Good 4=Easy
        val difficulty: Double        // 复习时的难度
    )

    /**
     * 计算预测保留率
     * R(t) = (1 + t / (9*S))^(-1)
     */
    fun predictedRetention(elapsedDays: Double, stability: Double): Double {
        if (stability <= 0) return 0.0
        return (1.0 + elapsedDays / (9.0 * stability)).pow(-1.0)
    }

    /**
     * 计算对数损失
     * loss = -(actual * ln(predicted) + (1-actual) * ln(1-predicted))
     * actual = 1 if rating >= 3 (Good/Easy), 0 if rating < 3 (Again/Hard)
     */
    fun logLoss(reviews: List<ReviewData>, w: List<Double>): Double {
        if (reviews.isEmpty() || w.size < 19) return Double.MAX_VALUE
        var totalLoss = 0.0
        var count = 0

        for (review in reviews) {
            val actualRecall = if (review.rating >= 3) 1.0 else 0.0
            val predictedRecall = predictedRetention(review.elapsedDays, review.stability)
            if (predictedRecall <= 0.0 || predictedRecall >= 1.0) continue

            val loss = -(actualRecall * ln(predictedRecall) + (1.0 - actualRecall) * ln(1.0 - predictedRecall))
            totalLoss += loss
            count++
        }

        return if (count == 0) Double.MAX_VALUE else totalLoss / count
    }

    /**
     * 执行参数优化
     * @param reviews 复习日志数据
     * @param initialW 初始 w[] 参数（若空则用 FSRSDefaults）
     * @param iterations 优化迭代次数
     * @return 优化后的 w[] 参数
     */
    fun optimize(
        reviews: List<ReviewData>,
        initialW: List<Double> = FSRSDefaults.DEFAULT_PARAMS.toList(),
        iterations: Int = 500
    ): List<Double> {
        if (reviews.size < 5) return initialW

        var bestW = initialW.toMutableList()
        var bestLoss = logLoss(reviews, bestW)
        val rng = java.util.Random(42)

        for (i in 0 until iterations) {
            val candidate = bestW.toMutableList()
            // 随机扰动 3-5 个参数
            val numChanges = rng.nextInt(3) + 3
            repeat(numChanges) {
                val idx = rng.nextInt(candidate.size)
                // 扰动幅度：随机 ±5%~±20%
                val perturbation = 1.0 + (rng.nextDouble() - 0.5) * 0.3
                candidate[idx] = (candidate[idx] * perturbation).coerceIn(0.01, 100.0)
            }

            val loss = logLoss(reviews, candidate)
            if (loss < bestLoss) {
                bestLoss = loss
                bestW = candidate
            }
        }

        return bestW
    }
}
