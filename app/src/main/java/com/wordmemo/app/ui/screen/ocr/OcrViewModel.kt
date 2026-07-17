package com.wordmemo.app.ui.screen.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.googlecode.tesseract.android.TessBaseAPI
import com.wordmemo.app.data.local.WordMemoDatabase
import com.wordmemo.app.data.local.entity.WordEntity
import com.wordmemo.app.data.local.entity.FlashcardEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

data class OcrUiState(
    val isProcessing: Boolean = false,
    val recognizedText: String = "",
    val words: List<RecognizedWord> = emptyList(),
    val selectedWords: Set<String> = emptySet(),
    val addedCount: Int = 0,
    val fileName: String = "",
    val error: String? = null
)

data class RecognizedWord(val text: String)

class OcrViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    private var db: WordMemoDatabase? = null
    private var tessDataDir: String = ""
    private var tessDataCopied = false

    fun init(context: Context) {
        if (db == null) db = WordMemoDatabase.getInstance(context)
        tessDataDir = context.filesDir.absolutePath
        val dataDir = File(tessDataDir, "tessdata")
        dataDir.mkdirs()
        if (!File(dataDir, "eng.traineddata").exists()) {
            try {
                context.assets.open("eng.traineddata").use { input ->
                    FileOutputStream(File(dataDir, "eng.traineddata")).use { output ->
                        input.copyTo(output)
                    }
                }
                tessDataCopied = true
            } catch (e: Exception) {
                tessDataCopied = false
            }
        } else tessDataCopied = true
    }

    fun processFile(uri: Uri, context: Context, mimeType: String, fileName: String) {
        viewModelScope.launch {
            _uiState.value = OcrUiState(isProcessing = true, fileName = fileName)
            try {
                val text = when {
                    mimeType.startsWith("image/") -> processImageOcr(uri, context)
                    mimeType == "text/plain" || mimeType == "text/markdown" || fileName.endsWith(".md") || fileName.endsWith(".txt") ->
                        processTextFile(uri, context)
                    mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || fileName.endsWith(".docx") ->
                        processDocx(uri, context)
                    else -> throw Exception("不支持的文件格式: $mimeType")
                }

                val recognized = extractEnglishWords(text)
                _uiState.value = OcrUiState(
                    recognizedText = text.take(2000),
                    words = recognized,
                    fileName = fileName,
                    isProcessing = false
                )
            } catch (e: Exception) {
                _uiState.value = OcrUiState(
                    isProcessing = false, fileName = fileName,
                    error = "处理失败: ${e.message?.take(60) ?: "未知错误"}"
                )
            }
        }
    }

    private suspend fun processImageOcr(uri: Uri, context: Context): String = withContext(Dispatchers.IO) {
        if (!tessDataCopied) throw Exception("Tesseract 数据未就绪")
        val input = context.contentResolver.openInputStream(uri)
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        val bitmap = BitmapFactory.decodeStream(input, null, opts) ?: throw Exception("无法解码图片")
        val argb = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: throw Exception("格式转换失败")
        bitmap.recycle()
        recognizeText(argb)
    }

    private suspend fun processTextFile(uri: Uri, context: Context): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: throw Exception("无法读取文件")
    }

    private suspend fun processDocx(uri: Uri, context: Context): String = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: throw Exception("无法读取文件")
        val zis = ZipInputStream(input)
        var text = StringBuilder()
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                text = StringBuilder(zis.readBytes().decodeToString())
                break
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        zis.close()
        if (text.isEmpty()) throw Exception("未找到文档内容")
        // Strip XML tags
        text.toString().replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun recognizeText(bitmap: Bitmap): String {
        val api = TessBaseAPI()
        try {
            api.init(tessDataDir, "eng")
            api.setImage(bitmap)
            return api.getUTF8Text() ?: ""
        } finally {
            api.end()
            bitmap.recycle()
        }
    }

    private fun extractEnglishWords(text: String): List<RecognizedWord> {
        return text.split(Regex("[\\s,.;:!?\"'()\\[\\]{}<>/\\\\|@#\$%^&*+=~`\n\r]+"))
            .map { it.trim() }
            .filter { it.matches(Regex("^[a-zA-Z][a-zA-Z\\-']{1,44}$")) }
            .distinctBy { it.lowercase() }
            .sortedBy { it.length }
            .map { RecognizedWord(it) }
    }

    fun toggleWord(word: String) {
        val current = _uiState.value.selectedWords.toMutableSet()
        if (current.contains(word)) current.remove(word) else current.add(word)
        _uiState.value = _uiState.value.copy(selectedWords = current)
    }

    fun addSelectedWords() {
        viewModelScope.launch {
            val dao = db?.wordDao() ?: return@launch
            val flashDao = db?.flashcardDao() ?: return@launch
            var count = 0
            for (text in _uiState.value.selectedWords) {
                if (dao.getByEnglish(text) == null) {
                    val id = dao.insert(WordEntity(english = text, chinese = ""))
                    flashDao.insert(FlashcardEntity(wordId = id))
                    count++
                }
            }
            _uiState.value = _uiState.value.copy(
                selectedWords = emptySet(),
                addedCount = _uiState.value.addedCount + count
            )
        }
    }
}
