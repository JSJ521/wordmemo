package com.wordmemo.app.data.shadowing.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 硬字幕（Burned-in / Hardcoded Subtitles）OCR 提取器。
 *
 * 处理视频画面中直接烧录的字幕（无独立轨道，文字在像素里）。
 * 利用 Android MediaMetadataRetriever 抽帧 + Tesseract OCR 识别。
 *
 * ## 工作流程
 * 1. MediaMetadataRetriever 每秒抽 1 帧
 * 2. 裁剪画面底部 20%（字幕通常在这）
 * 3. TessBaseAPI OCR 逐帧识别
 * 4. 帧间文本对比 → 合并相同文本 → 生成 SRT 时间轴
 * 5. 输出标准 .srt 文件
 *
 * ## 依赖
 * - MediaMetadataRetriever（Android SDK，零外部依赖）
 * - TessBaseAPI（tess-two，已在项目中）
 * - eng.traineddata（已在 assets/ 中）
 *
 * ## 性能
 * - 5分钟视频：~300帧，约30秒
 * - 10分钟视频：~600帧，约60秒
 * - 全在 Dispatchers.IO 后台执行
 */
@Singleton
class HardcodedSubtitleOCR @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "HardcodedSubtitleOCR"

    companion object {
        /** 帧采样间隔（毫秒） */
        private const val FRAME_INTERVAL_MS = 1000L

        /** 字幕区域：占画面底部比例 */
        private const val SUBTITLE_REGION_RATIO = 0.20

        /** 有效字幕最小持续帧数（< 此值视为噪声丢弃） */
        private const val MIN_VALID_FRAMES = 3

        /** 相邻帧文本相似度阈值（Levenshtein 归一化后） */
        private const val SIMILARITY_THRESHOLD = 0.7f

        /** Tesseract 语言 */
        private const val TESS_LANG = "eng"

        /** tessdata 目录名 */
        private const val TESSDATA_DIR = "tessdata"
    }

    /** Tesseract 是否已初始化 */
    private var tessInitialized = false

    /**
     * 从视频中提取硬字幕。
     *
     * @param videoPath  视频文件路径
     * @param outputPath 输出的 .srt 文件路径
     * @return Success(outputPath) 或失败信息
     */
    suspend fun extractSubtitles(videoPath: String, outputPath: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                ensureTessdata()

                val outputFile = File(outputPath)
                outputFile.parentFile?.mkdirs()

                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(videoPath)

                    // 获取视频时长
                    val durationStr = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    ) ?: return@withContext Result.failure(IOException("无法获取视频时长"))
                    val durationMs = durationStr.toLongOrNull() ?: return@withContext Result.failure(
                        IOException("无效的视频时长: $durationStr")
                    )
                    val videoWidth = (retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                    )?.toIntOrNull() ?: 640)
                    val videoHeight = (retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                    )?.toIntOrNull() ?: 480)

                    Log.i(TAG, "视频信息: ${durationMs}ms, ${videoWidth}x${videoHeight}")

                    // ---- 步骤1: 逐帧 OCR ----
                    val frameResults = mutableListOf<FrameOcrResult>()

                    // 降低大分辨率视频的采样尺寸以加速 OCR
                    val scaleFactor = when {
                        videoWidth > 1920 -> 4
                        videoWidth > 1280 -> 2
                        else -> 1
                    }
                    val cropY = (videoHeight * (1.0 - SUBTITLE_REGION_RATIO)).toInt()
                    val cropH = (videoHeight * SUBTITLE_REGION_RATIO).toInt()

                    Log.i(TAG, "字幕区域: y=$cropY, h=$cropH (scale=$scaleFactor)")

                    var frameCount = 0
                    var lastText = ""
                    var lastValidText = ""
                    var consecutiveEmpty = 0

                    for (timeMs in 0L until durationMs step FRAME_INTERVAL_MS) {
                        val bitmap = retriever.getFrameAtTime(
                            timeMs * 1000,  // 微秒
                            MediaMetadataRetriever.OPTION_CLOSEST
                        ) ?: continue

                        frameCount++

                        // 裁剪字幕区域 & 缩放
                        val subtitleBitmap = cropSubtitleRegion(bitmap, cropY, cropH, scaleFactor)
                        bitmap.recycle()
                        if (subtitleBitmap == null) continue

                        // OCR
                        val text = recognizeText(subtitleBitmap).trim()
                        subtitleBitmap.recycle()

                        if (text.isBlank()) {
                            consecutiveEmpty++
                            lastText = ""
                            continue
                        }
                        consecutiveEmpty = 0

                        // 文本标准化
                        val normalized = normalizeText(text)
                        if (normalized.length < 2) continue  // 太短的忽略

                        // 与上一帧比较
                        if (lastValidText.isNotEmpty()) {
                            val similarity = textSimilarity(normalized, lastValidText)
                            if (similarity >= SIMILARITY_THRESHOLD) {
                                // 相同字幕，继续
                                lastText = normalized
                                continue
                            }
                        }

                        // 新字幕出现
                        frameResults.add(
                            FrameOcrResult(
                                text = normalized,
                                startFrameMs = timeMs,
                                endFrameMs = timeMs + FRAME_INTERVAL_MS,
                                frameIndex = frameResults.size
                            )
                        )
                        lastValidText = normalized
                        lastText = normalized

                        if (frameCount % 50 == 0) {
                            Log.d(TAG, "OCR 进度: ${frameCount}帧, 已发现 ${frameResults.size} 条字幕")
                        }
                    }

                    retriever.release()
                    Log.i(TAG, "抽帧完成: ${frameCount}帧, ${frameResults.size} 条候选字幕")

                    // ---- 步骤2: 合并相邻同文本条目 ----
                    val mergedItems = mergeAdjacentItems(frameResults)
                    Log.i(TAG, "合并后: ${mergedItems.size} 条字幕")

                    // ---- 步骤3: 过滤噪声 ----
                    val validItems = mergedItems.filter { it.frameCount >= MIN_VALID_FRAMES }
                    Log.i(TAG, "过滤后: ${validItems.size} 条有效字幕")

                    if (validItems.isEmpty()) {
                        return@withContext Result.failure(
                            IOException("OCR 未能识别到有效字幕（共 ${frameResults.size} 条候选，均因帧数不足被过滤）。可能是视频无硬字幕、或字幕不在底部区域。")
                        )
                    }

                    // ---- 步骤4: 生成 SRT ----
                    val srtContent = buildSrt(validItems)
                    outputFile.writeText(srtContent, Charsets.UTF_8)

                    if (outputFile.exists() && outputFile.length() > 0L) {
                        Log.i(TAG, "硬字幕提取成功: ${outputFile.absolutePath} " +
                                "(${outputFile.length()} bytes, ${validItems.size} 条)")
                        Result.success(outputPath)
                    } else {
                        Result.failure(IOException("SRT 文件写入失败"))
                    }

                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }

            } catch (e: Exception) {
                Log.e(TAG, "硬字幕提取异常: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    // ==================== 帧处理 ====================

    /**
     * 裁剪字幕区域（画面底部）并缩放。
     */
    private fun cropSubtitleRegion(
        bitmap: Bitmap,
        cropY: Int,
        cropH: Int,
        scaleFactor: Int
    ): Bitmap? {
        try {
            val w = bitmap.width
            val h = bitmap.height

            // 确保裁剪范围不越界
            val safeY = cropY.coerceIn(0, h - 1)
            val safeH = cropH.coerceIn(1, h - safeY)

            // 裁剪
            val cropped = Bitmap.createBitmap(bitmap, 0, safeY, w, safeH)

            // 缩放以加速 OCR
            return if (scaleFactor > 1) {
                Bitmap.createScaledBitmap(
                    cropped,
                    w / scaleFactor,
                    safeH / scaleFactor,
                    true
                )
            } else {
                cropped
            }
        } catch (e: Exception) {
            Log.w(TAG, "裁剪失败: ${e.message}")
            return null
        }
    }

    // ==================== OCR ====================

    /**
     * 确保 Tesseract 数据已就绪。
     * tessdata 从 assets 复制到 filesDir。
     */
    private fun ensureTessdata() {
        if (tessInitialized) return

        val dataDir = File(context.filesDir, TESSDATA_DIR)
        dataDir.mkdirs()

        val traineddataFile = File(dataDir, "${TESS_LANG}.traineddata")
        if (!traineddataFile.exists()) {
            Log.i(TAG, "从 assets 复制 eng.traineddata...")
            context.assets.open("${TESS_LANG}.traineddata").use { input ->
                FileOutputStream(traineddataFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "tessdata 复制完成")
        } else {
            Log.d(TAG, "tessdata 已存在")
        }
        tessInitialized = true
    }

    /**
     * 对 Bitmap 执行 OCR 识别。
     * Tesseract 要求 Bitmap 格式为 ARGB_8888，否则静默返回空字符串。
     */
    private fun recognizeText(bitmap: Bitmap): String {
        val api = TessBaseAPI()
        try {
            // 强制转换为 ARGB_8888（MediaMetadataRetriever 可能返回 RGB_565）
            val argb = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            } ?: run {
                Log.w(TAG, "Bitmap 转换失败")
                return ""
            }

            api.init(context.filesDir.absolutePath, TESS_LANG)
            api.setImage(argb)

            if (argb !== bitmap) {
                argb.recycle()
            }
            return api.getUTF8Text() ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "OCR 识别失败: ${e.message}")
            return ""
        } finally {
            try { api.end() } catch (_: Exception) {}
        }
    }

    // ==================== 文本处理 ====================

    /**
     * 标准化 OCR 文本：去除非字母字符，统一空格。
     */
    private fun normalizeText(text: String): String {
        return text
            .replace(Regex("[^a-zA-Z0-9\\s'.\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }

    /**
     * 计算两段文本的相似度（基于字符级的 Levenshtein 归一化）。
     * 1.0 = 完全相同，0.0 = 完全不同。
     */
    private fun textSimilarity(a: String, b: String): Float {
        if (a.isEmpty() && b.isEmpty()) return 1.0f
        if (a.isEmpty() || b.isEmpty()) return 0.0f

        val distance = levenshteinDistance(a, b)
        val maxLen = maxOf(a.length, b.length)
        return 1.0f - (distance.toFloat() / maxLen)
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }

    // ==================== 合并与过滤 ====================

    /**
     * 合并相邻的相同文本条目。
     * OCR 可能将一条字幕识别为多个碎片帧，合并之。
     */
    private fun mergeAdjacentItems(items: List<FrameOcrResult>): List<MergedItem> {
        if (items.isEmpty()) return emptyList()

        val merged = mutableListOf<MergedItem>()

        // 按文本分组，合并时间相邻的
        var currentGroup = mutableListOf(items[0])

        for (i in 1 until items.size) {
            val current = items[i]
            val prev = currentGroup.last()

            // 如果文本相似且时间连续→合并
            val similarity = textSimilarity(current.text, prev.text)
            val timeGap = current.startFrameMs - prev.endFrameMs

            if (similarity >= SIMILARITY_THRESHOLD && timeGap <= FRAME_INTERVAL_MS * 2) {
                currentGroup.add(current)
            } else {
                // 结束当前组，开始新组
                merged.add(mergeGroup(currentGroup))
                currentGroup = mutableListOf(current)
            }
        }
        if (currentGroup.isNotEmpty()) {
            merged.add(mergeGroup(currentGroup))
        }

        return merged
    }

    private fun mergeGroup(group: List<FrameOcrResult>): MergedItem {
        // 取出现次数最多的文本作为最终文本（众数）
        val textCounts = group.groupBy { it.text }.mapValues { it.value.size }
        val bestText = textCounts.maxByOrNull { it.value }?.key ?: group.first().text

        return MergedItem(
            text = bestText,
            startMs = group.first().startFrameMs,
            endMs = group.last().endFrameMs,
            frameCount = group.size
        )
    }

    // ==================== SRT 生成 ====================

    private fun buildSrt(items: List<MergedItem>): String {
        return buildString {
            items.forEachIndexed { idx, item ->
                appendLine(idx + 1)
                appendLine("${fmtTime(item.startMs)} --> ${fmtTime(item.endMs)}")
                appendLine(item.text)
                appendLine()
            }
        }
    }

    private fun fmtTime(ms: Long): String {
        val h = ms / 3600000
        val m = (ms % 3600000) / 60000
        val s = (ms % 60000) / 1000
        val ml = ms % 1000
        return String.format("%02d:%02d:%02d,%03d", h, m, s, ml)
    }

    // ==================== 内部数据模型 ====================

    /** 单帧 OCR 结果 */
    private data class FrameOcrResult(
        val text: String,
        val startFrameMs: Long,
        val endFrameMs: Long,
        val frameIndex: Int
    )

    /** 合并后的字幕条目 */
    data class MergedItem(
        val text: String,
        val startMs: Long,
        val endMs: Long,
        val frameCount: Int
    )
}
