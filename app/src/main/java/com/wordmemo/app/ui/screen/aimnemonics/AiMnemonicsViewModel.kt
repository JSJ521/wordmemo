package com.wordmemo.app.ui.screen.aimnemonics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.data.local.WordMemoDatabase
import com.wordmemo.app.data.local.mapper.toDomain
import com.wordmemo.app.data.repository.AiRepositoryImpl
import com.wordmemo.app.domain.model.AiMnemonic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiMnemonicsUiState(
    val word: com.wordmemo.app.domain.model.Word? = null,
    val mnemonics: List<AiMnemonic> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class AiMnemonicsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WordMemoDatabase.getInstance(application)
    private val wordDao = db.wordDao()
    private val aiRepository = AiRepositoryImpl(db.aiContentDao(), db.appConfigDao(), com.google.gson.Gson())

    private val _uiState = MutableStateFlow(AiMnemonicsUiState())
    val uiState: StateFlow<AiMnemonicsUiState> = _uiState.asStateFlow()

    fun loadMnemonics(wordId: Long) {
        viewModelScope.launch {
            _uiState.value = AiMnemonicsUiState(isLoading = true)
            try {
                val word = wordDao.getById(wordId)?.toDomain()
                if (word != null) {
                    val mnemonics = aiRepository.generateMnemonics(word.english)
                    _uiState.value = AiMnemonicsUiState(
                        word = word,
                        mnemonics = mnemonics,
                        isLoading = false
                    )
                } else {
                    _uiState.value = AiMnemonicsUiState(isLoading = false, error = "单词未找到")
                }
            } catch (e: Exception) {
                _uiState.value = AiMnemonicsUiState(isLoading = false, error = e.message ?: "AI 请求失败")
            }
        }
    }
}
