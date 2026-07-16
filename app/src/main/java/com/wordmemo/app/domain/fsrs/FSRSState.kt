package com.wordmemo.app.domain.fsrs

/**
 * FSRS 状态枚举，映射 py-fsrs v4.5 的四种卡片状态。
 */
enum class FSRSState(val value: String) {
    NEW("New"),
    LEARNING("Learning"),
    REVIEW("Review"),
    RELEARNING("Relearning");

    companion object {
        fun fromValue(v: String): FSRSState =
            entries.firstOrNull { it.value == v } ?: NEW
    }
}
