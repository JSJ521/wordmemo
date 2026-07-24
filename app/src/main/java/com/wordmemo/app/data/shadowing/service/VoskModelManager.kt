package com.wordmemo.app.data.shadowing.service

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vosk 离线语音识别模型管理器。
 *
 * 模型文件打包在 APK assets 中（vosk-model-small-en-us-0.15.zip, ~40MB），
 * 安装后从 assets 解压到 filesDir，零网络依赖。
 *
 * ## 模型来源
 * - vosk-model-small-en-us-0.15.zip（~40MB）
 * - 路径：assets/vosk-model-small-en-us-0.15.zip
 * - 首次启动自动解压到 `context.filesDir/vosk/models/vosk-model-small-en-us-0.15/`
 *
 * ## 生命周期
 * 1. [ensureModelReady] 检查模型是否存在
 * 2. 若不存在 → 从 assets 解压
 * 3. [getModelPath] 返回模型目录路径
 */
@Singleton
class VoskModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "VoskModelManager"

    companion object {
        /** 模型在 assets 中的 ZIP 文件名 */
        private const val ASSETS_ZIP_NAME = "vosk-model-small-en-us-0.15.zip"

        /** 模型解压后根目录 */
        private const val MODELS_ROOT = "vosk/models"

        /** 模型名称 */
        const val MODEL_NAME = "vosk-model-small-en-us-0.15"

        /** 解压完成标记文件名 */
        private const val EXTRACT_MARKER = ".extracted"
    }

    /** 模型目录路径 */
    private val modelDir: File
        get() = File(context.filesDir, "$MODELS_ROOT/$MODEL_NAME")

    /** 模型是否已就绪（内存缓存） */
    @Volatile
    private var _ready: Boolean = false

    // ==================== 公开 API ====================

    /**
     * 确保模型已就绪。
     * 如果模型已解压且验证通过，直接返回 true。
     * 如果模型未解压，从 assets ZIP 解压。
     */
    fun ensureModelReady(): Boolean {
        if (_ready && modelDir.exists() && modelDir.listFiles()?.isNotEmpty() == true) {
            return true
        }

        if (isModelValid()) {
            _ready = true
            Log.i(TAG, "Vosk 模型已就绪: ${modelDir.absolutePath}")
            return true
        }

        // 从 assets 解压
        return try {
            extractFromAssets()
            _ready = isModelValid()
            if (_ready) {
                Log.i(TAG, "Vosk 模型从 assets 解压成功")
            } else {
                Log.e(TAG, "Vosk 模型解压后验证失败")
            }
            _ready
        } catch (e: Exception) {
            Log.e(TAG, "Vosk 模型解压失败: ${e.message}", e)
            _ready = false
            false
        }
    }

    /**
     * 返回 Vosk 模型路径。
     * @throws IllegalStateException 如果模型尚未就绪
     */
    fun getModelPath(): String {
        if (!_ready && !isModelValid()) {
            throw IllegalStateException("Vosk 模型未就绪。请先调用 ensureModelReady()。")
        }
        return modelDir.absolutePath
    }

    /**
     * 重置就绪状态。
     */
    fun reset() {
        _ready = false
    }

    // ==================== 内部方法 ====================

    /** 校验模型目录是否完整 */
    private fun isModelValid(): Boolean {
        if (!modelDir.exists() || !modelDir.isDirectory) return false
        val requiredPaths = listOf(
            "am/final.mdl",
            "conf/mfcc.conf",
            "graph/HCLG.fst",
            "graph/words.txt"
        )
        return requiredPaths.all { relPath -> File(modelDir, relPath).exists() }
    }

    /**
     * 从 assets ZIP 解压模型到 filesDir。
     * ZIP 包名 vosk-model-small-en-us-0.15.zip 解压后目录结构保持。
     */
    private fun extractFromAssets() {
        val modelsRoot = File(context.filesDir, MODELS_ROOT)
        modelsRoot.mkdirs()

        // 清理旧的（可能不完整的）模型目录
        if (modelDir.exists()) {
            modelDir.deleteRecursively()
        }

        Log.i(TAG, "从 assets 解压 Vosk 模型...")
        val startTime = System.currentTimeMillis()

        context.assets.open(ASSETS_ZIP_NAME).use { input ->
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
                    targetFile.setReadable(true, false)
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "Vosk 模型解压完成，耗时 ${elapsed}ms")

        // 解压完成标记
        File(modelsRoot, EXTRACT_MARKER).writeText(System.currentTimeMillis().toString())
    }
}
