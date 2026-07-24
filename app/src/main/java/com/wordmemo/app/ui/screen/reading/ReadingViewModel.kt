package com.wordmemo.app.ui.screen.reading

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.data.epub.Book
import com.wordmemo.app.data.epub.Chapter
import com.wordmemo.app.data.epub.EpubReader
import com.wordmemo.app.data.epub.SentenceSplitter
import com.wordmemo.app.data.epub.TextReader
import com.wordmemo.app.data.local.dao.AppConfigDao
import com.wordmemo.app.data.network.AiApiClient
import com.wordmemo.app.data.shadowing.dao.ReadingProgressDao
import com.wordmemo.app.data.shadowing.entity.ReadingProgressEntity
import com.wordmemo.app.data.tts.SentenceTTS
import com.wordmemo.app.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * EPUB / TXT 精听阅读 ViewModel。
 *
 * 职责：
 * - 管理 EPUB/TXT 文件导入与解析
 * - 管理书架（已导入书籍列表）
 * - 管理阅读状态（当前章节、当前句子、TTS 状态）
 * - 管理翻译（复用 AiApiClient + AppConfigDao）
 * - 管理阅读进度持久化
 */
@HiltViewModel
class ReadingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sentenceTTS: SentenceTTS,
    private val aiApiClient: AiApiClient,
    private val appConfigDao: AppConfigDao,
    private val readingProgressDao: ReadingProgressDao
) : ViewModel() {

    // ==================== 状态 ====================

    data class UiState(
        // 书架
        val books: List<BookItem> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,

        // 当前阅读
        val currentBook: Book? = null,
        val currentChapterIndex: Int = 0,
        val currentSentenceIndex: Int = -1,
        val sentences: List<String> = emptyList(),

        // TTS
        val isTtsInitialized: Boolean = false,
        val isTtsSpeaking: Boolean = false,
        val ttsHighlightedSentence: String = "",
        val playbackSpeed: Float = 1.0f,

        // 翻译
        val translation: String? = null,
        val isTranslating: Boolean = false,

        // 跟读入口
        val showShadowing: Boolean = false,

        // 导入
        val importPath: String? = null
    )

    data class BookItem(
        val title: String,
        val author: String,
        val chapterCount: Int,
        val filePath: String,  // EPUB/TXT 副本的本地路径
        val format: String = "epub" // "epub" or "txt"
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val epubReader = EpubReader()
    private val textReader = TextReader()
    private val sentenceSplitter = SentenceSplitter()

    // ==================== 书架管理 ====================

    /**
     * 初始化：加载已导入的书架列表 + 初始化 TTS + 导入示例书。
     */
    fun initialize() {
        _uiState.update { it.copy(isLoading = true) }
        sentenceTTS.initialize()
        loadBookShelf()

        // 如果书架为空，自动导入示例 EPUB
        if (_uiState.value.books.isEmpty()) {
            importSampleEpub("midsummer.epub")
        }

        // 监听 TTS 事件
        viewModelScope.launch {
            sentenceTTS.events.consumeAsFlow().collect { event ->
                when (event) {
                    is SentenceTTS.TtsEvent.SentenceStarted -> {
                        _uiState.update {
                            it.copy(
                                isTtsSpeaking = true,
                                ttsHighlightedSentence = event.text,
                                currentSentenceIndex = event.index
                            )
                        }
                    }
                    is SentenceTTS.TtsEvent.AllSentencesCompleted -> {
                        _uiState.update {
                            it.copy(isTtsSpeaking = false, ttsHighlightedSentence = "")
                        }
                    }
                    is SentenceTTS.TtsEvent.EngineError -> {
                        _uiState.update { it.copy(error = "TTS 出错") }
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * 从文件系统加载书架。
     */
    private fun loadBookShelf() {
        val booksDir = getBooksDir()
        if (!booksDir.exists()) return

        val bookItems = booksDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    val json = file.readText()
                    val parts = json.split("|", limit = 5)
                    if (parts.size >= 4) {
                        BookItem(
                            title = parts[0],
                            author = parts[1],
                            chapterCount = parts[2].toIntOrNull() ?: 0,
                            filePath = parts[3],
                            format = parts.getOrElse(4) { "epub" }
                        )
                    } else null
                } catch (_: Exception) { null }
            } ?: emptyList()

        _uiState.update { it.copy(books = bookItems) }
    }

    /**
     * 导入 EPUB 文件。
     */
    fun importEpub(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val book = epubReader.parse(context, uri)
                val booksDir = getBooksDir()
                booksDir.mkdirs()
                val destFile = File(booksDir, "${sanitizeFileName(book.title)}.epub")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 保存元数据
                val metaFile = File(booksDir, "${sanitizeFileName(book.title)}.json")
                metaFile.writeText("${book.title}|${book.author}|${book.chapters.size}|${destFile.absolutePath}|epub")

                loadBookShelf()
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "导入失败: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * 导入 TXT 文件。
     */
    fun importTxt(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val book = textReader.parse(context, uri)
                val booksDir = getBooksDir()
                booksDir.mkdirs()
                val destFile = File(booksDir, "${sanitizeFileName(book.title)}.txt")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 保存元数据
                val metaFile = File(booksDir, "${sanitizeFileName(book.title)}.json")
                metaFile.writeText("${book.title}|${book.author}|${book.chapters.size}|${destFile.absolutePath}|txt")

                loadBookShelf()
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "导入失败: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * 打开 .txt 文件（直接路径）。
     */
    fun importTxtFromPath(filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isLoading = false, error = "文件不存在: $filePath") }
                    }
                    return@launch
                }
                val book = textReader.parseFromFile(file)
                val booksDir = getBooksDir()
                booksDir.mkdirs()
                val destFile = File(booksDir, "${sanitizeFileName(book.title)}.txt")
                file.copyTo(destFile, overwrite = true)

                val metaFile = File(booksDir, "${sanitizeFileName(book.title)}.json")
                metaFile.writeText("${book.title}|${book.author}|${book.chapters.size}|${destFile.absolutePath}|txt")

                loadBookShelf()
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "导入失败: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * 导入 assets 中的示例 EPUB。
     */
    fun importSampleEpub(assetPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val book = epubReader.parseFromAssets(context, assetPath)
                val booksDir = getBooksDir()
                booksDir.mkdirs()

                val destFile = File(booksDir, "${sanitizeFileName(book.title)}.epub")
                context.assets.open(assetPath).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val metaFile = File(booksDir, "${sanitizeFileName(book.title)}.json")
                metaFile.writeText("${book.title}|${book.author}|${book.chapters.size}|${destFile.absolutePath}|epub")

                loadBookShelf()
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "导入示例失败: ${e.message}")
                    }
                }
            }
        }
    }

    // ==================== 阅读控制 ====================

    /**
     * 打开一本书开始阅读。
     */
    fun openBook(bookItem: BookItem) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val file = File(bookItem.filePath)
                if (!file.exists()) {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isLoading = false, error = "文件不存在") }
                    }
                    return@launch
                }

                val book = if (bookItem.format == "txt") {
                    textReader.parseFromFile(file)
                } else {
                    epubReader.parseFromFile(file)
                }

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            currentBook = book,
                            currentChapterIndex = 0,
                            currentSentenceIndex = -1,
                            sentences = emptyList(),
                            translation = null,
                            isLoading = false
                        )
                    }
                }

                // 尝试恢复进度
                val progressKey = sanitizeFileName(book.title)
                val savedProgress = readingProgressDao.getProgress(progressKey)
                val startChapter = savedProgress?.chapterIndex ?: 0
                loadChapterSentences(startChapter)

                if (savedProgress != null && savedProgress.sentenceIndex >= 0) {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(currentSentenceIndex = savedProgress.sentenceIndex) }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "打开失败: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * 加载指定章节的句子。
     */
    private fun loadChapterSentences(chapterIndex: Int) {
        val book = _uiState.value.currentBook ?: return
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return
        val sentences = sentenceSplitter.splitChapter(chapter.text)

        _uiState.update {
            it.copy(
                sentences = sentences,
                currentChapterIndex = chapterIndex,
                currentSentenceIndex = -1,
                translation = null
            )
        }

        // 保存进度
        saveReadingProgress(chapterIndex, -1)
    }

    /**
     * 切换到上一章。
     */
    fun previousChapter() {
        val current = _uiState.value.currentChapterIndex
        if (current > 0) {
            sentenceTTS.stop()
            _uiState.update { it.copy(currentChapterIndex = current - 1) }
            loadChapterSentences(current - 1)
        }
    }

    /**
     * 切换到下一章。
     */
    fun nextChapter() {
        val current = _uiState.value.currentChapterIndex
        val book = _uiState.value.currentBook ?: return
        if (current < book.chapters.size - 1) {
            sentenceTTS.stop()
            _uiState.update { it.copy(currentChapterIndex = current + 1) }
            loadChapterSentences(current + 1)
        }
    }

    /**
     * 跳转到指定章节。
     */
    fun jumpToChapter(index: Int) {
        val book = _uiState.value.currentBook ?: return
        if (index in book.chapters.indices) {
            sentenceTTS.stop()
            _uiState.update { it.copy(currentChapterIndex = index) }
            loadChapterSentences(index)
        }
    }

    // ==================== TTS 控制 ====================

    /**
     * 从当前句子开始播放 TTS。
     */
    fun playTts() {
        val sentences = _uiState.value.sentences
        if (sentences.isEmpty()) return

        val startIndex = _uiState.value.currentSentenceIndex.takeIf { it >= 0 } ?: 0
        sentenceTTS.playSentences(
            sentences = sentences,
            startIndex = startIndex,
            speed = _uiState.value.playbackSpeed
        )
        _uiState.update { it.copy(isTtsSpeaking = true) }
    }

    /**
     * 暂停 TTS。
     */
    fun pauseTts() {
        sentenceTTS.pause()
        _uiState.update { it.copy(isTtsSpeaking = false) }
    }

    /**
     * 恢复 TTS。
     */
    fun resumeTts() {
        sentenceTTS.resume()
    }

    /**
     * 停止 TTS。
     */
    fun stopTts() {
        sentenceTTS.stop()
        _uiState.update { it.copy(isTtsSpeaking = false, ttsHighlightedSentence = "") }
    }

    /**
     * 跳转到指定句子并播放。
     */
    fun jumpToAndPlay(index: Int) {
        if (index in _uiState.value.sentences.indices) {
            _uiState.update { it.copy(currentSentenceIndex = index) }
            sentenceTTS.jumpToSentence(index)
            saveReadingProgress(_uiState.value.currentChapterIndex, index)
        }
    }

    /**
     * 设置播放速度。
     */
    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
        sentenceTTS.setSpeed(speed)
    }

    // ==================== 翻译 ====================

    /**
     * 翻译当前句子（复用 AiApiClient + 读取用户配置）。
     */
    fun translateCurrentSentence() {
        val index = _uiState.value.currentSentenceIndex
        if (index < 0 || index >= _uiState.value.sentences.size) return

        val text = _uiState.value.sentences[index]
        _uiState.update { it.copy(isTranslating = true) }

        viewModelScope.launch {
            try {
                // 从 AppConfig 读取 API 配置
                val configBaseUrl = appConfigDao.getValue(Constants.KEY_API_BASE_URL)?.value
                val configModel = appConfigDao.getValue(Constants.KEY_API_MODEL)?.value

                val baseUrl = configBaseUrl ?: "https://api.deepseek.com"
                val model = configModel ?: "deepseek-chat"
                val encryptedKey = appConfigDao.getValue("api_key")?.value ?: ""

                val apiKey = if (encryptedKey.isNotBlank()) {
                    try {
                        com.wordmemo.app.data.encryption.ApiKeyCipher().decrypt(encryptedKey)
                    } catch (_: Exception) {
                        ""
                    }
                } else ""

                // 如果 API Key 为空或已损坏，使用简化提示
                val systemPrompt = "You are a translator. Translate the following English text to Chinese. Return only the translation, no explanations."
                val result = if (apiKey.isNotBlank() && !apiKey.startsWith("⚠️")) {
                    aiApiClient.chatCompletion(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        model = model,
                        systemPrompt = systemPrompt,
                        userMessage = text
                    )
                } else {
                    null
                }

                if (result != null) {
                    val translation = parseTranslation(result)
                    _uiState.update { it.copy(translation = translation, isTranslating = false) }
                } else {
                    _uiState.update { it.copy(isTranslating = false, error = "翻译失败：请检查 API 配置") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isTranslating = false, error = "翻译出错: ${e.message}") }
            }
        }
    }

    /**
     * 翻译指定句子（用于长按翻译）。
     */
    fun translateSentenceAtIndex(index: Int) {
        val sentences = _uiState.value.sentences
        if (index < 0 || index >= sentences.size) return

        _uiState.update { it.copy(currentSentenceIndex = index, isTranslating = true) }

        viewModelScope.launch {
            try {
                val text = sentences[index]
                val configBaseUrl = appConfigDao.getValue(Constants.KEY_API_BASE_URL)?.value
                val configModel = appConfigDao.getValue(Constants.KEY_API_MODEL)?.value

                val baseUrl = configBaseUrl ?: "https://api.deepseek.com"
                val model = configModel ?: "deepseek-chat"
                val encryptedKey = appConfigDao.getValue("api_key")?.value ?: ""

                val apiKey = if (encryptedKey.isNotBlank()) {
                    try {
                        com.wordmemo.app.data.encryption.ApiKeyCipher().decrypt(encryptedKey)
                    } catch (_: Exception) { "" }
                } else ""

                val systemPrompt = "You are a translator. Translate the following English text to Chinese. Return only the translation, no explanations."
                val result = if (apiKey.isNotBlank() && !apiKey.startsWith("⚠️")) {
                    aiApiClient.chatCompletion(baseUrl, apiKey, model, systemPrompt, text)
                } else null

                if (result != null) {
                    val translation = parseTranslation(result)
                    _uiState.update { it.copy(translation = translation, isTranslating = false) }
                } else {
                    _uiState.update { it.copy(isTranslating = false, error = "翻译失败：请检查 API 配置") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isTranslating = false, error = "翻译出错: ${e.message}") }
            }
        }
    }

    /**
     * 简化翻译响应解析（从 chat completion JSON 中提取 content）。
     */
    private fun parseTranslation(response: String): String? {
        return try {
            val gson = com.google.gson.Gson()
            val obj = gson.fromJson(response, Map::class.java)
            val choices = obj["choices"] as? List<Map<String, Any>>
            val message = choices?.firstOrNull()?.get("message") as? Map<String, String>
            message?.get("content")?.trim()
        } catch (_: Exception) {
            response
        }
    }

    /**
     * 清除翻译。
     */
    fun clearTranslation() {
        _uiState.update { it.copy(translation = null) }
    }

    // ==================== 进度持久化 ====================

    /**
     * 保存阅读进度。
     */
    private fun saveReadingProgress(chapterIndex: Int, sentenceIndex: Int) {
        val book = _uiState.value.currentBook ?: return
        val bookKey = sanitizeFileName(book.title)
        viewModelScope.launch {
            readingProgressDao.upsertProgress(
                ReadingProgressEntity(
                    bookKey = bookKey,
                    chapterIndex = chapterIndex,
                    sentenceIndex = sentenceIndex.coerceAtLeast(0),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    // ==================== 辅助方法 ====================

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun getBooksDir(): File {
        return File(context.filesDir, "epub_books")
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_")
            .take(100)
    }

    override fun onCleared() {
        super.onCleared()
        sentenceTTS.release()
    }
}
