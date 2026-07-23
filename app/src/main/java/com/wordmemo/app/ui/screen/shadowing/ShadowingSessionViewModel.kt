package com.wordmemo.app.ui.screen.shadowing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.domain.shadowing.model.ShadowingSentence
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
    val isPlayingVideo: Boolean = false,
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
    private val getSentencesUseCase: GetSentencesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShadowingSessionUiState())
    val uiState: StateFlow<ShadowingSessionUiState> = _uiState.asStateFlow()

    fun loadSentences(videoId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            getSentencesUseCase(videoId)
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { sentences ->
                    _uiState.update {
                        it.copy(
                            sentences = sentences,
                            isLoading = false,
                            currentSentenceIndex = 0
                        )
                    }
                }
        }
    }

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

    fun previousSentence() {
        val current = _uiState.value.currentSentenceIndex
        if (current > 0) {
            _uiState.update {
                it.copy(
                    currentSentenceIndex = current - 1,
                    recordingState = RecordingState.IDLE,
                    hasRecording = false,
                    recordingDurationMs = 0L
                )
            }
        }
    }

    fun nextSentence() {
        val current = _uiState.value.currentSentenceIndex
        if (current < _uiState.value.sentences.size - 1) {
            _uiState.update {
                it.copy(
                    currentSentenceIndex = current + 1,
                    recordingState = RecordingState.IDLE,
                    hasRecording = false,
                    recordingDurationMs = 0L
                )
            }
        }
    }

    fun jumpToSentence(index: Int) {
        if (index in _uiState.value.sentences.indices) {
            _uiState.update {
                it.copy(
                    currentSentenceIndex = index,
                    recordingState = RecordingState.IDLE,
                    hasRecording = false,
                    recordingDurationMs = 0L
                )
            }
        }
    }
}
