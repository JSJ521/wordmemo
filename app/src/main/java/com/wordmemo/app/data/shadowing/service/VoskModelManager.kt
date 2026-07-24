package com.wordmemo.app.data.shadowing.service

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vosk 离线语音识别模型管理器。
 *
 * 负责管理 Vosk 识别模型的下载、解压、就绪检查。
 *
 * ## 模型来源
 * 由于 Vosk 模型（~40MB）过大无法内置到 APK assets 中，采用**首次运行自动下载**策略：
 * - 下载地址：https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
 * - 模型大小：约 40MB
 * - 存储路径：`context.filesDir/vosk/models/vosk-model-small-en-us-0.15/`
 * - 下载标记：`context.filesDir/vosk/.downloaded` 记录完成状态（断点续传安全标记）
 *
 * ## 生命周期
 * 1. [ensureModelReady] 检查模型是否存在
 * 2. 若不存在 → 自动下载并解压到模型目录
 * 3. [getModelPath] 返回模型目录路径（供 Vosk Model 构造器使用）
 *
 * ## 线程安全
 * 外部调用应使用 [Dispatchers.IO]，内部操作均同步。
 *
 * ## 约束
 * - 首次运行需要网络（后续全离线）
 * - 模型一旦下载不会自动删除
 * - 模型目录结构: vosk/models/vosk-model-small-en-us-0.15/am/, conf/, graph/, ...
 */
@Singleton
class VoskModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "VoskModelManager"

    companion object {
        /** 模型存储根目录 */
        private const val MODELS_ROOT = "vosk/models"

        /** 推荐的小型英语模型名称 */
        const val MODEL_NAME = "vosk-model-small-en-us-0.15"

        /** HuggingFace 镜像下载地址（更稳定） */
        private const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/alphacep/vosk-model-small-en-us-0.15/resolve/main/vosk-model-small-en-us-0.15.zip"

        /** 回退下载地址（alphacephei.com 官方） */
        private const val FALLBACK_DOWNLOAD_URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"

        /** 下载标记文件名 */
        private const val DOWNLOAD_MARKER = ".downloaded"
    }

    /** 模型目录路径 */
    private val modelDir: File
        get() = File(context.filesDir, "$MODELS_ROOT/$MODEL_NAME")

    /** 模型是否已就绪（内存缓存，避免重复检查） */
    @Volatile
    private var _ready: Boolean = false

    // ==================== 公开 API ====================

    /**
     * 确保模型已就绪。
     *
     * 如果模型已存在且已验证，返回 true。
     * 如果模型不存在，自动从 HuggingFace 下载并解压。
     *
     * @return true 如果模型已就绪，false 如果下载失败
     */
    fun ensureModelReady(): Boolean {
        if (_ready && modelDir.exists() && modelDir.listFiles()?.isNotEmpty() == true) {
            return true
        }

        // 检查模型目录是否已完整
        if (isModelValid()) {
            _ready = true
            Log.i(TAG, "Vosk 模型已就绪: ${modelDir.absolutePath}")
            return true
        }

        // 需要下载
        return try {
            downloadAndExtract()
            _ready = isModelValid()
            if (_ready) {
                Log.i(TAG, "Vosk 模型下载并解压成功")
            } else {
                Log.e(TAG, "Vosk 模型下载后验证失败")
            }
            _ready
        } catch (e: Exception) {
            Log.e(TAG, "Vosk 模型准备失败: ${e.message}", e)
            _ready = false
            false
        }
    }

    /**
     * 返回 Vosk 模型路径（不含尾部斜杠）。
     *
     * 调用前应确保 [ensureModelReady] 已成功。
     *
     * @return 模型目录的绝对路径
     * @throws IllegalStateException 如果模型尚未就绪
     */
    fun getModelPath(): String {
        if (!_ready && !isModelValid()) {
            throw IllegalStateException(
                "Vosk 模型未就绪。请先调用 ensureModelReady() 或检查模型是否已下载。"
            )
        }
        return modelDir.absolutePath
    }

    /**
     * 重置就绪状态（当需要重新下载时调用）。
     */
    fun reset() {
        _ready = false
    }

    // ==================== 内部方法 ====================

    /**
     * 校验模型是否完整有效。
     */
    private fun isModelValid(): Boolean {
        if (!modelDir.exists()) return false
        if (!modelDir.isDirectory) return false

        // 检查关键子目录是否存在
        val requiredPaths = listOf(
            "am/final.mdl",
            "conf/mfcc.conf",
            "graph/HCLG.fst",
            "graph/words.txt"
        )
        return requiredPaths.all { relPath ->
            File(modelDir, relPath).exists()
        }
    }

    /**
     * 下载并解压 Vosk 模型 ZIP 文件。
     *
     * 流程:
     * 1. 创建临时下载文件
     * 2. 从 HuggingFace 或回退地址下载
     * 3. 校验下载完成
     * 4. 解压 ZIP 到模型目录
     * 5. 创建下载标记文件
     * 6. 清理临时文件
     */
    private fun downloadAndExtract() {
        val modelsRoot = File(context.filesDir, MODELS_ROOT)
        modelsRoot.mkdirs()

        // 清理旧的模型目录（如果有不完整的下载）
        if (modelDir.exists()) {
            modelDir.deleteRecursively()
        }

        // 尝试主下载地址，失败则回退
        var downloaded = false
        val exceptions = mutableListOf<Exception>()

        for (url in listOf(MODEL_DOWNLOAD_URL, FALLBACK_DOWNLOAD_URL)) {
            if (downloaded) break
            try {
                downloadFromUrl(url)
                downloaded = true
            } catch (e: Exception) {
                Log.w(TAG, "从 $url 下载失败: ${e.message}")
                exceptions.add(e)
            }
        }

        if (!downloaded) {
            throw RuntimeException(
                "Vosk 模型下载失败，已尝试所有下载源: ${exceptions.joinToString("; ") { it.message ?: "未知错误" }}"
            )
        }

        if (!isModelValid()) {
            throw RuntimeException("Vosk 模型下载后解压结果不完整")
        }
    }

    /**
     * 从指定 URL 下载 ZIP 并解压到模型目录。
     */
    private fun downloadFromUrl(downloadUrl: String) {
        val modelsRoot = File(context.filesDir, MODELS_ROOT)
        modelsRoot.mkdirs()

        Log.i(TAG, "开始下载 Vosk 模型: $downloadUrl")

        val connection = URL(downloadUrl).openConnection() as HttpURLConnection
        connection.apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("User-Agent", "WordMemo/8.8")
            instanceFollowRedirects = true
        }

        connection.connect()
        val contentLength = connection.contentLengthLong
        Log.i(TAG, "模型文件大小: ${contentLength / 1024 / 1024}MB")

        connection.inputStream.use { input ->
            val zipStream = ZipInputStream(input)
            var entry = zipStream.nextEntry
            val buffer = ByteArray(8192)

            while (entry != null) {
                if (!entry.isDirectory) {
                    val targetFile = File(modelsRoot, entry.name)
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).use { output ->
                        var bytesRead: Int
                        while (zipStream.read(buffer).also { bytesRead = it } >= 0) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                    // 设置可读权限
                    targetFile.setReadable(true, false)
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        }

        connection.disconnect()

        // 创建下载完成标记
        File(modelsRoot, DOWNLOAD_MARKER).writeText(System.currentTimeMillis().toString())

        Log.i(TAG, "Vosk 模型下载解压完成")
    }
}
