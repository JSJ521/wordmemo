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
    val batchInput: String = "",
    val isBatchMode: Boolean = false,
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
    fun onBatchInputChanged(text: String) { _uiState.value = _uiState.value.copy(batchInput = text) }
    fun toggleBatchMode() {
        _uiState.value = _uiState.value.copy(isBatchMode = !_uiState.value.isBatchMode, saveResult = null)
    }

    fun save() {
        val state = _uiState.value
        if (state.isBatchMode) {
            saveBatch(state.batchInput)
        } else {
            saveSingle(state.english, state.chinese, state.note)
        }
    }

    private fun saveSingle(english: String, chinese: String, note: String) {
        if (english.isBlank()) {
            _uiState.value = _uiState.value.copy(saveResult = "请输入英文单词")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            val cn = if (chinese.isNotBlank()) chinese.trim() else "[待补充]"
            val word = Word(english = english.trim(), chinese = cn, note = note.trim().ifBlank { null })
            try {
                val wordId = wordDao.insert(word.toEntity())
                if (wordId > 0) {
                    flashcardDao.insert(com.wordmemo.app.data.local.entity.FlashcardEntity(
                        wordId = wordId, state = "NEW", due = System.currentTimeMillis()))
                    _uiState.value = AddWordUiState(saveResult = "✅ 已收藏", isBatchMode = _uiState.value.isBatchMode)
                } else {
                    _uiState.value = _uiState.value.copy(isSaving = false, saveResult = "⚠️ 单词已存在")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, saveResult = "❌ 保存失败: ${e.message}")
            }
        }
    }

    private fun saveBatch(input: String) {
        if (input.isBlank()) {
            _uiState.value = _uiState.value.copy(saveResult = "请输入要添加的单词")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            val words = input.split(Regex("[\\n,，、\\s]+"))
                .map { it.trim().lowercase() }
                .filter { it.matches(Regex("^[a-z][a-z\\-']{1,44}$")) }
                .distinct()

            if (words.isEmpty()) {
                _uiState.value = _uiState.value.copy(isSaving = false, saveResult = "❌ 未识别到有效英文单词")
                return@launch
            }

            var added = 0
            var skipped = 0
            val errors = mutableListOf<String>()

            for (word in words) {
                try {
                    val existing = wordDao.getByEnglish(word)
                    if (existing != null) {
                        skipped++
                        continue
                    }
                    val wordId = wordDao.insert(Word(english = word, chinese = "[待补充]").toEntity())
                    if (wordId > 0) {
                        flashcardDao.insert(com.wordmemo.app.data.local.entity.FlashcardEntity(
                            wordId = wordId, state = "NEW", due = System.currentTimeMillis()))
                        added++
                    }
                } catch (e: Exception) {
                    errors.add(word)
                }
            }

            val msg = buildString {
                append("✅ 已添加 $added 个")
                if (skipped > 0) append("，跳过 $skipped 个重复")
                if (errors.isNotEmpty()) append("，${errors.size} 个失败")
            }
            _uiState.value = AddWordUiState(saveResult = msg, isBatchMode = true, batchInput = input)
        }
    }

    fun clearResult() { _uiState.value = _uiState.value.copy(saveResult = null) }
}
