package com.wordmemo.app.ui.screen.shadowing

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.data.shadowing.service.DownloadProgress
import com.wordmemo.app.data.shadowing.service.DownloadStatus
import com.wordmemo.app.data.shadowing.service.VideoImportService
import com.wordmemo.app.domain.shadowing.model.ShadowingVideo
import com.wordmemo.app.domain.shadowing.repository.ShadowingRepository
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
    data class ImportLocalFile(val uri: Uri) : ShadowingHomeEvent
    data class ImportLocalFileWithSubtitle(val videoUri: Uri, val subtitleUri: Uri) : ShadowingHomeEvent
    data class DeleteVideo(val videoId: Long) : ShadowingHomeEvent
    data object ClearError : ShadowingHomeEvent
}

@HiltViewModel
class ShadowingHomeViewModel @Inject constructor(
    private val getShadowingVideosUseCase: GetShadowingVideosUseCase,
    private val importVideoUseCase: ImportVideoUseCase,
    private val videoImportService: VideoImportService,
    private val shadowingRepository: ShadowingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShadowingHomeUiState())
    val uiState: StateFlow<ShadowingHomeUiState> = _uiState.asStateFlow()

    init {
        onEvent(ShadowingHomeEvent.LoadVideos)
        observeDownloadProgress()
    }

    private fun observeDownloadProgress() {
        viewModelScope.launch {
            videoImportService.downloadProgress.collect { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
            }
        }
    }

    fun onEvent(event: ShadowingHomeEvent) {
        when (event) {
            is ShadowingHomeEvent.LoadVideos -> loadVideos()
            is ShadowingHomeEvent.ImportBilibili -> importBilibili(event.url)
            is ShadowingHomeEvent.ImportLocalFile -> importLocalVideo(event.uri)
            is ShadowingHomeEvent.ImportLocalFileWithSubtitle -> importLocalVideoWithSubtitle(event.videoUri, event.subtitleUri)
            is ShadowingHomeEvent.DeleteVideo -> deleteVideo(event.videoId)
            is ShadowingHomeEvent.ClearError -> _uiState.update { it.copy(error = null) }
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

    fun importBilibili(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            val result = importVideoUseCase(url)
            result.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private fun importLocalVideo(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null, isLoading = true) }
            val result = videoImportService.importLocalVideo(uri)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun importLocalVideoWithSubtitle(videoUri: Uri, subtitleUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null, isLoading = true) }
            val result = videoImportService.importLocalVideo(videoUri, subtitleUri)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun deleteVideo(videoId: Long) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                shadowingRepository.deleteVideo(videoId)
                // deleteVideo 之后不需要显式调用 loadVideos，因为 Room Flow 会自动发射新数据
                // 但为了即时反馈，更新 isLoading
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "删除失败: ${e.message}") }
            }
        }
    }
}
