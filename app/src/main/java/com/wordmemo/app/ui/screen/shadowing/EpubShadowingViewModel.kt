package com.wordmemo.app.ui.screen.shadowing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.data.shadowing.service.AudioWaveformExtractor
import com.wordmemo.app.data.shadowing.service.SentenceAudioRecorder
import com.wordmemo.app.data.tts.SentenceTTS
import com.wordmemo.app.domain.shadowing.model.ShadowingSentence
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * EPUB 跟读 ViewModel。
 *
 * 与 ShadowingSessionViewModel 的区别：
 * - 不加载视频，直接接收 ShadowingSentence 列表
 * - 使用 SentenceTTS 播放原声（非 ExoPlayer）
 * - 保留录音/回放逻辑
 */
@HiltViewModel
class EpubShadowingViewModel @Inject constructor(
    private val sentenceTTS: SentenceTTS,
    private val sentenceAudioRecorder: SentenceAudioRecorder,
    private val audioWaveformExtractor: AudioWaveformExtractor,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EpubShadowingUiState())
    val uiState: StateFlow<EpubShadowingUiState> = _uiState.asStateFlow()

    /** 暴露 TTS 事件给 Screen */
    val ttsEvents get() = sentenceTTS.events

    /** 录音回放用 MediaPlayer */
    private var recordingPlayer: MediaPlayer? = null

    init {
        checkMicrophonePermission()
        sentenceTTS.initialize()
    }

    private fun checkMicrophonePermission() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        _uiState.update { it.copy(hasMicrophonePermission = granted) }
    }

    /**
     * 初始化 — 传入句子列表。
     */
    fun initialize(sentences: List<ShadowingSentence>, bookTitle: String) {
        _uiState.update {
            it.copy(
                sentences = sentences,
                bookTitle = bookTitle,
                currentSentenceIndex = 0,
                isLoading = false
            )
        }
    }

    // ========== TTS Controls ==========

    /**
     * 播放原声（TTS）。
     */
    fun playOriginalSentence() {
        stopRecordingPlayback()
        val state = _uiState.value
        val text = state.sentences.getOrNull(state.currentSentenceIndex)?.text ?: return
        sentenceTTS.playSentences(
            sentences = listOf(text),
            startIndex = 0,
            speed = state.playbackSpeed
        )
        _uiState.update { it.copy(isTtsPlaying = true, activeAudioSource = AudioSource.ORIGINAL) }
    }

    /**
     * 切换 TTS 播放/暂停。
     */
    fun toggleTtsPlayPause() {
        val state = _uiState.value
        if (state.isTtsPlaying) {
            sentenceTTS.pause()
            _uiState.update { it.copy(isTtsPlaying = false) }
        } else {
            // 恢复或重新播放当前句子
            val text = state.sentences.getOrNull(state.currentSentenceIndex)?.text ?: return
            sentenceTTS.playSentences(
                sentences = listOf(text),
                startIndex = 0,
                speed = state.playbackSpeed
            )
            _uiState.update { it.copy(isTtsPlaying = true) }
        }
    }

    /**
     * TTS 句子播放完成 → 自动推进到下一句。
     */
    fun onTtsSentenceCompleted(index: Int) {
        // EPUB 跟读只播放当前句子，不自动推进
        // 这里由用户手动推进
        _uiState.update { it.copy(isTtsPlaying = false) }
    }

    /**
     * TTS 全部完成。
     */
    fun onTtsAllCompleted() {
        _uiState.update { it.copy(isTtsPlaying = false) }
    }

    /**
     * 停止 TTS。
     */
    fun stopTts() {
        sentenceTTS.stop()
        _uiState.update { it.copy(isTtsPlaying = false) }
    }

    /**
     * 设置播放速度。
     */
    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
        sentenceTTS.setSpeed(speed)
    }

    // ========== Sentence Navigation ==========

    fun previousSentence() {
        val current = _uiState.value.currentSentenceIndex
        if (current > 0) {
            stopRecordingPlayback()
            stopTts()
            _uiState.update {
                it.copy(
                    currentSentenceIndex = current - 1,
                    recordingState = RecordingState.IDLE,
                    isRecording = false,
                    hasRecording = false,
                    activeAudioSource = AudioSource.ORIGINAL,
                    recordingDurationMs = 0L,
                    waveformAmplitudes = emptyList(),
                    recordedWaveform = emptyList(),
                    recordingFilePath = null,
                    isPlayingRecording = false
                )
            }
        }
    }

    fun nextSentence() {
        val current = _uiState.value.currentSentenceIndex
        if (current < _uiState.value.sentences.size - 1) {
            stopRecordingPlayback()
            stopTts()
            _uiState.update {
                it.copy(
                    currentSentenceIndex = current + 1,
                    recordingState = RecordingState.IDLE,
                    isRecording = false,
                    hasRecording = false,
                    activeAudioSource = AudioSource.ORIGINAL,
                    recordingDurationMs = 0L,
                    waveformAmplitudes = emptyList(),
                    recordedWaveform = emptyList(),
                    recordingFilePath = null,
                    isPlayingRecording = false
                )
            }
        }
    }

    fun jumpToSentence(index: Int) {
        if (index in _uiState.value.sentences.indices) {
            stopRecordingPlayback()
            stopTts()
            _uiState.update {
                it.copy(
                    currentSentenceIndex = index,
                    recordingState = RecordingState.IDLE,
                    isRecording = false,
                    hasRecording = false,
                    activeAudioSource = AudioSource.ORIGINAL,
                    recordingDurationMs = 0L,
                    waveformAmplitudes = emptyList(),
                    recordedWaveform = emptyList(),
                    recordingFilePath = null,
                    isPlayingRecording = false
                )
            }
        }
    }

    // ========== Recording Controls ==========

    fun startRecording() {
        if (!_uiState.value.hasMicrophonePermission) {
            _uiState.update { it.copy(error = "需要麦克风权限才能录音") }
            return
        }

        val state = _uiState.value
        val sentence = state.sentences.getOrNull(state.currentSentenceIndex) ?: return

        stopTts()
        stopRecordingPlayback()

        viewModelScope.launch {
            try {
                val filePath = sentenceAudioRecorder.startRecording(
                    videoId = -1L,  // EPUB 标记
                    sentenceId = sentence.id
                )

                _uiState.update {
                    it.copy(
                        recordingState = RecordingState.RECORDING,
                        isRecording = true,
                        hasRecording = false,
                        recordingDurationMs = 0L,
                        recordingFilePath = filePath,
                        waveformAmplitudes = List(60) { 0.05f }
                    )
                }

                // 实时振幅轮询
                launch {
                    var elapsed = 0L
                    while (_uiState.value.recordingState == RecordingState.RECORDING) {
                        val amp = sentenceAudioRecorder.getMaxAmplitude()
                        val normalized = if (amp > 0) {
                            (kotlin.math.log10(1.0 + amp.toDouble()) / 4.0).toFloat()
                                .coerceIn(0.05f, 1.0f)
                        } else {
                            0.05f
                        }
                        _uiState.update {
                            it.copy(
                                waveformAmplitudes = it.waveformAmplitudes.drop(1) + normalized,
                                recordingDurationMs = elapsed
                            )
                        }
                        delay(80)
                        elapsed += 80
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "录音启动失败: ${e.localizedMessage}") }
            }
        }
    }

    fun stopRecording() {
        val result = sentenceAudioRecorder.stopRecording()
        if (result == null) {
            _uiState.update { it.copy(error = "停止录音失败") }
            return
        }

        val (filePath, durationMs) = result

        viewModelScope.launch {
            val waveform = audioWaveformExtractor.extractFromFile(filePath)

            _uiState.update {
                it.copy(
                    recordingState = RecordingState.PLAYBACK,
                    isRecording = false,
                    hasRecording = true,
                    activeAudioSource = AudioSource.RECORDING,
                    recordingDurationMs = durationMs,
                    waveformAmplitudes = emptyList(),
                    recordedWaveform = waveform,
                    recordingFilePath = filePath
                )
            }
        }
    }

    fun playRecording() {
        val filePath = _uiState.value.recordingFilePath ?: return
        if (_uiState.value.isPlayingRecording) {
            stopRecordingPlayback()
            return
        }
        playRecordingFile(filePath)
    }

    fun deleteRecording() {
        stopRecordingPlayback()
        val state = _uiState.value
        val sentence = state.sentences.getOrNull(state.currentSentenceIndex)
        if (sentence != null) {
            sentenceAudioRecorder.deleteRecordingForSentence(-1L, sentence.id)
        }
        _uiState.update {
            it.copy(
                recordingState = RecordingState.IDLE,
                hasRecording = false,
                activeAudioSource = AudioSource.ORIGINAL,
                recordingDurationMs = 0L,
                waveformAmplitudes = emptyList(),
                recordedWaveform = emptyList(),
                recordingFilePath = null,
                isPlayingRecording = false
            )
        }
    }

    fun switchAudioSource(source: AudioSource) {
        _uiState.update { it.copy(activeAudioSource = source) }
        if (source == AudioSource.RECORDING) {
            playRecordingFile(_uiState.value.recordingFilePath ?: return)
        } else {
            stopRecordingPlayback()
        }
    }

    fun playUserRecording() {
        val filePath = _uiState.value.recordingFilePath ?: return
        _uiState.update { it.copy(activeAudioSource = AudioSource.RECORDING) }
        playRecordingFile(filePath)
    }

    // ========== Recording Playback ==========

    private fun playRecordingFile(filePath: String) {
        stopRecordingPlayback()
        try {
            recordingPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                setOnPreparedListener { start() }
                setOnCompletionListener {
                    _uiState.update { it.copy(isPlayingRecording = false) }
                }
                setOnErrorListener { _, _, _ ->
                    _uiState.update { it.copy(isPlayingRecording = false, error = "录音回放失败") }
                    true
                }
                prepareAsync()
            }
            _uiState.update { it.copy(isPlayingRecording = true) }

            viewModelScope.launch {
                while (_uiState.value.isPlayingRecording) {
                    val pos = (recordingPlayer?.currentPosition ?: 0).toLong()
                    _uiState.update { it.copy(recordingDurationMs = pos) }
                    delay(100)
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isPlayingRecording = false, error = "录音回放失败: ${e.localizedMessage}") }
        }
    }

    private fun stopRecordingPlayback() {
        try {
            recordingPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        } catch (_: Exception) {}
        recordingPlayer = null
        _uiState.update { it.copy(isPlayingRecording = false) }
    }

    override fun onCleared() {
        super.onCleared()
        stopRecordingPlayback()
        sentenceAudioRecorder.cancelRecording()
        sentenceTTS.release()
    }
}

data class EpubShadowingUiState(
    val sentences: List<ShadowingSentence> = emptyList(),
    val bookTitle: String = "",
    val currentSentenceIndex: Int = 0,
    // TTS playback state
    val isTtsPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val currentSentenceProgressMs: Long = 0L,
    val currentSentenceDurationMs: Long = 3000L, // 估计每句 3 秒
    // Recording state
    val recordingState: RecordingState = RecordingState.IDLE,
    val isRecording: Boolean = false,
    val hasRecording: Boolean = false,
    val activeAudioSource: AudioSource = AudioSource.ORIGINAL,
    val recordingDurationMs: Long = 0L,
    val waveformAmplitudes: List<Float> = emptyList(),
    val recordedWaveform: List<Float> = emptyList(),
    val recordingFilePath: String? = null,
    val isPlayingRecording: Boolean = false,
    val hasMicrophonePermission: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)
