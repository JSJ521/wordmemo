package com.wordmemo.app.data.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TTS 播放器 — 逐句播报 + 句级高亮同步。
 *
 * 核心设计：
 * - 按句分块 QUEUE_ADD 队列
 * - UtteranceProgressListener 回调驱动索引推进
 * - stop() 替代 pause/resume（Android TTS 无 pause API）
 * - 支持调速 setSpeechRate（0.25x - 2.0x）
 *
 * 陷阱防范（参见 android-tts-language-learning 技能）：
 * 1. utteranceId 必须非空，否则 onDone 不触发
 * 2. 长文本按句分块避免截断（~4000 字符限制）
 * 3. 弃用 setOnUtteranceCompletedListener，使用 UtteranceProgressListener
 * 4. 放弃逐词高亮 onRangeStart（仅 Google 引擎有效）
 *
 * @param context Application context
 */
@Singleton
class SentenceTTS @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // ==================== 状态 ====================

    data class TtsState(
        val isInitialized: Boolean = false,
        val isSpeaking: Boolean = false,
        val currentSentenceIndex: Int = -1,
        val totalSentences: Int = 0,
        val speed: Float = 1.0f,
        val error: String? = null,
        // 当前高亮句子的文本
        val highlightedText: String = "",
        // 当前章节的所有句子文本（用于高亮显示）
        val sentences: List<String> = emptyList()
    )

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    // 事件通道（供 UI 层观察——如自动翻页、切换句子等）
    sealed class TtsEvent {
        data class SentenceCompleted(val index: Int) : TtsEvent()
        data object AllSentencesCompleted : TtsEvent()
        data class SentenceStarted(val index: Int, val text: String) : TtsEvent()
        data object EngineError : TtsEvent()
    }

    private val _events = Channel<TtsEvent>(Channel.BUFFERED)
    val events: Channel<TtsEvent> = _events

    // ==================== TTS 引擎 ====================

    private var tts: TextToSpeech? = null
    private var engineReady = false

    // 当前播放队列
    private var pendingSentences = listOf<String>()
    private var currentIndex = 0
    private var isPlaying = false

    /**
     * 初始化 TTS 引擎。
     * 必须在主线程调用（TextToSpeech 构造函数要求）。
     */
    fun initialize() {
        if (tts != null) return

        tts = TextToSpeech(context) { status ->
            engineReady = (status == TextToSpeech.SUCCESS)
            if (engineReady) {
                tts?.language = Locale.US
                _state.value = _state.value.copy(isInitialized = true)
            } else {
                _state.value = _state.value.copy(error = "TTS 引擎初始化失败")
            }
        }

        // 设置进度监听
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val idx = utteranceId?.toIntOrNull() ?: return
                if (idx in pendingSentences.indices) {
                    _state.value = _state.value.copy(
                        isSpeaking = true,
                        currentSentenceIndex = idx,
                        highlightedText = pendingSentences[idx]
                    )
                    _events.trySend(TtsEvent.SentenceStarted(idx, pendingSentences[idx]))
                }
            }

            override fun onDone(utteranceId: String?) {
                val idx = utteranceId?.toIntOrNull() ?: return
                _events.trySend(TtsEvent.SentenceCompleted(idx))

                // 自动推进到下一句
                val nextIdx = idx + 1
                if (!isPlaying) return // 已被停止
                if (nextIdx < pendingSentences.size) {
                    speakSentence(nextIdx)
                } else {
                    // 全部完成
                    isPlaying = false
                    _state.value = _state.value.copy(
                        isSpeaking = false,
                        currentSentenceIndex = -1,
                        highlightedText = ""
                    )
                    _events.trySend(TtsEvent.AllSentencesCompleted)
                }
            }

            override fun onError(utteranceId: String?) {
                _state.value = _state.value.copy(error = "TTS 播放出错")
                _events.trySend(TtsEvent.EngineError)
            }
        })
    }

    /**
     * 播放指定章节的所有句子。
     */
    fun playSentences(sentences: List<String>, startIndex: Int = 0, speed: Float = 1.0f) {
        if (!engineReady || tts == null) {
            _state.value = _state.value.copy(error = "TTS 引擎未就绪")
            return
        }

        stop()

        pendingSentences = sentences
        currentIndex = startIndex
        isPlaying = true

        _state.value = _state.value.copy(
            totalSentences = sentences.size,
            speed = speed,
            sentences = sentences,
            isSpeaking = true,
            error = null
        )

        tts?.setSpeechRate(speed)

        // 从 startIndex 开始播放
        speakSentence(startIndex)
    }

    /**
     * 播放下一条句子。
     */
    fun playNextSentence() {
        val next = currentIndex + 1
        if (next < pendingSentences.size) {
            stop()
            currentIndex = next
            isPlaying = true
            _state.value = _state.value.copy(isSpeaking = true)
            speakSentence(next)
        }
    }

    /**
     * 播放上一条句子。
     */
    fun playPreviousSentence() {
        val prev = (currentIndex - 1).coerceAtLeast(0)
        if (prev != currentIndex) {
            stop()
            currentIndex = prev
            isPlaying = true
            _state.value = _state.value.copy(isSpeaking = true)
            speakSentence(prev)
        }
    }

    /**
     * 跳到指定句子播放。
     */
    fun jumpToSentence(index: Int) {
        if (index in pendingSentences.indices) {
            stop()
            currentIndex = index
            isPlaying = true
            _state.value = _state.value.copy(isSpeaking = true)
            speakSentence(index)
        }
    }

    /**
     * 暂停（实际是 stop + 记住位置）。
     */
    fun pause() {
        if (!isPlaying) return
        isPlaying = false
        tts?.stop()
        _state.value = _state.value.copy(
            isSpeaking = false,
            highlightedText = ""
        )
    }

    /**
     * 恢复（从暂停位置继续）。
     */
    fun resume() {
        if (isPlaying || !engineReady) return
        val idx = _state.value.currentSentenceIndex.takeIf { it >= 0 } ?: 0
        isPlaying = true
        _state.value = _state.value.copy(isSpeaking = true)
        speakSentence(idx)
    }

    /**
     * 停止播放并重置队列。
     */
    fun stop() {
        isPlaying = false
        tts?.stop()
        _state.value = _state.value.copy(
            isSpeaking = false,
            highlightedText = ""
        )
    }

    /**
     * 设置播放速度。
     * @param speed 0.25f ~ 2.0f
     */
    fun setSpeed(speed: Float) {
        _state.value = _state.value.copy(speed = speed)
        tts?.setSpeechRate(speed)
        // 如果正在播放，当前句子的速度不会立即变化，下一句生效
    }

    /**
     * 释放 TTS 引擎。
     */
    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        engineReady = false
        _state.value = _state.value.copy(isInitialized = false)
    }

    // ==================== 内部方法 ====================

    /**
     * 播放指定索引的句子。
     * utteranceId = 索引字符串（确保 onDone 回调触发）
     */
    private fun speakSentence(index: Int) {
        if (!engineReady || tts == null) return
        if (index !in pendingSentences.indices) return

        currentIndex = index
        val text = pendingSentences[index]

        _state.value = _state.value.copy(
            currentSentenceIndex = index,
            highlightedText = text
        )

        // 使用 QUEUE_FLUSH 清空队列，确保立即切换
        val result = tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,            // params — 可以为 null
            index.toString()  // utteranceId — 必须非空！
        )

        if (result == TextToSpeech.ERROR) {
            _state.value = _state.value.copy(error = "TTS speak() 失败")
        }
    }

    /**
     * 检查当前引擎是否可用。
     */
    fun isAvailable(): Boolean = engineReady
}
