package com.wordmemo.app.ui.screen.pronunciation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.domain.pronunciation.model.AssessmentRecord
import com.wordmemo.app.domain.pronunciation.repository.PronunciationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssessmentResultUiState(
    val assessment: AssessmentRecord? = null,
    val accuracyScore: Int = 0,
    val fluencyScore: Int = 0,
    val completenessScore: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AssessmentResultViewModel @Inject constructor(
    private val pronunciationRepository: PronunciationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssessmentResultUiState())
    val uiState: StateFlow<AssessmentResultUiState> = _uiState.asStateFlow()

    fun loadAssessment(assessmentId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val assessment = pronunciationRepository.getById(assessmentId)
                if (assessment != null) {
                    // Derive dimension scores from overall score as v1 approximation
                    val overall = assessment.overallScore
                    _uiState.update {
                        it.copy(
                            assessment = assessment,
                            accuracyScore = computeAccuracy(overall),
                            fluencyScore = computeFluency(overall),
                            completenessScore = computeCompleteness(overall),
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, error = "未找到测评记录")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "加载失败")
                }
            }
        }
    }

    private fun computeAccuracy(overall: Int): Int = when {
        overall >= 90 -> overall - 2
        overall >= 75 -> overall - 5
        overall >= 60 -> overall - 8
        else -> overall
    }.coerceIn(0, 100)

    private fun computeFluency(overall: Int): Int = when {
        overall >= 80 -> overall - 8
        overall >= 60 -> overall - 10
        else -> overall - 5
    }.coerceIn(0, 100)

    private fun computeCompleteness(overall: Int): Int = when {
        overall >= 80 -> overall - 3
        overall >= 60 -> overall - 6
        else -> overall - 2
    }.coerceIn(0, 100)
}
