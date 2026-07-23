package com.wordmemo.app.data.shadowing.service

import android.util.Log
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube 视频信息——由 [YouTubeParser.parse] 返回
 */
data class YouTubeVideoInfo(
    val title: String,
    val videoId: String,
    /** 直接可用的视频流 URL */
    val streamUrl: String? = null,
    /** 音频流 URL（如果音视频分离） */
    val audioStreamUrl: String? = null,
    /** 字幕 URL 列表 */
    val subtitleUrls: List<YouTubeCaptionInfo> = emptyList(),
    /** 视频时长（秒） */
    val durationSeconds: Long = 0
)

/**
 * YouTube 字幕信息
 */
data class YouTubeCaptionInfo(
    val languageCode: String,
    val label: String,
    val url: String
)

/**
 * YouTube 视频直接解析器——通过公共 Invidious 实例获取视频流和字幕，
 * 无需 API Key 或 cookie（仅限公开视频）。
 *
 * ## 策略
 * 1. 尝试 Invidious 公共实例获取视频信息（视频直链 + 字幕）
 * 2. Invidious 失败时回退到 yt-dlp（已集成）
 *
 * Invidious 是免费、开源的 YouTube 前端代理，提供 REST API。
 */
@Singleton
class YouTubeParser @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: android.content.Context
) {
    private val TAG = "YouTubeParser"

    companion object {
        /** 可用的 Invidious 公共实例列表（按可靠性排序） */
        private val INVIOUS_INSTANCES = listOf(
            "https://invidious.snopyta.org",
            "https://yewtu.be",
            "https://inv.nadeko.net",
            "https://invidious.privacydev.net",
            "https://invidious.namazso.eu"
        )

        /** YouTube 视频 URL 正则 */
        private val YT_REGEX = Regex(
            """(?:youtube\.com/watch\?.*v=|youtu\.be/|youtube\.com/embed/|youtube\.com/shorts/)([a-zA-Z0-9_-]{11})"""
        )
    }

    private val apiClient = okHttpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val streamClient = okHttpClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // ====================================================================
    // 公开 API
    // ====================================================================

    /**
     * 从 YouTube URL 提取视频 ID。
     *
     * 支持格式:
     * - https://www.youtube.com/watch?v=dQw4w9WgXcQ
     * - https://youtu.be/dQw4w9WgXcQ
     * - https://www.youtube.com/embed/dQw4w9WgXcQ
     * - https://www.youtube.com/shorts/dQw4w9WgXcQ
     */
    fun extractVideoId(url: String): String? {
        return YT_REGEX.find(url)?.groupValues?.getOrNull(1)
    }

    /**
     * 解析 YouTube 视频信息。
     *
     * 策略：依次尝试 Invidious 实例，第一个成功则返回。
     *
     * @param url YouTube 视频链接
     * @return 包含视频流 URL 和字幕信息的 [YouTubeVideoInfo]
     */
    suspend fun parse(url: String): Result<YouTubeVideoInfo> = withContext(Dispatchers.IO) {
        try {
            val videoId = extractVideoId(url)
                ?: return@withContext Result.failure(
                    VideoImportException.UnsupportedUrlException("无法从URL提取视频ID: $url")
                )

            Log.i(TAG, "解析 YouTube 视频: $videoId")

            // 尝试 Invidious 实例
            for (instance in INVIOUS_INSTANCES) {
                try {
                    val result = parseFromInvidious(instance, videoId)
                    if (result.isSuccess) {
                        val info = result.getOrThrow()
                        Log.i(TAG, "Invidious [$instance] 解析成功: ${info.title}")
                        return@withContext Result.success(info)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Invidious [$instance] 失败: ${e.message}")
                }
            }

            // 所有 Invidious 实例都失败
            Log.w(TAG, "所有 Invidious 实例均失败，视频 $videoId 无法直接解析")
            return@withContext Result.failure(
                VideoImportException.NetworkException(
                    "无法通过公开 API 获取 YouTube 视频信息。请换用链接下载方式（yt-dlp）。"
                )
            )
        } catch (e: VideoImportException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "YouTube 解析失败: ${e.message}", e)
            Result.failure(
                VideoImportException.NetworkException("YouTube 解析失败: ${e.message}", e)
            )
        }
    }

    /**
     * 从单个 Invidious 实例获取视频信息。
     */
    private suspend fun parseFromInvidious(
        instance: String,
        videoId: String
    ): Result<YouTubeVideoInfo> {
        val json = apiGet("$instance/api/v1/videos/$videoId")
        val root = JsonParser.parseString(json).asJsonObject

        val title = root.get("title")?.asString ?: "未知标题"
        val lengthSeconds = root.get("lengthSeconds")?.asLong ?: 0L

        // 解析格式流（获取直接 URL）
        var bestStreamUrl: String? = null
        var bestAudioUrl: String? = null
        var bestQuality = 0

        val formatStreams = root.getAsJsonArray("formatStreams")
        if (formatStreams != null) {
            for (item in formatStreams) {
                val obj = item.asJsonObject
                val url = obj.get("url")?.asString ?: continue
                val quality = obj.get("qualityLabel")?.asString ?: ""

                val isAudio = quality.contains("audio", ignoreCase = true) ||
                    obj.get("encoding")?.asString?.contains("mp4a", ignoreCase = true) == true

                if (isAudio) {
                    if (bestAudioUrl == null) bestAudioUrl = url
                } else {
                    // 视频流：取最清晰的
                    val qScore = getQualityScore(quality)
                    if (qScore > bestQuality) {
                        bestQuality = qScore
                        bestStreamUrl = url
                    }
                }
            }
        }

        // 如果 formatStreams 没有视频，尝试 adaptiveFormats
        if (bestStreamUrl == null) {
            val adaptiveFormats = root.getAsJsonArray("adaptiveFormats")
            if (adaptiveFormats != null) {
                for (item in adaptiveFormats) {
                    val obj = item.asJsonObject
                    val url = obj.get("url")?.asString ?: continue
                    val mime = obj.get("type")?.asString ?: obj.get("mimeType")?.asString ?: ""

                    if (mime.startsWith("video/") && bestStreamUrl == null) {
                        bestStreamUrl = url
                    } else if (mime.startsWith("audio/") && bestAudioUrl == null) {
                        bestAudioUrl = url
                    }
                }
            }
        }

        // 如果还没有视频 URL，用 formatStreams 中的第一个
        if (bestStreamUrl == null && formatStreams != null && formatStreams.size() > 0) {
            bestStreamUrl = formatStreams[0].asJsonObject.get("url")?.asString
        }

        // 解析字幕
        val captions = mutableListOf<YouTubeCaptionInfo>()
        try {
            val captionArr = root.getAsJsonArray("captions")
            if (captionArr != null) {
                for (item in captionArr) {
                    val obj = item.asJsonObject
                    val langCode = obj.get("languageCode")?.asString ?: continue
                    val label = obj.get("label")?.asString ?: langCode
                    val captionUrl = obj.get("url")?.asString
                        ?: obj.get("captionUrl")?.asString
                        ?: continue

                    captions.add(YouTubeCaptionInfo(
                        languageCode = langCode,
                        label = label,
                        url = captionUrl
                    ))
                }
            }
        } catch (_: Exception) {
            Log.w(TAG, "字幕解析失败（可能无字幕）")
        }

        if (bestStreamUrl == null) {
            return Result.failure(IOException("Invidious 未返回可用的视频流地址"))
        }

        return Result.success(YouTubeVideoInfo(
            title = title,
            videoId = videoId,
            streamUrl = bestStreamUrl,
            audioStreamUrl = bestAudioUrl,
            subtitleUrls = captions,
            durationSeconds = lengthSeconds
        ))
    }

    /**
     * 下载 YouTube 视频。
     *
     * @param info       [parse] 返回的视频信息
     * @param outputPath 输出文件路径
     * @return 成功返回 outputPath
     */
    suspend fun downloadVideo(info: YouTubeVideoInfo, outputPath: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (info.streamUrl == null) {
                    return@withContext Result.failure(
                        IOException("没有可用的视频流 URL")
                    )
                }

                val outputFile = File(outputPath)
                outputFile.parentFile?.mkdirs()

                Log.i(TAG, "下载 YouTube 视频流...")
                downloadStream(info.streamUrl, outputFile.absolutePath)

                if (outputFile.exists() && outputFile.length() > 0) {
                    Log.i(TAG, "YouTube 下载完成: ${outputFile.absolutePath} (${outputFile.length()})")
                    Result.success(outputPath)
                } else {
                    Result.failure(IOException("视频下载失败: 文件为空"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "YouTube 视频下载失败: ${e.message}", e)
                Result.failure(
                    VideoImportException.VideoDownloadException("YouTube 视频下载失败: ${e.message}", e)
                )
            }
        }

    /**
     * 下载 YouTube 字幕。
     *
     * 尝试优先中文/英文字幕，转为 SRT 格式。
     *
     * @param info       [parse] 返回的视频信息
     * @param outputPath 输出的 SRT 文件路径
     * @return 成功返回 SRT 路径；无可用字幕返回 null
     */
    suspend fun downloadSubtitle(info: YouTubeVideoInfo, outputPath: String): Result<String?> =
        withContext(Dispatchers.IO) {
            try {
                if (info.subtitleUrls.isEmpty()) {
                    Log.i(TAG, "无可用字幕")
                    return@withContext Result.success(null)
                }

                // 优先中文、英文、第一个
                val preferred = info.subtitleUrls.find { it.languageCode.startsWith("zh") }
                    ?: info.subtitleUrls.find { it.languageCode.startsWith("en") }
                    ?: info.subtitleUrls.first()

                Log.i(TAG, "下载字幕: ${preferred.label} (${preferred.languageCode})")

                // Invidious 字幕 URL 返回 VTT 格式
                // 尝试先请求 SRT 格式
                val srtUrl = if (preferred.url.contains("?")) {
                    "${preferred.url}&format=srt"
                } else {
                    "${preferred.url}?format=srt"
                }

                val subtitleContent = try {
                    // 先试 SRT
                    val content = apiGet(srtUrl)
                    if (content.isNotBlank() && !content.startsWith("WEBVTT")) {
                        content
                    } else {
                        // 回退到 VTT
                        apiGet(preferred.url)
                    }
                } catch (_: Exception) {
                    // 回退到原 URL
                    apiGet(preferred.url)
                }

                if (subtitleContent.isBlank()) {
                    return@withContext Result.success(null)
                }

                // 转换为标准 SRT
                val srtContent = convertToSrt(subtitleContent, preferred.url)

                if (srtContent.isNullOrBlank()) {
                    return@withContext Result.success(null)
                }

                val outputFile = File(outputPath)
                outputFile.parentFile?.mkdirs()
                outputFile.writeText(srtContent, Charsets.UTF_8)

                Log.i(TAG, "字幕已保存: $outputPath (${outputFile.length()} bytes)")
                Result.success(outputFile.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "字幕下载失败: ${e.message}")
                Result.success(null) // 字幕失败不阻塞主流程
            }
        }

    // ====================================================================
    // 内部方法
    // ====================================================================

    /**
     * 执行 HTTP GET 请求。
     */
    private fun apiGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val response = apiClient.newCall(request).execute()
        val body = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            throw IOException("API 返回 ${response.code}: ${body.take(200)}")
        }

        return body
    }

    /**
     * 下载流到本地文件。
     */
    private fun downloadStream(url: String, outputPath: String) {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val response = streamClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("流下载返回 ${response.code}")
        }

        val body = response.body ?: throw IOException("响应体为空")
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()

        body.byteStream().use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * 将 Invidious 返回的字幕（VTT/SRT/JSON格式）转为标准 SRT。
     */
    private fun convertToSrt(content: String, sourceUrl: String): String? {
        val trimmed = content.trim()

        // 已经是 SRT
        if (!trimmed.startsWith("WEBVTT", ignoreCase = true) &&
            trimmed.contains("-->") &&
            Regex("""^\d+$""").containsMatchIn(trimmed.lines().firstOrNull()?.trim() ?: "")
        ) {
            return trimmed
        }

        // VTT 格式
        if (trimmed.startsWith("WEBVTT", ignoreCase = true)) {
            return convertVttToSrt(trimmed)
        }

        // JSON 格式（Invidious 某些版本）
        if (trimmed.startsWith("{")) {
            return convertJsonCaptionsToSrt(trimmed)
        }

        // 未知格式，直接返回
        return trimmed
    }

    /**
     * VTT → SRT 转换
     */
    private fun convertVttToSrt(vtt: String): String {
        var text = vtt.trim()
        // 去掉 WEBVTT 头部
        if (text.startsWith("WEBVTT", ignoreCase = true)) {
            val idx = text.indexOf('\n')
            text = if (idx > 0) text.substring(idx + 1).trim() else ""
        }

        // 移除 STYLE、NOTE 块
        text = text.replace(Regex("STYLE[\\s\\S]*?\\n\\n"), "")
        text = text.replace(Regex("NOTE[\\s\\S]*?\\n\\n"), "")

        val sb = StringBuilder()
        var index = 1
        val blocks = text.split(Regex("\\n\\s*\\n"))

        for (block in blocks) {
            val lines = block.trim().lines()
            if (lines.isEmpty()) continue

            // 找到时间行
            var timeLineIdx = -1
            for (i in lines.indices) {
                if (lines[i].contains("-->")) {
                    timeLineIdx = i
                    break
                }
            }
            if (timeLineIdx < 0) continue

            val timeLine = lines[timeLineIdx]
                .replace('.', ',')  // VTT 用点，SRT 用逗号
            val textLines = lines.drop(timeLineIdx + 1)
                .joinToString("\n")
                .replace(Regex("<[^>]+>"), "")
                .trim()

            if (textLines.isNotBlank()) {
                sb.appendLine(index++)
                sb.appendLine(timeLine)
                sb.appendLine(textLines)
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    /**
     * Invidious JSON 字幕格式 → SRT
     * {"events": [{"tStartMs": 500, "dDurationMs": 2500, "segs": [{"utf8": "Hello"}]}]}
     * 或 Invidious 的 caption 响应
     */
    private fun convertJsonCaptionsToSrt(json: String): String? {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val events = root.getAsJsonArray("events") ?: return null

            val sb = StringBuilder()
            var index = 1

            for (element in events) {
                val obj = element.asJsonObject
                val tStartMs = obj.get("tStartMs")?.asLong
                    ?: obj.get("startMs")?.asLong
                    ?: continue
                val dDurationMs = obj.get("dDurationMs")?.asLong
                    ?: obj.get("durMs")?.asLong
                    ?: 5000L

                val segs = obj.getAsJsonArray("segs") ?: continue
                val text = segs.mapNotNull { seg ->
                    seg.asJsonObject.get("utf8")?.asString
                }.joinToString("").trim()

                if (text.isBlank()) continue

                sb.appendLine(index++)
                sb.appendLine("${formatJsonTime(tStartMs)} --> ${formatJsonTime(tStartMs + dDurationMs)}")
                sb.appendLine(text)
                sb.appendLine()
            }

            if (sb.isEmpty()) null else sb.toString()
        } catch (e: Exception) {
            Log.w(TAG, "JSON 字幕转换失败: ${e.message}")
            null
        }
    }

    /**
     * 将毫秒数转为 SRT 时间格式
     */
    private fun formatJsonTime(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val secs = (ms % 60000) / 1000
        val millis = ms % 1000
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, secs, millis)
    }

    /**
     * 根据清晰度标签计算优先级分数。
     */
    private fun getQualityScore(label: String): Int {
        return when {
            label.contains("2160") || label.contains("4k", ignoreCase = true) -> 100
            label.contains("1440") || label.contains("2k", ignoreCase = true) -> 80
            label.contains("1080") -> 60
            label.contains("720")  -> 40
            label.contains("480")  -> 20
            label.contains("360")  -> 10
            else -> 0
        }
    }
}
