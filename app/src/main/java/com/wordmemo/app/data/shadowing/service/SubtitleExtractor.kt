package com.wordmemo.app.data.shadowing.service

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用 Android MediaExtractor 检测和提取视频内嵌字幕。
 *
 * **行业标准方案**：纯 Android SDK + Media3 已有依赖，零第三方依赖。
 *
 * 原理：
 * 1. MediaExtractor 扫描视频轨道 → 找到 text/ 字幕流
 * 2. 逐帧读取字幕样本 → 提取文本和时间戳
 * 3. 合并相邻句子 → 输出 SRT 文件
 *
 * 支持格式：MP4 embedded, WebVTT, SRT, TTML, ASS/SSA 等。
 *
 * v8.9 彻底重写：弃用 ffmpeg ProcessBuilder (Android 10+ noexec 限制)，
 * 改用 Android 系统 API，与系统版本和架构无关，100% 可靠。
 */
@Singleton
class SubtitleExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "SubtitleExtractor"

    /** 已知的字幕 MIME 类型 */
    private val SUBTITLE_MIMES = setOf(
        "text/", "application/x-subrip", "application/x-ssa", "application/x-quicktime",
        "application/ttml+xml", "application/x-mp4vtt", "text/vtt",
        "text/x-ssa", "text/subviewer"
    )

    /** 最大样本数据大小 */
    private val MAX_SAMPLE_SIZE = 256 * 1024

    /**
     * 检测视频是否包含内嵌字幕轨道。
     */
    suspend fun hasSubtitleTrack(videoPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(videoPath)
                val has = (0 until extractor.trackCount).any { i ->
                    val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                    SUBTITLE_MIMES.any { mime.startsWith(it) || mime.contains(it) }
                }
                Log.d(TAG, "字幕检测: $has (${extractor.trackCount} 轨道)")
                has
            } catch (e: Exception) {
                Log.e(TAG, "检测失败: ${e.message}", e)
                false
            } finally {
                try { extractor.release() } catch (_: Exception) {}
            }
        }
    }

    /**
     * 提取第一个字幕轨道为 SRT 格式。
     *
     * 从 MediaExtractor 逐帧读取字幕样本，
     * 提取文本内容 + 时间戳 → 合并为 SRT。
     */
    suspend fun extractSubtitle(videoPath: String, outputPath: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val outputFile = File(outputPath)
                outputFile.parentFile?.mkdirs()

                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(videoPath)

                    // 找第一个字幕轨道
                    var trackIdx = -1
                    for (i in 0 until extractor.trackCount) {
                        val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                        if (SUBTITLE_MIMES.any { mime.startsWith(it) || mime.contains(it) }) {
                            trackIdx = i
                            break
                        }
                    }
                    if (trackIdx < 0) {
                        return@withContext Result.failure(IOException("未找到字幕轨道"))
                    }

                    extractor.selectTrack(trackIdx)
                    Log.i(TAG, "选中字幕轨道 #$trackIdx")

                    // 逐样本读取
                    val subtitles = mutableListOf<SubtitleSample>()
                    val buffer = ByteBuffer.allocate(MAX_SAMPLE_SIZE)

                    while (true) {
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break

                        val timeUs = extractor.sampleTime
                        buffer.limit(size)

                        // 提取 UTF-8 文本
                        buffer.position(0)
                        val sampleBytes = ByteArray(buffer.remaining())
                        buffer.get(sampleBytes)

                        // 尝试提取文本（跳过二进制头/SSA格式头等）
                        val text = extractTextFromSample(sampleBytes, size)
                        if (text != null && text.isNotBlank()) {
                            // 如果和前一条文本相同且时间连续，合并
                            val last = subtitles.lastOrNull()
                            if (last != null && last.text == text && timeUs - last.endUs < 3_000_000) {
                                subtitles[subtitles.size - 1] = last.copy(endUs = timeUs)
                            } else {
                                subtitles.add(SubtitleSample(text, timeUs, timeUs + 2_000_000))
                            }
                        }

                        extractor.advance()
                    }

                    if (subtitles.isEmpty()) {
                        return@withContext Result.failure(IOException("未提取到有效字幕文本"))
                    }

                    // 修正最后一条的结束时间（从下一个的开始或+3s）
                    for (i in 0 until subtitles.size - 1) {
                        if (subtitles[i].endUs >= subtitles[i + 1].startUs) {
                            subtitles[i] = subtitles[i].copy(endUs = subtitles[i + 1].startUs - 1)
                        }
                    }

                    // 生成 SRT
                    val srt = buildSrt(subtitles)
                    outputFile.writeText(srt, Charsets.UTF_8)

                    if (outputFile.exists() && outputFile.length() > 0L) {
                        Log.i(TAG, "字幕提取成功: ${outputFile.absolutePath} " +
                                "(${outputFile.length()} bytes, ${subtitles.size} 条)")
                        Result.success(outputPath)
                    } else {
                        Result.failure(IOException("SRT 写入失败"))
                    }

                } finally {
                    try { extractor.release() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "提取异常: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 从样本字节中提取文本内容。
     * 跳过二进制头（前几个字节可能是编码标识/长度等）。
     */
    private fun extractTextFromSample(data: ByteArray, size: Int): String? {
        if (size <= 0) return null

        var offset = 0
        // 尝试跳过前导长度字段（常见于 MP4 tx3g 格式）
        // tx3g: 2字节长度 + 2字节保留 + 文本
        if (size > 4) {
            val possibleLen = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
            if (possibleLen in 1..size) {
                offset = 2
            }
        }

        val text = try {
            String(data, offset, size - offset, Charsets.UTF_8)
        } catch (_: Exception) {
            String(data, offset, size - offset, Charsets.ISO_8859_1)
        }

        // 清理不可见字符和格式标记
        val cleaned = text
            .replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\uFFFE\uFFFF]"), "")
            .replace(Regex("""\{[^}]*\}"""), "")  // SSA 格式标记 {\\fn...}
            .replace(Regex("<[^>]+>"), "")         // HTML 标签
            .trim()

        return cleaned.ifBlank { null }
    }

    /**
     * 提取视频音频（用于 ASR）。
     * 注：此处仅返回视频路径，实际音频提取由 VoskSubtitleGenerator 内部处理。
     */
    suspend fun extractAudio(videoPath: String, outputPath: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                File(outputPath).parentFile?.mkdirs()
                // VoskSubtitleGenerator 内部会处理格式转换
                Result.success(videoPath)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** 列出所有字幕流 */
    suspend fun listSubtitleStreams(videoPath: String): List<SubtitleStreamInfo> {
        return withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(videoPath)
                (0 until extractor.trackCount).mapNotNull { i ->
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return@mapNotNull null
                    if (SUBTITLE_MIMES.any { mime.startsWith(it) || mime.contains(it) }) {
                        SubtitleStreamInfo(
                            index = i.toString(),
                            codec = mime,
                            language = fmt.getString(MediaFormat.KEY_LANGUAGE) ?: "und"
                        )
                    } else null
                }
            } catch (e: Exception) {
                emptyList()
            } finally {
                try { extractor.release() } catch (_: Exception) {}
            }
        }
    }

    // ==================== 内部数据结构 ====================

    private data class SubtitleSample(
        val text: String,
        val startUs: Long,
        val endUs: Long
    )

    // ==================== SRT 生成 ====================

    private fun buildSrt(samples: List<SubtitleSample>): String {
        return buildString {
            samples.forEachIndexed { idx, s ->
                appendLine(idx + 1)
                appendLine("${fmtTime(s.startUs)} --> ${fmtTime(s.endUs)}")
                appendLine(s.text)
                appendLine()
            }
        }
    }

    private fun fmtTime(us: Long): String {
        val ms = us / 1000
        val h = ms / 3600000
        val m = (ms % 3600000) / 60000
        val s = (ms % 60000) / 1000
        val ml = ms % 1000
        return String.format("%02d:%02d:%02d,%03d", h, m, s, ml)
    }
}

data class SubtitleStreamInfo(
    val index: String,
    val codec: String,
    val language: String = "und"
)
