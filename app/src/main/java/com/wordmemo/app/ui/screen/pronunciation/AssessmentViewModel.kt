package com.wordmemo.app.ui.screen.pronunciation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.domain.pronunciation.model.AssessmentRecord
import com.wordmemo.app.domain.pronunciation.repository.PronunciationRepository
import com.wordmemo.app.domain.pronunciation.service.PronunciationAssessor
import com.wordmemo.app.domain.shadowing.model.ShadowingRecord
import com.wordmemo.app.domain.shadowing.repository.ShadowingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssessmentHomeUiState(
    val pendingRecordings: List<ShadowingRecord> = emptyList(),
    val historyRecords: List<AssessmentRecord> = emptyList(),
    val selectedRecordId: Long? = null,
    val isLoading: Boolean = false,
    val isAssessing: Boolean = false,
    val sentencesText: Map<Long, String> = emptyMap(),
    val videoTitles: Map<Long, String> = emptyMap(),
    val error: String? = null
)

@HiltViewModel
class AssessmentViewModel @Inject constructor(
    private val pronunciationAssessor: PronunciationAssessor,
    private val shadowingRepository: ShadowingRepository,
    private val pronunciationRepository: PronunciationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssessmentHomeUiState())
    val uiState: StateFlow<AssessmentHomeUiState> = _uiState.asStateFlow()

    init {
        loadRecordings()
        loadHistory()
    }

    private fun loadRecordings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Observe all videos and get sentences for recordings
            shadowingRepository.observeVideos()
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { videos ->
                    _uiState.update { it.copy(
                        videoTitles = videos.associate { it.id to it.title }
                    )}
                }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            pronunciationRepository.observeAll()
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { records ->
                    _uiState.update { it.copy(historyRecords = records) }
                }
        }
    }

    fun selectRecording(recordId: Long) {
        _uiState.update { it.copy(selectedRecordId = recordId) }
    }

    fun startAssessment(onResult: (Long) -> Unit) {
        val recordId = _uiState.value.selectedRecordId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isAssessing = true, error = null) }
            try {
                // Load the recording and its sentence
                val recording = _uiState.value.pendingRecordings.find { it.id == recordId }
                val sentenceText = _uiState.value.sentencesText[recordId] ?: ""

                if (recording != null) {
                    val assessment = pronunciationAssessor.recordToAssessment(
                        recordId = recordId,
                        sentenceText = sentenceText,
                        audioFilePath = recording.audioFilePath
                    )
                    val savedId = pronunciationRepository.insert(assessment)
                    _uiState.update { it.copy(isAssessing = false) }
                    onResult(savedId)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isAssessing = false, error = e.message ?: "测评失败")
                }
            }
        }
    }
}
