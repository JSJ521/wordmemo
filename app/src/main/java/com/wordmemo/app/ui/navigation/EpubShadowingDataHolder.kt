package com.wordmemo.app.ui.navigation

import com.wordmemo.app.domain.shadowing.model.ShadowingSentence

/**
 * 跨页面数据持有者 — 用于在 ReadingScreen 和 EpubShadowingScreen 之间传递复杂对象。
 *
 * Navigation Compose 不支持序列化复杂对象作为参数，
 * 使用单例持有者是常见方案（类似 SavedStateHandle 的替代）。
 */
object EpubShadowingDataHolder {
    var sentences: List<ShadowingSentence>? = null
    var bookTitle: String = ""
}
