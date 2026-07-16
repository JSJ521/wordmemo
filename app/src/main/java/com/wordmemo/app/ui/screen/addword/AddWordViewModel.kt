package com.wordmemo.app.ui.screen.addword

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.data.local.WordMemoDatabase
import com.wordmemo.app.data.local.mapper.toEntity
import com.wordmemo.app.domain.model.Word
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AddWordUiState(
    val english: String = "",
    val chinese: String = "",
    val note: String = "",
    val isSaving: Boolean = false,
    val saveResult: String? = null
)

class AddWordViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WordMemoDatabase.getInstance(application)
    private val wordDao = db.wordDao()
    private val flashcardDao = db.flashcardDao()

    private val _uiState = MutableStateFlow(AddWordUiState())
    val uiState: StateFlow<AddWordUiState> = _uiState.asStateFlow()

    fun onEnglishChanged(text: String) { _uiState.value = _uiState.value.copy(english = text) }
    fun onChineseChanged(text: String) { _uiState.value = _uiState.value.copy(chinese = text) }
    fun onNoteChanged(text: String) { _uiState.value = _uiState.value.copy(note = text) }

    fun save() {
        val state = _uiState.value
        if (state.english.isBlank()) {
            _uiState.value = state.copy(saveResult = "请输入英文单词")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            val chinese = if (state.chinese.isNotBlank()) state.chinese.trim() else "[待补充]"
            val word = Word(
                english = state.english.trim(),
                chinese = chinese,
                note = state.note.trim().ifBlank { null }
            )
            try {
                val wordId = wordDao.insert(word.toEntity())
                if (wordId > 0) {
                    // 自动创建复习卡
                    flashcardDao.insert(
                        com.wordmemo.app.data.local.entity.FlashcardEntity(
                            wordId = wordId,
                            state = "NEW",
                            due = System.currentTimeMillis()
                        )
                    )
                    _uiState.value = AddWordUiState(saveResult = "✅ 已收藏")
                } else {
                    _uiState.value = _uiState.value.copy(isSaving = false, saveResult = "⚠️ 单词已存在")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, saveResult = "❌ 保存失败: ${e.message}")
            }
        }
    }

    fun clearResult() { _uiState.value = _uiState.value.copy(saveResult = null) }
}
