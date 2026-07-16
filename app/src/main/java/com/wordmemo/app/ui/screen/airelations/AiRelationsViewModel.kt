package com.wordmemo.app.ui.screen.airelations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.data.local.WordMemoDatabase
import com.wordmemo.app.data.local.mapper.toDomain
import com.wordmemo.app.data.repository.AiRepositoryImpl
import com.wordmemo.app.domain.model.AiRelation
import com.wordmemo.app.domain.model.Word
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiRelationsUiState(
    val word: Word? = null,
    val relations: List<AiRelation> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class AiRelationsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WordMemoDatabase.getInstance(application)
    private val wordDao = db.wordDao()
    private val aiRepository = AiRepositoryImpl(db.aiContentDao(), db.appConfigDao(), com.google.gson.Gson())

    private val _uiState = MutableStateFlow(AiRelationsUiState())
    val uiState: StateFlow<AiRelationsUiState> = _uiState.asStateFlow()

    fun loadRelations(wordId: Long) {
        viewModelScope.launch {
            _uiState.value = AiRelationsUiState(isLoading = true)
            try {
                val word = wordDao.getById(wordId)?.toDomain()
                if (word != null) {
                    val relations = aiRepository.getRelatedWords(word.english)
                    _uiState.value = AiRelationsUiState(
                        word = word,
                        relations = relations,
                        isLoading = false
                    )
                } else {
                    _uiState.value = AiRelationsUiState(isLoading = false, error = "单词未找到")
                }
            } catch (e: Exception) {
                _uiState.value = AiRelationsUiState(isLoading = false, error = e.message ?: "AI 请求失败")
            }
        }
    }
}
