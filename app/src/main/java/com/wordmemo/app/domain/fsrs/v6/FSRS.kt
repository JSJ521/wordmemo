package com.wordmemo.app.domain.fsrs.v6

import kotlin.math.*
import kotlin.random.Random

class FSRS(
    private val requestRetention: Double = 0.9,
    private val params: List<Double> = defaultParams,
    private val isReview: Boolean = false
) {
    companion object {
        val defaultParams = listOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001,
            1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014,
            1.8729, 0.5425, 0.0912, 0.0658, 0.1542
        )
    }

    data class InitState(var difficulty: Double = 0.0, var stability: Double = 0.0)

    private val decay = -params[20]
    private val factor = 0.9.pow(1.0 / decay) - 1

    /** 对一张卡片计算四个评分的结果 */
    fun calculate(card: FSRSFlashCard): List<Grade> {
        val dayMs = 24 * 60 * 60 * 1000L
        var ivlHard = 0; var ivlGood = 0; var ivlEasy = 0
        var durHard = 5 * 60 * 1000L
        var durGood: Long; var durEasy: Long
        var txtHard: String; var txtGood: String; var txtEasy: String

        val stateAgain: InitState; val stateHard: InitState
        val stateGood: InitState; val stateEasy: InitState

        when (card.phase) {
            CardPhase.Added.value -> {
                stateAgain = InitState()
                stateHard = InitState()
                stateGood = InitState()
                stateEasy = InitState()
                ivlEasy = 1
                txtHard = "5m"; txtGood = "10m"; txtEasy = "1d"
                durGood = 10 * 60 * 1000L
                durEasy = ivlEasy * dayMs
            }
            CardPhase.ReLearning.value -> {
                val list = if (card.difficulty == 0.0) {
                    listOf(initState(Rating.Again), initState(Rating.Hard), initState(Rating.Good), initState(Rating.Easy))
                } else {
                    val ld = card.difficulty; val ls = card.stability
                    listOf(
                        InitState(difficulty = nextDifficulty(ld, Rating.Again), stability = nextShortTermStability(ls, Rating.Again)),
                        InitState(difficulty = nextDifficulty(ld, Rating.Hard), stability = nextShortTermStability(ls, Rating.Hard)),
                        InitState(difficulty = nextDifficulty(ld, Rating.Good), stability = nextShortTermStability(ls, Rating.Good)),
                        InitState(difficulty = nextDifficulty(ld, Rating.Easy), stability = nextShortTermStability(ls, Rating.Easy))
                    )
                }
                stateAgain = list[0]; stateHard = list[1]; stateGood = list[2]; stateEasy = list[3]
                ivlGood = nextInterval(stateGood.stability)
                ivlEasy = max(nextInterval(stateEasy.stability), ivlGood + 1)
                txtHard = "10m"
                txtGood = convertDays(ivlGood)
                txtEasy = convertDays(ivlEasy)
                durGood = ivlGood * dayMs
                durEasy = ivlEasy * dayMs
            }
            else -> { // Review
                val interval = card.interval
                val ld = card.difficulty; val ls = card.stability
                val retrievability = forgettingCurve(interval.toDouble(), ls)
                stateAgain = InitState(
                    difficulty = nextDifficulty(ld, Rating.Again),
                    stability = nextForgetStability(ld, ls, retrievability)
                )
                stateHard = InitState(
                    difficulty = nextDifficulty(ld, Rating.Hard),
                    stability = nextRecallStability(ld, ls, retrievability, Rating.Hard)
                )
                stateGood = InitState(
                    difficulty = nextDifficulty(ld, Rating.Good),
                    stability = nextRecallStability(ld, ls, retrievability, Rating.Good)
                )
                stateEasy = InitState(
                    difficulty = nextDifficulty(ld, Rating.Easy),
                    stability = nextRecallStability(ld, ls, retrievability, Rating.Easy)
                )
                ivlHard = nextInterval(stateHard.stability)
                ivlGood = nextInterval(stateGood.stability)
                ivlEasy = nextInterval(stateEasy.stability)
                ivlHard = min(ivlHard, ivlGood)
                ivlGood = min(ivlGood, ivlHard + 1)
                ivlEasy = min(ivlEasy, ivlGood + 1)
                txtHard = convertDays(ivlHard)
                txtGood = convertDays(ivlGood)
                txtEasy = convertDays(ivlEasy)
                durHard = ivlHard * dayMs
                durGood = ivlGood * dayMs
                durEasy = ivlEasy * dayMs
            }
        }

        return listOf(
            Grade(title = "Easy", durationMillis = durEasy, interval = ivlEasy, txt = txtEasy, choice = Rating.Easy, stability = stateEasy.stability, difficulty = stateEasy.difficulty),
            Grade(title = "Good", durationMillis = durGood, interval = ivlGood, txt = txtGood, choice = Rating.Good, stability = stateGood.stability, difficulty = stateGood.difficulty),
            Grade(title = "Hard", durationMillis = durHard, interval = ivlHard, txt = txtHard, choice = Rating.Hard, stability = stateHard.stability, difficulty = stateHard.difficulty),
            Grade(title = "Again", durationMillis = 3 * 60 * 1000L, interval = card.interval, txt = "< 3m", choice = Rating.Again, stability = stateAgain.stability, difficulty = stateAgain.difficulty)
        )
    }

    private fun convertDays(days: Int): String = when {
        days > 365 -> "${days / 365}y"
        days > 30 -> "${days / 30}mo"
        else -> "${days}d"
    }

    private fun applyFuzz(interval: Double, fuzzFactor: Double, scheduledDays: Int = 0): Double {
        if (interval < 2.5) return interval
        val ivl = roundToInt(interval)
        var minIvl = max(2, roundToInt(ivl * 0.95 - 1))
        val maxIvl = roundToInt(ivl * 1.05 + 1)
        if (isReview && ivl > scheduledDays) minIvl = max(minIvl, scheduledDays + 1)
        return floor(fuzzFactor * (maxIvl - minIvl + 1) + minIvl)
    }

    private fun forgettingCurve(interval: Double, stability: Double): Double = exp(-interval / stability)

    private fun generateFuzzFactor(): Double = Random(System.currentTimeMillis()).nextDouble()

    private fun initDifficulty(rating: Rating): Double {
        val base = params[4]; val exponent = params[5] * (rating.value - 1)
        return (base - exp(exponent) + 1).coerceIn(1.0, 10.0)
    }

    private fun initStability(rating: Rating): Double = params.getOrElse(rating.value - 1) { 0.1 }.coerceAtMost(0.1)

    private fun initState(rating: Rating) = InitState(difficulty = initDifficulty(rating), stability = initStability(rating))

    private fun linearDamping(delta: Double, oldD: Double): Double = delta * (10 - oldD) / 9

    private fun meanReversion(initD: Double, nextD: Double): Double = params[7] * initD + (1 - params[7]) * nextD

    private fun nextInterval(stability: Double, maxInterval: Int = 36500, lastInterval: Int = 0): Int {
        val raw = stability / factor * (requestRetention.pow(1 / decay) - 1)
        return roundToInt(applyFuzz(raw, generateFuzzFactor(), lastInterval)).coerceIn(1, maxInterval)
    }

    private fun nextDifficulty(currentD: Double, rating: Rating): Double {
        val deltaD = -params[6] * (rating.value - 3)
        val damped = linearDamping(deltaD, currentD)
        val nextD = currentD + damped
        return meanReversion(initDifficulty(Rating.Easy), nextD).coerceIn(1.0, 10.0)
    }

    private fun nextShortTermStability(currentS: Double, rating: Rating): Double {
        var sinc = exp(params[17] * (rating.value - 3 + params[18])) * currentS.pow(-params[19])
        if (rating.value >= 3) sinc = max(sinc, 1.0)
        return abs(currentS * sinc)
    }

    private fun nextForgetStability(difficulty: Double, stability: Double, retrievability: Double): Double {
        val sMin = stability / exp(params[17] * params[18])
        val result = params[11] * difficulty.pow(-params[12]) * ((stability + 1).pow(params[13]) - 1) * exp((1 - retrievability) * params[14])
        return min(result, sMin)
    }

    private fun nextRecallStability(d: Double, s: Double, r: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.Hard) params[15] else 1.0
        val easyBonus = if (rating == Rating.Easy) params[16] else 1.0
        val factor = exp(params[8]) * (11 - d) * s.pow(-params[9]) * (exp((1 - r) * params[10]) - 1) * hardPenalty * easyBonus
        return s * (1 + factor)
    }

    private fun roundToInt(d: Double): Int = d.toInt()
}
