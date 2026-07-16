package com.wordmemo.app.ui.screen.wordlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.data.local.WordMemoDatabase
import com.wordmemo.app.data.local.entity.FlashcardEntity
import com.wordmemo.app.data.local.entity.WordEntity
import com.wordmemo.app.data.network.AiWordGenerator
import com.wordmemo.app.domain.model.Group
import com.wordmemo.app.domain.model.Word
import com.wordmemo.app.domain.repository.GroupRepository
import com.wordmemo.app.domain.repository.ReviewRepository
import com.wordmemo.app.domain.repository.WordRepository
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class WordListUiState(
    val words: List<Word> = emptyList(),
    val groups: List<Group> = emptyList(),
    val selectedGroupId: Long? = null,
    val searchQuery: String = "",
    val dueCount: Int = 0,
    val masteredWordIds: Set<Long> = emptySet(),
    val showMasteredOnly: Boolean = false,
    val isLoading: Boolean = true,
    // AI 生成状态
    val isGeneratingAi: Boolean = false,
    val aiGeneratedWords: List<AiWordGenerator.GeneratedWord> = emptyList(),
    val aiGenerationResult: String? = null
)

@HiltViewModel
class WordListViewModel @Inject constructor(
    private val application: Application,
    private val wordRepository: WordRepository,
    private val groupRepository: GroupRepository,
    private val reviewRepository: ReviewRepository
) : AndroidViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedGroupId = MutableStateFlow<Long?>(null)

    private val _uiState = MutableStateFlow(WordListUiState())
    val uiState: StateFlow<WordListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                wordRepository.observeAll(),
                groupRepository.observeAll(),
                _selectedGroupId,
                _searchQuery,
                reviewRepository.observeDueCount()
            ) { words, groups, groupId, query, dueCount ->
                val display = when {
                    groupId != null -> wordRepository.observeByGroup(groupId).first()
                    query.isNotBlank() -> wordRepository.search(query)
                    else -> words
                }
                WordListUiState(
                    words = display, groups = groups,
                    selectedGroupId = groupId, searchQuery = query,
                    dueCount = dueCount, isLoading = false,
                    masteredWordIds = _uiState.value.masteredWordIds,
                    showMasteredOnly = _uiState.value.showMasteredOnly,
                    isGeneratingAi = _uiState.value.isGeneratingAi,
                    aiGeneratedWords = _uiState.value.aiGeneratedWords,
                    aiGenerationResult = _uiState.value.aiGenerationResult
                )
            }.collect { _uiState.value = it }
        }

        viewModelScope.launch {
            combine(
                reviewRepository.observeDueCount(),
                flow { emit(reviewRepository.getMasteredWordIds()) }
            ) { dueCount, masteredIds ->
                _uiState.update { it.copy(dueCount = dueCount, masteredWordIds = masteredIds.toSet()) }
            }.collect()
        }
    }

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }
    fun onGroupSelected(groupId: Long?) { _selectedGroupId.value = groupId }
    fun deleteWord(wordId: Long) { viewModelScope.launch { wordRepository.delete(wordId) } }
    fun toggleMasteredFilter() { _uiState.update { it.copy(showMasteredOnly = !it.showMasteredOnly) } }
    fun dismissAiResult() { _uiState.update { it.copy(aiGenerationResult = null) } }

    /** 一键生成海外 EPC 项目词汇 + 自动创建复习卡 */
    fun generateAiVocab() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingAi = true, aiGeneratedWords = emptyList(), aiGenerationResult = null) }
            try {
                val db = WordMemoDatabase.getInstance(application)
                val generator = AiWordGenerator(Gson())

                // 1. 读取 API 配置
                val config = generator.loadApiConfig(db)
                if (config == null) {
                    _uiState.update { it.copy(isGeneratingAi = false, aiGenerationResult = "⚠️ 请先在设置页配置 API Key") }
                    return@launch
                }

                // 2. 估算难度
                val level = generator.estimateDifficultyLevel(db)
                android.util.Log.i("AiVocab", "估算难度等级: $level/10")

                // 3. 调用 AI 生成
                val result = withContext(Dispatchers.IO) { generator.generateVocab(config, level) }

                result.fold(
                    onSuccess = { words ->
                        // 4. 逐个保存到数据库 + 创建复习卡
                        var savedCount = 0
                        for (w in words) {
                            try {
                                val now = System.currentTimeMillis()
                                val wid = withContext(Dispatchers.IO) {
                                    db.wordDao().insert(WordEntity(
                                        english = w.english, chinese = w.chinese,
                                        phonetic = w.phonetic, note = "海外EPC行业词汇\n例句: ${w.example}\n搭配: ${w.collocations}",
                                        createdAt = now, updatedAt = now
                                    ))
                                }
                                withContext(Dispatchers.IO) {
                                    db.flashcardDao().insert(FlashcardEntity(
                                        wordId = wid, state = "NEW", due = now
                                    ))
                                }
                                savedCount++
                            } catch (_: Exception) { }
                        }

                        _uiState.update { it.copy(
                            isGeneratingAi = false,
                            aiGeneratedWords = words,
                            aiGenerationResult = "✅ 已生成 $savedCount 个行业词汇并加入复习"
                        )}
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(
                            isGeneratingAi = false,
                            aiGenerationResult = "❌ 生成失败: ${e.message?.take(60) ?: "API 调用异常"}"
                        )}
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isGeneratingAi = false, aiGenerationResult = "❌ 错误: ${e.message?.take(60)}") }
            }
        }
    }
}
