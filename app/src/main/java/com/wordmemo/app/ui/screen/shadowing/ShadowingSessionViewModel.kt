package com.wordmemo.app.ui.screen.shadowing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.domain.shadowing.model.ShadowingSentence
import com.wordmemo.app.domain.shadowing.repository.ShadowingRepository
import com.wordmemo.app.domain.shadowing.usecase.GetSentencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val waveformAmplitudes: List<Float> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ShadowingSessionViewModel @Inject constructor(
    private val getSentencesUseCase: GetSentencesUseCase,
    private val shadowingRepository: ShadowingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShadowingSessionUiState())
    val uiState: StateFlow<ShadowingSessionUiState> = _uiState.asStateFlow()

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
                        videoDurationMs = video.durationMs
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
        val newPos = (current + seconds * 1000L).coerceAtMost(_uiState.value.videoDurationMs)
        seekTo(newPos)
    }

    fun seekBackward(seconds: Int = 10) {
        val current = _uiState.value.currentPositionMs
        val newPos = (current - seconds * 1000L).coerceAtLeast(0L)
        seekTo(newPos)
    }

    // ========== Sentence Navigation ==========

    private fun seekToSentence(index: Int) {
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
                waveformAmplitudes = emptyList()
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
        // Find first sentence whose endTimeMs > positionMs
        val index = sentences.indexOfFirst { it.endTimeMs > positionMs }
        return if (index >= 0) index else sentences.size - 1
    }

    /** Called by the UI layer when playback position changes (e.g., from ExoPlayer callback) */
    fun onPlaybackPositionChanged(positionMs: Long) {
        _uiState.update { it.copy(currentPositionMs = positionMs) }
        // Auto-advance sentence based on playback position
        val sentenceIndex = findSentenceAtPosition(positionMs)
        if (sentenceIndex >= 0 && sentenceIndex != _uiState.value.currentSentenceIndex) {
            _uiState.update {
                it.copy(
                    currentSentenceIndex = sentenceIndex,
                    pendingSeekToSentenceMs = -1L // no seek needed, position already matches
                )
            }
        }
    }

    // ========== Recording Controls ==========

    fun startRecording() {
        _uiState.update {
            it.copy(
                recordingState = RecordingState.RECORDING,
                isRecording = true,
                waveformAmplitudes = List(32) { 0f }
            )
        }
        // Simulate waveform animation
        viewModelScope.launch {
            while (_uiState.value.recordingState == RecordingState.RECORDING) {
                val amps = List(32) { kotlin.random.Random.nextFloat() * 0.8f + 0.1f }
                _uiState.update { it.copy(waveformAmplitudes = amps) }
                delay(80)
            }
        }
        // Simulate recording duration
        viewModelScope.launch {
            var elapsed = 0L
            while (_uiState.value.recordingState == RecordingState.RECORDING) {
                delay(100)
                elapsed += 100
                _uiState.update { it.copy(recordingDurationMs = elapsed) }
            }
        }
    }

    fun stopRecording() {
        _uiState.update {
            it.copy(
                recordingState = RecordingState.PLAYBACK,
                isRecording = false,
                hasRecording = true,
                waveformAmplitudes = emptyList()
            )
        }
    }

    fun playRecording() {
        _uiState.update {
            it.copy(
                recordingState = RecordingState.PLAYBACK,
                activeAudioSource = AudioSource.RECORDING
            )
        }
    }

    fun deleteRecording() {
        _uiState.update {
            it.copy(
                recordingState = RecordingState.IDLE,
                hasRecording = false,
                activeAudioSource = AudioSource.ORIGINAL,
                recordingDurationMs = 0L
            )
        }
    }

    fun switchAudioSource(source: AudioSource) {
        _uiState.update { it.copy(activeAudioSource = source) }
    }

    fun playOriginalSentence() {
        _uiState.update { it.copy(activeAudioSource = AudioSource.ORIGINAL) }
    }

    fun playUserRecording() {
        _uiState.update { it.copy(activeAudioSource = AudioSource.RECORDING) }
    }
}
