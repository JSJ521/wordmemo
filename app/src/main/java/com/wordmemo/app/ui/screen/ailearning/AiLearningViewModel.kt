package com.wordmemo.app.ui.screen.ailearning

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.data.local.WordMemoDatabase
import com.wordmemo.app.data.local.entity.FlashcardEntity
import com.wordmemo.app.data.local.entity.WordEntity
import com.wordmemo.app.data.local.mapper.toDomain
import com.google.gson.Gson
import com.wordmemo.app.data.network.AiWordGenerator
import com.wordmemo.app.data.network.AiWordGenerator.AiConfig
import com.wordmemo.app.data.repository.AiRepositoryImpl
import com.wordmemo.app.domain.model.AiMnemonic
import com.wordmemo.app.domain.model.Word
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiLearningUiState(
    val word: Word? = null,
    val mnemonics: List<AiMnemonic> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isGenerating: Boolean = false,
    val generatedWords: List<AiWordGenerator.GeneratedWord> = emptyList(),
    val generationResult: String? = null,
    val selectedTab: Int = 0
)

class AiLearningViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WordMemoDatabase.getInstance(application)
    private val wordDao = db.wordDao()
    private val appConfigDao = db.appConfigDao()
    private val flashcardDao = db.flashcardDao()
    private val aiRepository = AiRepositoryImpl(db.aiContentDao(), db.appConfigDao(), com.google.gson.Gson())

    private val _uiState = MutableStateFlow(AiLearningUiState())
    val uiState: StateFlow<AiLearningUiState> = _uiState.asStateFlow()

    fun loadWord(wordId: Long) {
        viewModelScope.launch {
            _uiState.value = AiLearningUiState(isLoading = true)
            try {
                val word = wordDao.getById(wordId)?.toDomain()
                if (word != null) {
                    val mnemonics = aiRepository.generateMnemonics(word.english)
                    _uiState.value = AiLearningUiState(
                        word = word,
                        mnemonics = mnemonics,
                        isLoading = false
                    )
                } else {
                    _uiState.value = AiLearningUiState(isLoading = false, error = "单词未找到")
                }
            } catch (e: Exception) {
                _uiState.value = AiLearningUiState(isLoading = false, error = e.message ?: "AI 请求失败")
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }

    fun generateAiVocab() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, generationResult = null)
            try {
                val configs = appConfigDao.getAll()
                val rawKey = configs.find { it.key == "api_key" }?.value ?: run {
                    _uiState.value = _uiState.value.copy(isGenerating = false, generationResult = "API Key 未配置")
                    return@launch
                }
                // 解密 + 截断检测（仅识别 ... 标记）
                val cipher = com.wordmemo.app.data.encryption.ApiKeyCipher()
                val apiKey = if (rawKey.isBlank()) {
                    "⚠️ API Key 未配置，请在设置页填写"
                } else {
                    val decrypted = cipher.decrypt(rawKey)
                    if (decrypted.contains("...")) {
                        "⚠️ API Key 已损坏，请在设置页重新配置"
                    } else decrypted
                }
                val baseUrl = configs.find { it.key == "api_base_url" }?.value ?: "https://api.deepseek.com"
                val model = configs.find { it.key == "api_model" }?.value ?: "deepseek-chat"
                val generator = AiWordGenerator(Gson())
                val total = flashcardDao.countTotal()
                val difficulty = when {
                    total < 5 -> 6
                    else -> {
                        val mastered = flashcardDao.countMastered()
                        val ratio = mastered.toFloat() / total
                        when {
                            ratio < 0.1f -> 4
                            ratio < 0.3f -> 6
                            ratio < 0.5f -> 7
                            else -> 8
                        }
                    }
                }
                val config = AiConfig(apiKey, baseUrl, model)
                val existingWords = wordDao.getAllEnglish()
                val result = generator.generateVocab(config, difficulty, 8, existingVocab = existingWords)
                result.onSuccess { words ->
                    // batch内去重 + 数据库去重
                    val unique = words.distinctBy { it.english.lowercase().trim() }
                    var added = 0
                    var skipped = 0
                    for (w in unique) {
                        val existing = wordDao.getByEnglish(w.english)
                        if (existing == null) {
                            val entity = WordEntity(english = w.english, chinese = w.chinese)
                            val id = wordDao.insert(entity)
                            flashcardDao.insert(FlashcardEntity(wordId = id))
                            added++
                        } else {
                            skipped++
                        }
                    }
                    val msg = if (added > 0) "✅ 已生成 $added 个行业词汇"
                             else if (skipped > 0) "⚠️ 本次生成的词汇都已存在，未添加新词"
                             else "⚠️ 未生成有效词汇"
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        generatedWords = unique,
                        generationResult = "✅ 已生成 $added 个行业词汇"
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        generationResult = "AI生成失败: ${e.message?.take(100) ?: "未知错误"}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    generationResult = "生成异常: ${e.message?.take(100) ?: "未知错误"}"
                )
            }
        }
    }

    fun dismissResult() {
        _uiState.value = _uiState.value.copy(generationResult = null)
    }
}
