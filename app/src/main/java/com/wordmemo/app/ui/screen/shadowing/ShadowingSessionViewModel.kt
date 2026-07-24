package com.wordmemo.app.ui.screen.shadowing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.data.shadowing.service.AudioWaveformExtractor
import com.wordmemo.app.data.shadowing.service.SentenceAudioRecorder
import com.wordmemo.app.data.shadowing.service.VideoImportService
import com.wordmemo.app.domain.shadowing.model.ShadowingSentence
import com.wordmemo.app.domain.shadowing.repository.ShadowingRepository
import com.wordmemo.app.domain.shadowing.usecase.GetSentencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RecordingState {
    IDLE, RECORDING, PLAYBACK
}

enum class AudioSource {
    ORIGINAL, RECORDING
}

data class ShadowingSessionUiState(
    val sentences: List<ShadowingSentence> = emptyList(),
    val currentSentenceIndex: Int = 0,
    // Video playback state
    val videoFilePath: String = "",
    val videoTitle: String = "",
    val videoDurationMs: Long = 0L,
    val videoSubtitlePath: String? = null,
    val currentVideoId: Long = -1L,
    val isPlayingVideo: Boolean = false,
    val currentPositionMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    // Sync — when true, the UI layer should seek the player to the current sentence's start time
    val pendingSeekToSentenceMs: Long = -1L,
    // Recording state
    val recordingState: RecordingState = RecordingState.IDLE,
    val isRecording: Boolean = false,
    val hasRecording: Boolean = false,
    val activeAudioSource: AudioSource = AudioSource.ORIGINAL,
    val recordingDurationMs: Long = 0L,
    /** 实时振幅（录音中从 MediaRecorder 读取） */
    val waveformAmplitudes: List<Float> = emptyList(),
    /** 已保存录音的波形（录音结束后从文件提取） */
    val recordedWaveform: List<Float> = emptyList(),
    /** 当前句子的录音文件路径 */
    val recordingFilePath: String? = null,
    /** 录音回放中 */
    val isPlayingRecording: Boolean = false,
    /** 麦克风权限状态 */
    val hasMicrophonePermission: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ShadowingSessionViewModel @Inject constructor(
    private val getSentencesUseCase: GetSentencesUseCase,
    private val shadowingRepository: ShadowingRepository,
    private val videoImportService: VideoImportService,
    private val sentenceAudioRecorder: SentenceAudioRecorder,
    private val audioWaveformExtractor: AudioWaveformExtractor,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShadowingSessionUiState())
    val uiState: StateFlow<ShadowingSessionUiState> = _uiState.asStateFlow()

    /** 录音回放用 MediaPlayer */
    private var recordingPlayer: MediaPlayer? = null

    init {
        checkMicrophonePermission()
    }

    private fun checkMicrophonePermission() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        _uiState.update { it.copy(hasMicrophonePermission = granted) }
    }

    fun loadSentences(videoId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Load video info first for the file path
            val video = shadowingRepository.getVideoById(videoId)
            if (video != null) {
                _uiState.update {
                    it.copy(
                        videoFilePath = video.filePath,
                        videoTitle = video.title,
                        videoDurationMs = video.durationMs,
                        videoSubtitlePath = video.subtitlePath,
                        currentVideoId = video.id
                    )
                }
            }

            // Load sentences
            getSentencesUseCase(videoId)
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { sentences ->
                    _uiState.update {
                        it.copy(
                            sentences = sentences,
                            isLoading = false,
                            currentSentenceIndex = 0,
                            // Auto-seek to first sentence
                            pendingSeekToSentenceMs = if (sentences.isNotEmpty()) sentences[0].startTimeMs else -1L
                        )
                    }
                }
        }
    }
    /** Called by the UI layer after it has processed a seek command */
    fun onSeekCompleted() {
        _uiState.update { it.copy(pendingSeekToSentenceMs = -1L) }
    }

    // ========== Playback Controls ==========

    fun togglePlayPause() {
        _uiState.update { it.copy(isPlayingVideo = !it.isPlayingVideo) }
    }

    fun playVideo() {
        _uiState.update { it.copy(isPlayingVideo = true) }
    }

    fun pauseVideo() {
        _uiState.update { it.copy(isPlayingVideo = false) }
    }

    fun seekTo(positionMs: Long) {
        _uiState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun seekForward(seconds: Int = 10) {
        val current = _uiState.value.currentPositionMs
        val duration = _uiState.value.videoDurationMs
        if (duration <= 0L) {
            seekTo(current + seconds * 1000L)
        } else {
            val newPos = (current + seconds * 1000L).coerceAtMost(duration)
            seekTo(newPos)
        }
    }

    fun seekBackward(seconds: Int = 10) {
        val current = _uiState.value.currentPositionMs
        val newPos = (current - seconds * 1000L).coerceAtLeast(0L)
        seekTo(newPos)
    }

    // ========== Sentence Navigation ==========

    private fun seekToSentence(index: Int) {
        resetRecordingPlayback()
        val sentence = _uiState.value.sentences.getOrNull(index) ?: return
        _uiState.update {
            it.copy(
                currentSentenceIndex = index,
                pendingSeekToSentenceMs = sentence.startTimeMs,
                currentPositionMs = sentence.startTimeMs,
                // Reset recording state for the new sentence
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

    fun previousSentence() {
        val current = _uiState.value.currentSentenceIndex
        if (current > 0) {
            seekToSentence(current - 1)
        }
    }

    fun nextSentence() {
        val current = _uiState.value.currentSentenceIndex
        if (current < _uiState.value.sentences.size - 1) {
            seekToSentence(current + 1)
        }
    }

    fun jumpToSentence(index: Int) {
        if (index in _uiState.value.sentences.indices) {
            seekToSentence(index)
        }
    }

    /** Find the sentence that contains the given playback position */
    fun findSentenceAtPosition(positionMs: Long): Int {
        val sentences = _uiState.value.sentences
        if (sentences.isEmpty()) return -1
        val index = sentences.indexOfFirst { it.endTimeMs > positionMs }
        return if (index >= 0) index else sentences.size - 1
    }

    /** Called by the UI layer when playback position changes */
    fun onPlaybackPositionChanged(positionMs: Long) {
        _uiState.update { it.copy(currentPositionMs = positionMs) }
        // Auto-advance sentence based on playback position
        val sentenceIndex = findSentenceAtPosition(positionMs)
        if (sentenceIndex >= 0 && sentenceIndex != _uiState.value.currentSentenceIndex) {
            _uiState.update {
                it.copy(
                    currentSentenceIndex = sentenceIndex,
                    pendingSeekToSentenceMs = -1L
                )
            }
        }
    }

    // ========== Recording Controls ==========

    /**
     * 开始录音。
     * 检查权限 → 启动 MediaRecorder → 开始实时振幅轮询。
     */
    fun startRecording() {
        if (!_uiState.value.hasMicrophonePermission) {
            _uiState.update { it.copy(error = "需要麦克风权限才能录音") }
            return
        }

        val state = _uiState.value
        val sentence = state.sentences.getOrNull(state.currentSentenceIndex) ?: return

        // 暂停视频播放
        if (state.isPlayingVideo) {
            pauseVideo()
        }
        stopRecordingPlayback()
        resetRecordingPlayback()

        viewModelScope.launch {
            try {
                val filePath = sentenceAudioRecorder.startRecording(
                    state.currentVideoId,
                    sentence.id
                )

                _uiState.update {
                    it.copy(
                        recordingState = RecordingState.RECORDING,
                        isRecording = true,
                        hasRecording = false,
                        recordingDurationMs = 0L,
                        recordingFilePath = filePath,
                        waveformAmplitudes = List(60) { 0.05f } // 初始静默
                    )
                }

                // 实时振幅轮询
                launch {
                    var elapsed = 0L
                    while (_uiState.value.recordingState == RecordingState.RECORDING) {
                        // 从 MediaRecorder 读取实时振幅
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

    /**
     * 停止录音。
     * 关闭 MediaRecorder → 提取波形 → 更新状态。
     */
    fun stopRecording() {
        val result = sentenceAudioRecorder.stopRecording()
        if (result == null) {
            _uiState.update { it.copy(error = "停止录音失败") }
            return
        }

        val (filePath, durationMs) = result

        viewModelScope.launch {
            // 从录音文件提取真实波形
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

    /**
     * 播放已保存的录音。
     */
    fun playRecording() {
        val filePath = _uiState.value.recordingFilePath ?: return
        if (_uiState.value.isPlayingRecording) {
            stopRecordingPlayback()
            return
        }
        playRecordingFile(filePath)
    }

    /**
     * 删除当前句子的录音。
     */
    fun deleteRecording() {
        stopRecordingPlayback()
        val state = _uiState.value
        val sentence = state.sentences.getOrNull(state.currentSentenceIndex)
        if (sentence != null) {
            sentenceAudioRecorder.deleteRecordingForSentence(state.currentVideoId, sentence.id)
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

    fun playOriginalSentence() {
        stopRecordingPlayback()
        _uiState.update { it.copy(activeAudioSource = AudioSource.ORIGINAL) }
        // The screen's ExoPlayer handles playing the original video
        playVideo()
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

            // 轮询播放位置，更新 UI
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

    private fun resetRecordingPlayback() {
        stopRecordingPlayback()
    }

    override fun onCleared() {
        super.onCleared()
        stopRecordingPlayback()
        sentenceAudioRecorder.cancelRecording()
    }
}
