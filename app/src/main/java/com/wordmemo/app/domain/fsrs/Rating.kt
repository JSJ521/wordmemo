package com.wordmemo.app.domain.fsrs

/**
 * 评分等级，映射 py-fsrs v4.5 的四种评分。
 */
enum class Rating(val value: Int) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4);

    companion object {
        fun fromValue(v: Int): Rating =
            entries.firstOrNull { it.value == v } ?: GOOD
    }
}
