package com.wordmemo.app.ui.screen.worddetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.data.local.WordMemoDatabase
import com.wordmemo.app.data.local.mapper.toDomain
import com.wordmemo.app.data.local.mapper.toEntity
import com.wordmemo.app.data.local.entity.WordGroupCrossRef
import com.wordmemo.app.data.network.NetworkMonitor
import com.wordmemo.app.data.repository.AiRepositoryImpl
import com.wordmemo.app.domain.model.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WordDetailUiState(
    val word: Word? = null,
    val isOnline: Boolean = false,
    val isLoading: Boolean = true,
    val isTranslating: Boolean = false,
    val translationResult: String? = null,
    val translationError: String? = null,
    val allGroups: List<com.wordmemo.app.domain.model.Group> = emptyList(),
    val wordGroups: List<Long> = emptyList()
)

class WordDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WordMemoDatabase.getInstance(application)
    private val wordDao = db.wordDao()
    private val groupDao = db.groupDao()
    private val aiRepository = AiRepositoryImpl(db.aiContentDao(), db.appConfigDao(), com.google.gson.Gson())
    private val networkMonitor = NetworkMonitor(application)

    private val _uiState = MutableStateFlow(WordDetailUiState())
    val uiState: StateFlow<WordDetailUiState> = _uiState.asStateFlow()

    fun loadWord(wordId: Long) {
        viewModelScope.launch {
            try {
                val entity = wordDao.getById(wordId)
                val word = entity?.toDomain()
                val isOnline = networkMonitor.isOnline()
                _uiState.value = WordDetailUiState(
                    word = word,
                    isOnline = isOnline,
                    isLoading = false
                )

                // 加载分组信息
                val allGroups = groupDao.observeAll().first().map {
                    com.wordmemo.app.domain.model.Group(id = it.id, name = it.name, color = it.color)
                }
                val wordGroups = groupDao.getGroupsForWord(wordId).map { it.id }
                _uiState.value = _uiState.value.copy(allGroups = allGroups, wordGroups = wordGroups)

                // 自动翻译
                if (word != null && isOnline && word.chinese.contains("待补充")) {
                    autoTranslate(word)
                } else if (word != null && word.chinese.contains("待补充") && !isOnline) {
                    _uiState.value = _uiState.value.copy(translationError = "当前无网络连接，无法自动获取翻译")
                }
            } catch (e: Exception) {
                _uiState.value = WordDetailUiState(
                    isLoading = false,
                    isOnline = false
                )
            }
        }
    }

    private suspend fun autoTranslate(word: Word) {
        _uiState.value = _uiState.value.copy(isTranslating = true)
        try {
            val result = aiRepository.translate(word.english)
            if (result.translation.isNotBlank() && result.translation != "翻译不可用" && result.translation != "解析失败") {
                val phonetic = if (result.phonetic.isNotBlank()) "[${result.phonetic}] " else ""
                // 只在单词无已有备注时写入翻译用法说明
                val newNote = if (word.note.isNullOrBlank() && !result.usage.isNullOrBlank()) {
                    "📖 ${result.usage}"
                } else {
                    word.note // 保留用户已有备注
                }
                val updatedWord = Word(
                    id = word.id, english = word.english,
                    chinese = "$phonetic${result.translation}",
                    note = newNote,
                    createdAt = word.createdAt, updatedAt = System.currentTimeMillis()
                )
                wordDao.update(updatedWord.toEntity())
                val entity = wordDao.getById(word.id)
                _uiState.value = _uiState.value.copy(
                    word = entity?.toDomain(), isTranslating = false, translationResult = result.translation
                )
            } else {
                val errorMsg = if (result.translation.isBlank() || 
                    result.translation == "翻译不可用" || 
                    result.translation == "解析失败") {
                    "AI翻译暂不可用，请检查网络连接或API配置"
                } else {
                    null
                }
                _uiState.value = _uiState.value.copy(
                    isTranslating = false,
                    translationResult = null,
                    translationError = errorMsg
                )
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isTranslating = false,
                translationResult = null,
                translationError = "翻译请求失败: ${e.message?.take(50) ?: "未知错误"}"
            )
        }
    }

    /** 重新尝试翻译 */
    fun retryTranslate() {
        val word = _uiState.value.word ?: return
        viewModelScope.launch {
            autoTranslate(word)
        }
    }

    fun assignToGroup(groupId: Long) {
        val word = _uiState.value.word ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                groupDao.assignWordToGroup(
                    WordGroupCrossRef(wordId = word.id, groupId = groupId)
                )
            }
            val ids = groupDao.getGroupsForWord(word.id).map { it.id }
            _uiState.value = _uiState.value.copy(wordGroups = ids)
        }
    }

    fun removeFromGroup(groupId: Long) {
        val word = _uiState.value.word ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                groupDao.removeWordFromGroup(word.id, groupId)
            }
            val ids = groupDao.getGroupsForWord(word.id).map { it.id }
            _uiState.value = _uiState.value.copy(wordGroups = ids)
        }
    }
}
