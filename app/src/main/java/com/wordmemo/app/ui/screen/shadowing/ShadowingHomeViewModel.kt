package com.wordmemo.app.ui.screen.shadowing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.data.shadowing.service.DownloadProgress
import com.wordmemo.app.data.shadowing.service.DownloadStatus
import com.wordmemo.app.domain.shadowing.model.ShadowingVideo
import com.wordmemo.app.domain.shadowing.usecase.GetShadowingVideosUseCase
import com.wordmemo.app.domain.shadowing.usecase.ImportVideoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShadowingHomeUiState(
    val videoList: List<ShadowingVideo> = emptyList(),
    val isLoading: Boolean = false,
    val downloadProgress: DownloadProgress? = null,
    val error: String? = null
)

sealed interface ShadowingHomeEvent {
    data object LoadVideos : ShadowingHomeEvent
    data class ImportBilibili(val url: String) : ShadowingHomeEvent
    data object ImportLocalVideo : ShadowingHomeEvent
    data class DeleteVideo(val videoId: Long) : ShadowingHomeEvent
}

@HiltViewModel
class ShadowingHomeViewModel @Inject constructor(
    private val getShadowingVideosUseCase: GetShadowingVideosUseCase,
    private val importVideoUseCase: ImportVideoUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShadowingHomeUiState())
    val uiState: StateFlow<ShadowingHomeUiState> = _uiState.asStateFlow()

    init {
        onEvent(ShadowingHomeEvent.LoadVideos)
    }

    fun onEvent(event: ShadowingHomeEvent) {
        when (event) {
            is ShadowingHomeEvent.LoadVideos -> loadVideos()
            is ShadowingHomeEvent.ImportBilibili -> importBilibili(event.url)
            is ShadowingHomeEvent.ImportLocalVideo -> importLocalVideo()
            is ShadowingHomeEvent.DeleteVideo -> deleteVideo(event.videoId)
        }
    }

    private fun loadVideos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            getShadowingVideosUseCase()
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { videos ->
                    _uiState.update { it.copy(videoList = videos, isLoading = false) }
                }
        }
    }

    private fun importBilibili(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            val result = importVideoUseCase(url)
            result.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private fun importLocalVideo() {
        // 通过SAF文件选择器导入 — UI层处理Intent启动
        _uiState.update { it.copy(error = null) }
    }

    private fun deleteVideo(videoId: Long) {
        // 由UI层调用Repository
    }
}
