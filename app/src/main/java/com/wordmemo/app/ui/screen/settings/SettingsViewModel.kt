package com.wordmemo.app.ui.screen.settings

import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.wordmemo.app.data.encryption.ApiKeyCipher
import com.wordmemo.app.data.io.JsonExporter
import com.wordmemo.app.data.local.WordMemoDatabase
import com.wordmemo.app.data.local.entity.WordEntity
import com.wordmemo.app.data.network.AiApiClient
import com.wordmemo.app.data.network.NetworkMonitor
import com.wordmemo.app.data.network.UpdateChecker
import com.wordmemo.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SettingsUiState(
    val apiKey: String = "",
    val apiBaseUrl: String = "https://api.deepseek.com",
    val apiModel: String = "deepseek-chat",
    val dailyReviewLimit: Int = 13,
    val connectionTestResult: String? = null,
    val isTestingConnection: Boolean = false,
    val exportResult: String? = null,
    val isOnline: Boolean = false,
    val isCheckingUpdate: Boolean = false,
    val updateCheckResult: String? = null,
    val pendingUpdate: UpdateChecker.UpdateInfo? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WordMemoDatabase.getInstance(application)
    private val wordDao = db.wordDao()
    private val networkMonitor = NetworkMonitor(application)
    private val gson = Gson()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val isOnline = networkMonitor.isOnline()
            _uiState.value = _uiState.value.copy(isOnline = isOnline)
            loadConfig()
        }
    }

    private suspend fun loadConfig() {
        try {
            val config = db.appConfigDao().getAll()
            val cipher = ApiKeyCipher()
            _uiState.value = _uiState.value.copy(
                apiKey = config.firstOrNull { it.key == "api_key" }?.let { cipher.decrypt(it.value) } ?: "",
                apiBaseUrl = config.firstOrNull { it.key == "api_base_url" }?.value ?: "https://api.deepseek.com",
                apiModel = config.firstOrNull { it.key == "api_model" }?.value ?: "deepseek-chat",
                dailyReviewLimit = config.firstOrNull { it.key == "daily_review_limit" }?.value?.toIntOrNull() ?: 13
            )
        } catch (_: Exception) {}
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            val cipher = ApiKeyCipher()
            db.appConfigDao().setValue(com.wordmemo.app.data.local.entity.AppConfigEntity("api_key", cipher.encrypt(key)))
            _uiState.value = _uiState.value.copy(apiKey = key)
        }
    }

    fun saveApiConfig() {
        viewModelScope.launch {
            val state = _uiState.value
            val cipher = ApiKeyCipher()
            db.appConfigDao().setValue(com.wordmemo.app.data.local.entity.AppConfigEntity("api_key", cipher.encrypt(state.apiKey)))
            db.appConfigDao().setValue(com.wordmemo.app.data.local.entity.AppConfigEntity("api_base_url", state.apiBaseUrl))
            db.appConfigDao().setValue(com.wordmemo.app.data.local.entity.AppConfigEntity("api_model", state.apiModel))
            _uiState.value = state.copy(connectionTestResult = "✅ 配置已保存")
        }
    }

    fun onApiKeyChanged(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key)
    }

    fun onBaseUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(apiBaseUrl = url)
    }

    fun onModelChanged(model: String) {
        _uiState.value = _uiState.value.copy(apiModel = model)
    }

    fun onDailyLimitChanged(limit: Int) {
        _uiState.value = _uiState.value.copy(dailyReviewLimit = limit)
        viewModelScope.launch {
            db.appConfigDao().setValue(com.wordmemo.app.data.local.entity.AppConfigEntity("daily_review_limit", limit.toString()))
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.value = state.copy(isTestingConnection = true, connectionTestResult = null)
            try {
                val client = AiApiClient(gson)
                val result = client.chatCompletion(
                    baseUrl = state.apiBaseUrl, apiKey = state.apiKey,
                    model = state.apiModel, systemPrompt = "Reply OK", userMessage = "Hello"
                )
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    connectionTestResult = if (result != null) "✅ 连接成功" else "❌ 连接失败，请检查配置"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    connectionTestResult = "❌ 连接失败: ${e.message}"
                )
            }
        }
    }

    fun exportDataToUri(uri: android.net.Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            try {
                val words = wordDao.observeAll().first().map {
                    com.wordmemo.app.domain.model.Word(
                        id = it.id, english = it.english, chinese = it.chinese,
                        note = it.note, createdAt = it.createdAt, updatedAt = it.updatedAt
                    )
                }
                val json = JsonExporter(GsonBuilder().setPrettyPrinting().create())
                    .exportToJson(words, emptyList(), emptyList())
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                _uiState.value = _uiState.value.copy(exportResult = "✅ 导出成功（${words.size} 个单词）")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(exportResult = "❌ 导出失败: ${e.message}")
            }
        }
    }

    fun importCet6(context: android.content.Context) {
        viewModelScope.launch {
            try {
                val json = context.assets.open("cet6_vocab.json").bufferedReader().readText()
                val root = JsonParser.parseString(json).asJsonObject
                val wordsArray = root.getAsJsonArray("words")
                if (wordsArray != null && wordsArray.size() > 0) {
                    val count = withContext(Dispatchers.IO) {
                        var n = 0
                        for (el in wordsArray) {
                            val obj = el.asJsonObject
                            val eng = obj.get("english")?.asString ?: continue
                            val chi = obj.get("chinese")?.asString ?: ""
                            try {
                                val wid = wordDao.insert(WordEntity(
                                    english = eng, chinese = chi, note = "",
                                    createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
                                ))
                                db.flashcardDao().insert(
                                    com.wordmemo.app.data.local.entity.FlashcardEntity(
                                        wordId = wid, state = "NEW", due = System.currentTimeMillis()
                                    )
                                )
                                n++
                            } catch (_: Exception) { }
                        }
                        n
                    }
                    _uiState.value = _uiState.value.copy(exportResult = "✅ 导入六级词汇 $count/${wordsArray.size()}（去重）")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(exportResult = "❌ 导入失败: ${e.message}")
            }
        }
    }

    fun importFromJson(jsonContent: String) {
        viewModelScope.launch {
            try {
                val backup = JsonExporter().parseBackup(jsonContent)
                if (backup != null && backup.words.isNotEmpty()) {
                    val count = withContext(Dispatchers.IO) {
                        var n = 0
                        for (w in backup.words) {
                            try {
                                wordDao.insert(WordEntity(
                                    english = w.english, chinese = w.chinese,
                                    note = w.note, createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis()
                                ))
                                n++
                            } catch (_: Exception) { }
                        }
                        n
                    }
                    _uiState.value = _uiState.value.copy(exportResult = "✅ 导入成功（$count 个单词）")
                } else {
                    _uiState.value = _uiState.value.copy(exportResult = "❌ 导入失败: 文件格式不识别")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(exportResult = "❌ 导入失败: ${e.message}")
            }
        }
    }

    fun optimizeFsrs() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(connectionTestResult = "⏳ 优化中...")
                // 收集复习日志
                val logs = withContext(Dispatchers.IO) { db.reviewLogDao().getAllLogs() }
                if (logs.size < 5) {
                    _uiState.value = _uiState.value.copy(connectionTestResult = "❌ 复习数据不足（至少5次）")
                    return@launch
                }
                val reviews = logs.map {
                    com.wordmemo.app.domain.fsrs.FSRSOptimizer.ReviewData(
                        elapsedDays = it.stabilityBefore.coerceAtLeast(0.0),
                        stability = it.stabilityAfter.coerceAtLeast(0.1),
                        rating = it.rating,
                        difficulty = it.difficultyAfter.coerceIn(1.0, 10.0)
                    )
                }
                val optimizer = com.wordmemo.app.domain.fsrs.FSRSOptimizer()
                val currentW = com.wordmemo.app.domain.fsrs.FSRSDefaults.DEFAULT_PARAMS
                val optimized = optimizer.optimize(reviews, currentW.toList(), iterations = 300)

                // 存到配置
                val wJson = com.google.gson.Gson().toJson(optimized)
                withContext(Dispatchers.IO) {
                    db.appConfigDao().setValue(
                        com.wordmemo.app.data.local.entity.AppConfigEntity("fsrs_weights", wJson)
                    )
                    db.appConfigDao().setValue(
                        com.wordmemo.app.data.local.entity.AppConfigEntity("fsrs_optimized", "true")
                    )
                }
                val beforeLoss = String.format("%.4f", optimizer.logLoss(reviews, currentW.toList()))
                val afterLoss = String.format("%.4f", optimizer.logLoss(reviews, optimized))
                _uiState.value = _uiState.value.copy(
                    connectionTestResult = "✅ 优化完成！损失: $beforeLoss → $afterLoss（${logs.size}条数据）"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(connectionTestResult = "❌ 优化失败: ${e.message}")
            }
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingUpdate = true, updateCheckResult = null)
            try {
                val checker = UpdateChecker(getApplication())
                val currentVer = "1.1.0"
                val info = withContext(Dispatchers.IO) { checker.checkForUpdate(currentVer) }
                if (info.hasUpdate) {
                    _uiState.value = _uiState.value.copy(
                        isCheckingUpdate = false,
                        updateCheckResult = "发现新版本 ${info.latestVersion}",
                        pendingUpdate = info
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isCheckingUpdate = false,
                        updateCheckResult = "✓ 已是最新版本（${info.latestVersion.ifBlank { currentVer }}）"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCheckingUpdate = false,
                    updateCheckResult = "❌ 检查失败: ${e.message}"
                )
            }
        }
    }

    fun dismissUpdate() {
        _uiState.value = _uiState.value.copy(pendingUpdate = null)
    }

    fun downloadUpdate(info: UpdateChecker.UpdateInfo) {
        _uiState.value = _uiState.value.copy(pendingUpdate = null)
        viewModelScope.launch {
            try {
                val checker = UpdateChecker(getApplication())
                val fileName = "WordMemo-${info.latestVersion}.apk"
                val downloadId = checker.downloadApk(info.downloadUrl, fileName)
                _uiState.value = _uiState.value.copy(updateCheckResult = "⏳ 开始下载 ${info.latestVersion}...")

                val app = getApplication<android.app.Application>()
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                        if (id == downloadId) {
                            _uiState.value = _uiState.value.copy(updateCheckResult = "✅ 下载完成，正在安装...")
                            val apkFile = File(
                                android.os.Environment.getExternalStoragePublicDirectory(
                                    android.os.Environment.DIRECTORY_DOWNLOADS
                                ),
                                fileName
                            )
                            if (apkFile.exists()) checker.installApk(apkFile)
                            app.unregisterReceiver(this)
                        }
                    }
                }
                app.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(updateCheckResult = "❌ 下载失败: ${e.message}")
            }
        }
    }
}
