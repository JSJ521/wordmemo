package com.wordmemo.app.ui.screen.pronunciation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.domain.pronunciation.model.AssessmentRecord
import com.wordmemo.app.domain.pronunciation.usecase.GetAssessmentHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProgressUiState(
    val historyRecords: List<AssessmentRecord> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val getAssessmentHistoryUseCase: GetAssessmentHistoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            getAssessmentHistoryUseCase()
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { records ->
                    _uiState.update { it.copy(historyRecords = records, isLoading = false) }
                }
        }
    }
}
