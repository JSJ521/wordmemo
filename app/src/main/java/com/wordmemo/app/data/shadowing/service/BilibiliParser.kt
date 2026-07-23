package com.wordmemo.app.data.shadowing.service

import android.content.Context
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
 * B站视频信息——由 [BilibiliParser.parse] 返回
 */
data class BilibiliVideoInfo(
    val title: String,
    val bvid: String,
    val cid: Long,
    /** DASH 视频流 URL（纯视频，无音频） */
    val videoUrl: String? = null,
    /** DASH 音频流 URL */
    val audioUrl: String? = null,
    /** 旧格式单流 URL（视频+音频合一），无 DASH 时使用 */
    val combinedUrl: String? = null,
    /** 字幕列表 */
    val subtitles: List<BilibiliSubtitleInfo> = emptyList(),
    /** 视频时长（秒） */
    val durationSeconds: Long = 0
)

/**
 * B站字幕信息
 */
data class BilibiliSubtitleInfo(
    val language: String,
    val url: String
)

/**
 * B站视频直接解析器——通过 B站公开 REST API 获取视频流和字幕，
 * 无需用户提供 cookie 或登录信息（仅限公开视频）。
 *
 * ## 工作流程
 * 1. [extractBvId]——从 URL 提取 BV 号
 * 2. [parse]——调用 view API 获取 cid、标题、字幕列表
 * 3. [parse]——调用 playurl API 获取视频/音频流地址
 * 4. [downloadVideo]——下载 DASH 流并用 ffmpeg 合并
 * 5. [downloadSubtitle]——下载字幕 JSON 并转为 SRT
 */
@Singleton
class BilibiliParser @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) {
    private val TAG = "BilibiliParser"

    companion object {
        /** B站视频信息 API（无需 cookie） */
        private const val API_VIEW = "https://api.bilibili.com/x/web-interface/view"
        /** B站播放地址 API */
        private const val API_PLAYURL = "https://api.bilibili.com/x/player/playurl"

        /** 1080P 清晰度 */
        private const val QN_1080P = 80
        /** 720P 清晰度 */
        private const val QN_720P = 64
        /** 480P 清晰度 */
        private const val QN_480P = 32

        /** 默认 UA */
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        /** B站 API 签名用的 Referer */
        private const val REFERER = "https://www.bilibili.com"
    }

    /** 自定义 OkHttpClient（超时更短，重试） */
    private val apiClient = okHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** 流下载用 Client（长时间读取） */
    private val streamClient = okHttpClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** ffmpeg 可执行文件路径（由 yt-dlp android 库安装） */
    private val ffmpegPath: String?
        get() {
            val ffDir = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg")
            val ff = File(ffDir, "ffmpeg")
            return if (ff.exists()) ff.absolutePath else null
        }

    // ====================================================================
    // 公开 API
    // ====================================================================

    /**
     * 从 B站 URL 提取 BV 号。
     *
     * 支持格式:
     * - https://www.bilibili.com/video/BV1xx411c7mD
     * - https://b23.tv/xxxx (短链接 — 会跟随重定向)
     * - BV1xx411c7mD (纯 BV 号)
     */
    fun extractBvId(url: String): String? {
        // 纯 BV 号
        val bvMatch = Regex("BV[a-zA-Z0-9]{10,}").find(url)
        if (bvMatch != null) return bvMatch.value

        // 短链接 b23.tv — 需要解析重定向
        if (url.contains("b23.tv")) {
            Log.i(TAG, "检测到 b23.tv 短链接，需要跟随重定向")
            // 返回占位符，调用方应重试
            return null
        }

        return null
    }

    /**
     * 跟随 b23.tv 短链接重定向，提取最终 URL 中的 BV 号。
     */
    suspend fun resolveShortUrl(shortUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(shortUrl)
                .addHeader("User-Agent", UA)
                .addHeader("Referer", REFERER)
                .head()  // HEAD 请求即可
                .build()

            val response = apiClient.newCall(request).execute()
            val finalUrl = response.request.url.toString()
            Log.i(TAG, "短链接重定向到: $finalUrl")

            val bvId = extractBvId(finalUrl)
            if (bvId != null) {
                Result.success(bvId)
            } else {
                Result.failure(IOException("无法从重定向 URL 提取 BV 号: $finalUrl"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "短链接解析失败: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 从 B站 URL 解析视频信息（标题、CID、视频流地址、字幕）。
     *
     * @param bvUrl B站视频链接 (含 BV 号)
     * @return 包含视频流 URL 和字幕信息的 [BilibiliVideoInfo]
     */
    suspend fun parse(bvUrl: String): Result<BilibiliVideoInfo> = withContext(Dispatchers.IO) {
        try {
            // Step 1: 提取 BV 号
            val bvId = extractBvId(bvUrl)
                ?: return@withContext Result.failure(
                    VideoImportException.UnsupportedUrlException("无法从URL提取BV号: $bvUrl")
                )

            // Step 2: 获取视频信息 (CID + 标题 + 字幕)
            val viewUrl = "$API_VIEW?bvid=$bvId"
            val viewJson = apiGet(viewUrl)

            val viewRoot = JsonParser.parseString(viewJson).asJsonObject
            val viewCode = viewRoot.get("code")?.asInt ?: -1
            if (viewCode != 0) {
                val msg = viewRoot.get("message")?.asString ?: "unknown"
                return@withContext Result.failure(
                    VideoImportException.NetworkException("B站API返回错误: code=$viewCode, message=$msg")
                )
            }

            val data = viewRoot.getAsJsonObject("data")
            val title = data?.get("title")?.asString ?: "未命名视频"
            val cid = data?.get("cid")?.asLong ?: 0L
            val duration = data?.get("duration")?.asLong ?: 0L

            // 解析字幕信息
            val subtitleList = mutableListOf<BilibiliSubtitleInfo>()
            try {
                val subtitleObj = data?.getAsJsonObject("subtitle")
                val subtitles = subtitleObj?.getAsJsonArray("subtitles")
                if (subtitles != null) {
                    for (item in subtitles) {
                        val obj = item.asJsonObject
                        val lang = obj.get("lan_doc")?.asString ?: "unknown"
                        val subUrl = obj.get("subtitle_url")?.asString ?: continue
                        val fullUrl = if (subUrl.startsWith("http")) subUrl
                        else "https:$subUrl"
                        subtitleList.add(BilibiliSubtitleInfo(lang, fullUrl))
                    }
                }
            } catch (_: Exception) {
                Log.w(TAG, "解析字幕信息失败（可能无字幕）")
            }

            // Step 3: 获取视频流地址
            val playUrl = "$API_PLAYURL?bvid=$bvId&cid=$cid&qn=$QN_1080P&fnver=0&fnval=4048&fourk=1"
            val playJson = apiGet(playUrl)

            val playRoot = JsonParser.parseString(playJson).asJsonObject
            val playCode = playRoot.get("code")?.asInt ?: -1
            if (playCode != 0) {
                // 1080P 失败，降级到 720P
                Log.w(TAG, "1080P获取失败(code=$playCode)，降级到720P")
                return@withContext parseWithQuality(bvId, cid, QN_720P, title, duration, subtitleList)
            }

            val playData = playRoot.getAsJsonObject("data")

            // 尝试 DASH（音视频分离）
            var videoUrl: String? = null
            var audioUrl: String? = null
            var combinedUrl: String? = null

            val dash = playData?.getAsJsonObject("dash")
            if (dash != null) {
                // DASH 格式 — 首选
                val videos = dash.getAsJsonArray("video")
                if (videos != null && videos.size() > 0) {
                    // 取最高画质的视频流
                    var bestVideo = videos[0].asJsonObject
                    for (i in 1 until videos.size()) {
                        val v = videos[i].asJsonObject
                        val curId = v.get("id")?.asInt ?: 0
                        val bestId = bestVideo.get("id")?.asInt ?: 0
                        if (curId > bestId) bestVideo = v
                    }
                    videoUrl = bestVideo.get("baseUrl")?.asString
                        ?: bestVideo.get("base_url")?.asString
                }

                val audios = dash.getAsJsonArray("audio")
                if (audios != null && audios.size() > 0) {
                    // 取最高码率音频
                    var bestAudio = audios[0].asJsonObject
                    for (i in 1 until audios.size()) {
                        val a = audios[i].asJsonObject
                        val curBr = a.get("bandwidth")?.asInt ?: 0
                        val bestBr = bestAudio.get("bandwidth")?.asInt ?: 0
                        if (curBr > bestBr) bestAudio = a
                    }
                    audioUrl = bestAudio.get("baseUrl")?.asString
                        ?: bestAudio.get("base_url")?.asString
                }
            }

            // 回退：旧格式 DURL（单流，音视频已合并）
            if (videoUrl == null) {
                val durl = playData?.getAsJsonArray("durl")
                if (durl != null && durl.size() > 0) {
                    val first = durl[0].asJsonObject
                    combinedUrl = first.get("url")?.asString
                        ?: first.get("backup_url")?.asJsonArray?.get(0)?.asString
                }
            }

            if (videoUrl == null && combinedUrl == null) {
                return@withContext Result.failure(
                    VideoImportException.NetworkException("无法获取视频流地址（可能需登录）")
                )
            }

            val info = BilibiliVideoInfo(
                title = title,
                bvid = bvId,
                cid = cid,
                videoUrl = videoUrl,
                audioUrl = audioUrl,
                combinedUrl = combinedUrl,
                subtitles = subtitleList,
                durationSeconds = duration
            )

            Log.i(TAG, "解析成功: $title (BV:$bvId, DASH=${videoUrl != null}, 字幕=${subtitleList.size}条)")
            Result.success(info)
        } catch (e: VideoImportException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "B站解析失败: ${e.message}", e)
            Result.failure(
                VideoImportException.NetworkException("B站解析失败: ${e.message}", e)
            )
        }
    }

    /**
     * 用指定画质重新解析（降级用）
     */
    private suspend fun parseWithQuality(
        bvId: String,
        cid: Long,
        qn: Int,
        title: String,
        duration: Long,
        subtitles: List<BilibiliSubtitleInfo>
    ): Result<BilibiliVideoInfo> {
        val playUrl = "$API_PLAYURL?bvid=$bvId&cid=$cid&qn=$qn&fnver=0&fnval=4048"
        val playJson = apiGet(playUrl)
        val playRoot = JsonParser.parseString(playJson).asJsonObject
        val playData = playRoot.getAsJsonObject("data")

        var videoUrl: String? = null
        var audioUrl: String? = null
        var combinedUrl: String? = null

        val dash = playData?.getAsJsonObject("dash")
        if (dash != null) {
            val videos = dash.getAsJsonArray("video")
            if (videos != null && videos.size() > 0) {
                var bestVideo = videos[0].asJsonObject
                for (i in 1 until videos.size()) {
                    val v = videos[i].asJsonObject
                    if ((v.get("id")?.asInt ?: 0) > (bestVideo.get("id")?.asInt ?: 0))
                        bestVideo = v
                }
                videoUrl = bestVideo.get("baseUrl")?.asString ?: bestVideo.get("base_url")?.asString
            }
            val audios = dash.getAsJsonArray("audio")
            if (audios != null && audios.size() > 0) {
                var bestAudio = audios[0].asJsonObject
                for (i in 1 until audios.size()) {
                    val a = audios[i].asJsonObject
                    if ((a.get("bandwidth")?.asInt ?: 0) > (bestAudio.get("bandwidth")?.asInt ?: 0))
                        bestAudio = a
                }
                audioUrl = bestAudio.get("baseUrl")?.asString ?: bestAudio.get("base_url")?.asString
            }
        }

        val durl = playData?.getAsJsonArray("durl")
        if (videoUrl == null && durl != null && durl.size() > 0) {
            combinedUrl = durl[0].asJsonObject.get("url")?.asString
        }

        if (videoUrl == null && combinedUrl == null) {
            return Result.failure(
                VideoImportException.NetworkException("降级后仍无法获取视频流（可能需要登录）")
            )
        }

        return Result.success(BilibiliVideoInfo(
            title = title,
            bvid = bvId,
            cid = cid,
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            combinedUrl = combinedUrl,
            subtitles = subtitles,
            durationSeconds = duration
        ))
    }

    /**
     * 下载视频（DASH 流分别下载并用 ffmpeg 合并，或直接下载旧格式单流）。
     *
     * @param info       [parse] 返回的视频信息
     * @param outputPath 合并后的 MP4 输出路径
     * @return 成功返回 outputPath
     */
    suspend fun downloadVideo(info: BilibiliVideoInfo, outputPath: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val outputFile = File(outputPath)
                outputFile.parentFile?.mkdirs()

                if (info.combinedUrl != null) {
                    // 旧格式：单流直接下载
                    Log.i(TAG, "下载合并流: ${info.combinedUrl.take(80)}...")
                    downloadStream(info.combinedUrl, outputPath)
                    if (outputFile.exists() && outputFile.length() > 0) {
                        Log.i(TAG, "下载完成: ${outputFile.absolutePath} (${outputFile.length()})")
                        return@withContext Result.success(outputPath)
                    } else {
                        return@withContext Result.failure(
                            IOException("合并流下载失败: 文件为空")
                        )
                    }
                }

                if (info.videoUrl == null) {
                    return@withContext Result.failure(
                        IOException("没有可用的视频流 URL")
                    )
                }

                // DASH：分别下载视频和音频，然后合并
                val videoFile = File(outputFile.parentFile, "video_${System.currentTimeMillis()}.m4s")
                val audioFile = File(outputFile.parentFile, "audio_${System.currentTimeMillis()}.m4s")

                Log.i(TAG, "下载 DASH 视频流...")
                downloadStream(info.videoUrl, videoFile.absolutePath)

                if (!videoFile.exists() || videoFile.length() == 0L) {
                    return@withContext Result.failure(IOException("视频流下载失败"))
                }

                if (info.audioUrl != null) {
                    Log.i(TAG, "下载 DASH 音频流...")
                    downloadStream(info.audioUrl, audioFile.absolutePath)

                    if (!audioFile.exists() || audioFile.length() == 0L) {
                        Log.w(TAG, "音频流下载失败，仅保留视频（无声）")
                        // 仅复制视频文件
                        videoFile.copyTo(outputFile, overwrite = true)
                    } else {
                        // 用 ffmpeg 合并
                        val ffmpeg = ffmpegPath
                        if (ffmpeg != null) {
                            Log.i(TAG, "用 ffmpeg 合并音视频...")
                            val mergeResult = mergeVideoAudio(
                                ffmpeg = ffmpeg,
                                videoPath = videoFile.absolutePath,
                                audioPath = audioFile.absolutePath,
                                outputPath = outputPath
                            )
                            if (!mergeResult) {
                                Log.w(TAG, "ffmpeg 合并失败，仅保留视频流")
                                videoFile.copyTo(outputFile, overwrite = true)
                            }
                        } else {
                            Log.w(TAG, "ffmpeg 不可用，仅保留视频流（无声）")
                            videoFile.copyTo(outputFile, overwrite = true)
                        }
                    }
                } else {
                    // 无音频流，直接复制视频
                    videoFile.copyTo(outputFile, overwrite = true)
                }

                // 清理临时文件
                videoFile.delete()
                audioFile.delete()

                if (outputFile.exists() && outputFile.length() > 0) {
                    Log.i(TAG, "下载完成: ${outputFile.absolutePath} (${outputFile.length()})")
                    Result.success(outputPath)
                } else {
                    Result.failure(IOException("输出文件为空或不存在"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "视频下载失败: ${e.message}", e)
                Result.failure(
                    VideoImportException.VideoDownloadException("B站视频下载失败: ${e.message}", e)
                )
            }
        }

    /**
     * 下载 B站字幕（JSON 格式）并转为 SRT。
     *
     * @param info       [parse] 返回的视频信息
     * @param outputPath 输出的 SRT 文件路径
     * @return 成功返回 SRT 路径；无可用字幕返回 null
     */
    suspend fun downloadSubtitle(info: BilibiliVideoInfo, outputPath: String): Result<String?> =
        withContext(Dispatchers.IO) {
            try {
                if (info.subtitles.isEmpty()) {
                    Log.i(TAG, "无可用字幕")
                    return@withContext Result.success(null)
                }

                // 优先中文，其次英文，最后第一个
                val preferred = info.subtitles.find { it.language.contains("中文") || it.language.contains("zh") }
                    ?: info.subtitles.find { it.language.contains("英文") || it.language.contains("en") }
                    ?: info.subtitles.first()

                Log.i(TAG, "下载字幕: ${preferred.language} → $preferred.url")

                val subJson = apiGet(preferred.url)
                val srtContent = convertBilibiliSubtitleToSrt(subJson)

                if (srtContent.isNullOrBlank()) {
                    Log.w(TAG, "字幕内容为空")
                    return@withContext Result.success(null)
                }

                val outputFile = File(outputPath)
                outputFile.parentFile?.mkdirs()
                outputFile.writeText(srtContent, Charsets.UTF_8)

                Log.i(TAG, "字幕已保存: $outputPath (${outputFile.length()} bytes)")
                Result.success(outputFile.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "字幕下载失败: ${e.message}", e)
                // 字幕失败不影响主流程
                Result.success(null)
            }
        }

    // ====================================================================
    // 内部方法
    // ====================================================================

    /**
     * 执行 B站 API GET 请求。
     */
    private fun apiGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", UA)
            .addHeader("Referer", REFERER)
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
        // B站流 URL 有时需要 Referer
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", UA)
            .addHeader("Referer", REFERER)
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
     * 用 ffmpeg 合并视频和音频流。
     */
    private fun mergeVideoAudio(
        ffmpeg: String,
        videoPath: String,
        audioPath: String,
        outputPath: String
    ): Boolean {
        return try {
            val pb = ProcessBuilder(
                ffmpeg, "-y",
                "-i", videoPath,
                "-i", audioPath,
                "-c:v", "copy",
                "-c:a", "aac",
                "-strict", "experimental",
                outputPath
            ).redirectErrorStream(true)

            val process = pb.start()
            val output = process.inputStream.bufferedReader().readText()
            val exited = process.waitFor(60, TimeUnit.SECONDS)
            val exitCode = if (exited) process.exitValue() else {
                process.destroyForcibly()
                -1
            }

            if (exitCode == 0) {
                Log.i(TAG, "ffmpeg 合并成功")
                true
            } else {
                Log.w(TAG, "ffmpeg 合并失败 exit=$exitCode:\n${output.take(300)}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "ffmpeg 异常: ${e.message}")
            false
        }
    }

    /**
     * 将 B站 JSON 字幕格式转为标准 SRT。
     *
     * B站 JSON 格式:
     * ```json
     * {
     *   "body": [
     *     {"from": 0.5, "to": 3.0, "content": "你好", ...},
     *     {"from": 3.5, "to": 6.0, "content": "世界", ...}
     *   ]
     * }
     * ```
     */
    private fun convertBilibiliSubtitleToSrt(json: String): String? {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val body = root.getAsJsonArray("body") ?: return null

            val sb = StringBuilder()
            var index = 1
            for (element in body) {
                val obj = element.asJsonObject
                val from = obj.get("from")?.asDouble ?: continue
                val to = obj.get("to")?.asDouble ?: continue
                val content = obj.get("content")?.asString?.trim() ?: continue

                if (content.isBlank()) continue

                sb.appendLine(index++)
                sb.appendLine("${formatTime(from)} --> ${formatTime(to)}")
                sb.appendLine(content)
                sb.appendLine()
            }

            if (sb.isEmpty()) null else sb.toString()
        } catch (e: Exception) {
            Log.w(TAG, "B站字幕转换失败: ${e.message}")
            null
        }
    }

    /**
     * 将秒数转为 SRT 时间格式: HH:MM:SS,mmm
     */
    private fun formatTime(seconds: Double): String {
        val totalMs = (seconds * 1000).toLong()
        val hours = totalMs / 3600000
        val minutes = (totalMs % 3600000) / 60000
        val secs = (totalMs % 60000) / 1000
        val millis = totalMs % 1000
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, secs, millis)
    }
}
