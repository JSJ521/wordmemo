package com.wordmemo.app.domain.fsrs

/**
 * FSRS v4.5 默认参数，移植自 py-fsrs v4.5。
 * 19 维向量 w[0] ~ w[18]。
 */
object FSRSDefaults {
    /** 19 维默认参数向量 */
    val DEFAULT_PARAMS = doubleArrayOf(
        0.40255,   // w[0]  - initial stability for Good
        1.18385,   // w[1]  - initial stability for Easy
        3.173,     // w[2]  - initial difficulty for Good
        15.69105,  // w[3]  - difficulty delta
        7.1949,    // w[4]  - mean repropagation stability
        0.5345,    // w[5]  -
        1.4604,    // w[6]  -
        0.0046,    // w[7]  -
        1.54575,   // w[8]  - stability short term rating factor
        0.1192,    // w[9]  - stability short term difficulty factor
        1.01925,   // w[10] -
        1.9395,    // w[11] -
        0.11,      // w[12] - initial stability step
        0.29605,   // w[13] -
        2.2698,    // w[14] -
        0.2315,    // w[15] -
        2.9898,    // w[16] -
        0.51655,   // w[17] -
        0.6621     // w[18] -
    )

    const val DEFAULT_RETRIEVABILITY_THRESHOLD = 0.9
    const val DEFAULT_DAILY_REVIEW_LIMIT = 13
    const val DEFAULT_OPTIMIZE_THRESHOLD = 20

    /** 默认请求保留率（requestedRetention） */
    const val REQUESTED_RETENTION = 0.9

    /** 最大间隔天数 */
    const val MAX_INTERVAL_DAYS = 365.0

    /** 最小间隔天数 */
    const val MIN_INTERVAL_DAYS = 0.0
}
