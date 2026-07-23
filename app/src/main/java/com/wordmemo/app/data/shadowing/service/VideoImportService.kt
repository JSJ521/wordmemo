package com.wordmemo.app.data.shadowing.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.wordmemo.app.data.local.dao.AppConfigDao
import com.wordmemo.app.data.shadowing.dao.ShadowingSentenceDao
import com.wordmemo.app.data.shadowing.dao.ShadowingVideoDao
import com.wordmemo.app.data.shadowing.entity.ShadowingSentenceEntity
import com.wordmemo.app.data.shadowing.entity.ShadowingVideoEntity
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DownloadProgress(
    val videoId: Long? = null,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = -1,
    val percentage: Int = 0,
    val status: DownloadStatus = DownloadStatus.IDLE
)

enum class DownloadStatus {
    IDLE, DOWNLOADING, COMPLETED, FAILED
}

sealed class VideoImportException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class VideoDownloadException(message: String, cause: Throwable? = null) : VideoImportException(message, cause)
    class UnsupportedUrlException(message: String) : VideoImportException(message)
    class NetworkException(message: String, cause: Throwable? = null) : VideoImportException(message, cause)
    class FileCopyException(message: String, cause: Throwable? = null) : VideoImportException(message, cause)
    class UnsupportedFormatException(message: String) : VideoImportException(message)
    class SubtitleParseException(message: String, cause: Throwable? = null) : VideoImportException(message, cause)
    class AsrNotAvailableException(message: String) : VideoImportException(message)
    class AsrConfigurationException(message: String) : VideoImportException(message)
}

/**
 * 字幕获取策略——描述此次导入使用了哪种方式获取字幕
 */
enum class SubtitleSource {
    /** 用户提供的外挂字幕文件 */
    EXTERNAL,
    /** 视频内嵌字幕轨道 */
    EMBEDDED,
    /** ASR 语音识别生成 */
    ASR_GENERATED,
    /** 没有任何可用字幕 */
    NONE
}

/**
 * URL 类型枚举——由 [VideoImportService.detectUrlType] 返回
 */
enum class UrlType {
    BILIBILI,
    YOUTUBE,
    OTHER
}

@Singleton
class VideoImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shadowingVideoDao: ShadowingVideoDao,
    private val shadowingSentenceDao: ShadowingSentenceDao,
    private val subtitleExtractor: SubtitleExtractor,
    private val subtitleGenerator: SubtitleGenerator,
    private val appConfigDao: AppConfigDao,
    private val bilibiliParser: BilibiliParser,
    private val youTubeParser: YouTubeParser
) {
    private val TAG = "VideoImportService"

    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: Flow<DownloadProgress> = _downloadProgress.asStateFlow()

    private val subtitleParser = SubtitleParser()

    private val shadowingDir: File
        get() = File(context.filesDir, "shadowing_videos").also { it.mkdirs() }

    private var currentProcessId: String? = null

    // ==================== 公开 API ====================

    /**
     * 从B站URL下载视频
     *
     * 完整流程:
     * 1. 初始化 youtubedl-android
     * 2. 配置下载选项（视频+字幕）
     * 3. 异步下载并监控进度
     * 4. 下载完成后扫描字幕文件
     * 5. 解析字幕并存入 shadowing_sentences 表
     * 6. 更新视频记录的 duration/subtitle 信息
     */
    suspend fun downloadFromBilibili(url: String): Result<ShadowingVideoEntity> {
        return try {
            if (!url.contains("bilibili.com") && !url.contains("b23.tv")) {
                return Result.failure(VideoImportException.UnsupportedUrlException("不支持的URL: $url"))
            }

            _downloadProgress.value = DownloadProgress(
                status = DownloadStatus.DOWNLOADING,
                percentage = 0
            )

            // 确保 youtubedl-android 已初始化
            try {
                YoutubeDL.getInstance().init(context)
            } catch (e: YoutubeDLException) {
                // 可能已经初始化过，忽略
            }

            // 创建视频专属输出目录
            val videoDir = File(shadowingDir, "bilibili_${System.currentTimeMillis()}")
            videoDir.mkdirs()

            // 读取 B站 cookie 并写入临时文件
            val bilibiliCookieFile = writeCookieFile("bilibili_cookies")

            val processId = UUID.randomUUID().toString()
            currentProcessId = processId

            // 配置下载请求
            val request = YoutubeDLRequest(url).apply {
                addOption("-o", "${videoDir.absolutePath}/%(title)s.%(ext)s")
                addOption("--no-mtime")
                addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
                // 传递 cookie 解锁登录内容
                bilibiliCookieFile?.let {
                    addOption("--cookies", it.absolutePath)
                }
                // 下载外挂字幕（yt-dlp 从 B站等网站抓取）
                addOption("--write-subs")
                addOption("--write-auto-subs")
                addOption("--sub-lang", "zh-Hans,en,zh")
                addOption("--sub-format", "srt")
                addOption("--convert-subs", "srt")
            }

            // 使用 suspendCancellableCoroutine 将回调转为挂起
            val response: YoutubeDLResponse = withContext(Dispatchers.IO) {
                suspendCancellableCoroutine { continuation ->
                    try {
                        val callback: (Float, Long, String) -> Unit = { progress, eta, line ->
                            _downloadProgress.value = DownloadProgress(
                                status = DownloadStatus.DOWNLOADING,
                                percentage = progress.toInt()
                            )
                        }

                        val result = YoutubeDL.getInstance().execute(request, processId, callback)
                        continuation.resume(result)
                    } catch (e: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            }

            currentProcessId = null

            // 查找下载的视频文件
            val videoFile = videoDir.listFiles()?.find { file ->
                file.isFile && file.extension in listOf("mp4", "mkv", "webm", "avi", "mov")
            } ?: throw VideoImportException.VideoDownloadException("未找到下载的视频文件")

            val videoTitle = videoFile.nameWithoutExtension

            // 创建视频实体（先插入，获取 videoId）
            val entity = ShadowingVideoEntity(
                title = videoTitle,
                sourceType = "bilibili",
                sourceUrl = url,
                filePath = videoFile.absolutePath,
                subtitlePath = null,
                durationMs = 0L,
                fileSizeBytes = videoFile.length()
            )
            val videoId = shadowingVideoDao.insert(entity)

            // ---- 字幕获取管线 ----
            val subtitleResult = acquireSubtitles(
                videoFile = videoFile,
                videoId = videoId,
                subtitleDir = videoDir,
                externalSubtitleFile = findExternalSubtitle(videoDir)
            )

            // 更新视频信息
            val updatedEntity = entity.copy(
                id = videoId,
                subtitlePath = subtitleResult.subtitlePath,
                sentenceCount = subtitleResult.sentenceCount
            )
            shadowingVideoDao.update(updatedEntity)

            _downloadProgress.value = DownloadProgress(
                videoId = videoId,
                status = DownloadStatus.COMPLETED,
                percentage = 100
            )

            Result.success(shadowingVideoDao.getById(videoId) ?: updatedEntity)
        } catch (e: YoutubeDLException) {
            _downloadProgress.value = DownloadProgress(status = DownloadStatus.FAILED)
            Result.failure(VideoImportException.VideoDownloadException("yt-dlp下载失败: ${e.message}", e))
        } catch (e: VideoImportException) {
            _downloadProgress.value = DownloadProgress(status = DownloadStatus.FAILED)
            Result.failure(e)
        } catch (e: Exception) {
            _downloadProgress.value = DownloadProgress(status = DownloadStatus.FAILED)
            Result.failure(VideoImportException.VideoDownloadException("下载失败: ${e.message}", e))
        }
    }

    // ==================== 直接解析 API（无需 cookie/登录）====================

    /**
     * 检测 URL 类型。
     *
     * - B站链接（bilibili.com / b23.tv）→ [UrlType.BILIBILI]
     * - YouTube链接（youtube.com / youtu.be）→ [UrlType.YOUTUBE]
     * - 其他 → [UrlType.OTHER]
     */
    private fun detectUrlType(url: String): UrlType {
        return when {
            url.contains("bilibili.com") || url.contains("b23.tv") -> UrlType.BILIBILI
            url.contains("youtube.com") || url.contains("youtu.be") -> UrlType.YOUTUBE
            else -> UrlType.OTHER
        }
    }

    /**
     * 通过公开 API 直接下载视频（无需 cookie/登录），支持 B站 和 YouTube。
     *
     * 路由逻辑：
     * - B站链接 → [BilibiliParser] 直接解析下载 + 字幕
     * - YouTube 链接 → [YouTubeParser] 通过 Invidious 实例解析下载 + 字幕
     * - 其他链接 → 回退到 yt-dlp（需 cookie 的站点由原有流程处理）
     *
     * @param url 视频链接
     * @return 入库后的 [ShadowingVideoEntity]
     */
    suspend fun downloadFromUrl(url: String): Result<ShadowingVideoEntity> {
        return when (detectUrlType(url)) {
            UrlType.BILIBILI -> downloadBilibiliDirect(url)
            UrlType.YOUTUBE -> downloadYouTubeDirect(url)
            UrlType.OTHER -> downloadFromBilibili(url)  // 回退到 yt-dlp
        }
    }

    /**
     * 通过 [BilibiliParser] 直接下载 B站视频（无需 cookie）。
     */
    private suspend fun downloadBilibiliDirect(url: String): Result<ShadowingVideoEntity> {
        return try {
            // 处理 b23.tv 短链接
            val resolvedUrl = if (url.contains("b23.tv")) {
                val bvResult = bilibiliParser.resolveShortUrl(url)
                if (bvResult.isSuccess) {
                    "https://www.bilibili.com/video/${bvResult.getOrThrow()}"
                } else {
                    Log.w(TAG, "短链接解析失败，回退 yt-dlp: ${bvResult.exceptionOrNull()?.message}")
                    return downloadFromBilibili(url)
                }
            } else {
                url
            }

            _downloadProgress.value = DownloadProgress(
                status = DownloadStatus.DOWNLOADING,
                percentage = 0
            )

            // Step 1: 解析视频信息
            val parseResult = bilibiliParser.parse(resolvedUrl)
            if (parseResult.isFailure) {
                val err = parseResult.exceptionOrNull()
                Log.w(TAG, "BilibiliParser 解析失败: ${err?.message}，回退 yt-dlp")
                return downloadFromBilibili(url)
            }
            val info = parseResult.getOrThrow()
            _downloadProgress.value = DownloadProgress(
                status = DownloadStatus.DOWNLOADING,
                percentage = 10
            )

            // 创建输出目录
            val videoDir = File(shadowingDir, "bilibili_direct_${System.currentTimeMillis()}")
            videoDir.mkdirs()
            val videoOutputPath = File(videoDir, "${sanitizeFileName(info.title)}.mp4").absolutePath

            // Step 2: 下载视频
            val downloadResult = bilibiliParser.downloadVideo(info, videoOutputPath)
            if (downloadResult.isFailure) {
                val err = downloadResult.exceptionOrNull()
                Log.w(TAG, "BilibiliParser 下载失败: ${err?.message}，回退 yt-dlp")
                return downloadFromBilibili(url)
            }

            _downloadProgress.value = DownloadProgress(
                status = DownloadStatus.DOWNLOADING,
                percentage = 70
            )

            val videoFile = File(videoOutputPath)
            val videoTitle = sanitizeFileName(info.title)

            // Step 3: 下载字幕
            val subtitlePath = bilibiliParser.downloadSubtitle(
                info,
                File(videoDir, "${videoTitle}.srt").absolutePath
            ).getOrNull()

            _downloadProgress.value = DownloadProgress(
                status = DownloadStatus.DOWNLOADING,
                percentage = 85
            )

            // Step 4: 入库
            val entity = ShadowingVideoEntity(
                title = info.title,
                sourceType = "bilibili",
                sourceUrl = url,
                filePath = videoFile.absolutePath,
                subtitlePath = null,  // 先插入，字幕后面再关联
                durationMs = info.durationSeconds * 1000,
                fileSizeBytes = videoFile.length()
            )
            val videoId = shadowingVideoDao.insert(entity)

            // Step 5: 处理字幕
            var subtitleResult: SubtitleAcquireResult
            if (subtitlePath != null) {
                val subtitleFile = File(subtitlePath)
                if (subtitleFile.exists() && subtitleFile.length() > 0) {
                    try {
                        val content = subtitleFile.readText()
                        val entries = subtitleParser.detectAndParse(content, subtitleFile.name)
                        if (entries.isNotEmpty()) {
                            val count = saveSentences(videoId, entries)
                            subtitleResult = SubtitleAcquireResult(
                                subtitlePath = subtitleFile.absolutePath,
                                sentenceCount = count,
                                source = SubtitleSource.EXTERNAL
                            )
                        } else {
                            subtitleResult = SubtitleAcquireResult(null, 0, SubtitleSource.NONE)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "字幕解析失败: ${e.message}")
                        subtitleResult = SubtitleAcquireResult(null, 0, SubtitleSource.NONE)
                    }
                } else {
                    subtitleResult = SubtitleAcquireResult(null, 0, SubtitleSource.NONE)
                }
            } else {
                // 无 API 字幕，走原有字幕获取管线（尝试内嵌字幕/ASR）
                subtitleResult = acquireSubtitles(
                    videoFile = videoFile,
                    videoId = videoId,
                    subtitleDir = videoDir,
                    externalSubtitleFile = null
                )
            }

            // Step 6: 更新视频记录
            val updatedEntity = entity.copy(
                id = videoId,
                subtitlePath = subtitleResult.subtitlePath,
                sentenceCount = subtitleResult.sentenceCount
            )
            shadowingVideoDao.update(updatedEntity)

            _downloadProgress.value = DownloadProgress(
                videoId = videoId,
                status = DownloadStatus.COMPLETED,
                percentage = 100
            )

            Log.i(TAG, "B站直接下载完成: ${info.title} (${videoFile.length()} bytes)")
            Result.success(shadowingVideoDao.getById(videoId) ?: updatedEntity)
        } catch (e: VideoImportException) {
            _downloadProgress.value = DownloadProgress(status = DownloadStatus.FAILED)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "B站直接下载异常: ${e.message}", e)
            // 捕获所有异常后尝试回退到 yt-dlp
            Log.w(TAG, "直接下载失败，回退 yt-dlp")
            return downloadFromBilibili(url)
        }
    }

    /**
     * 通过 [YouTubeParser] 直接下载 YouTube 视频（无需 cookie/API key）。
     */
    private suspend fun downloadYouTubeDirect(url: String): Result<ShadowingVideoEntity> {
        return try {
            _downloadProgress.value = DownloadProgress(
                status = DownloadStatus.DOWNLOADING,
                percentage = 0
            )

            // Step 1: 解析视频信息（通过 Invidious 实例）
            val parseResult = youTubeParser.parse(url)
            if (parseResult.isFailure) {
                val err = parseResult.exceptionOrNull()
                Log.w(TAG, "YouTubeParser 解析失败: ${err?.message}，回退 yt-dlp")
                return downloadFromBilibili(url)  // yt-dlp 也支持 YouTube
            }
            val info = parseResult.getOrThrow()
            _downloadProgress.value = DownloadProgress(
                status = DownloadStatus.DOWNLOADING,
                percentage = 15
            )

            // 创建输出目录
            val videoDir = File(shadowingDir, "youtube_direct_${System.currentTimeMillis()}")
            videoDir.mkdirs()
            val videoOutputPath = File(videoDir, "${sanitizeFileName(info.title)}.mp4").absolutePath

            // Step 2: 下载视频
            val downloadResult = youTubeParser.downloadVideo(info, videoOutputPath)
            if (downloadResult.isFailure) {
                val err = downloadResult.exceptionOrNull()
                Log.w(TAG, "YouTubeParser 下载失败: ${err?.message}，回退 yt-dlp")
                return downloadFromBilibili(url)
            }

            _downloadProgress.value = DownloadProgress(
                status = DownloadStatus.DOWNLOADING,
                percentage = 75
            )

            val videoFile = File(videoOutputPath)
            val videoTitle = sanitizeFileName(info.title)

            // Step 3: 下载字幕
            val subtitlePath = youTubeParser.downloadSubtitle(
                info,
                File(videoDir, "${videoTitle}.srt").absolutePath
            ).getOrNull()

            _downloadProgress.value = DownloadProgress(
                status = DownloadStatus.DOWNLOADING,
                percentage = 85
            )

            // Step 4: 入库
            val entity = ShadowingVideoEntity(
                title = info.title,
                sourceType = "youtube",
                sourceUrl = url,
                filePath = videoFile.absolutePath,
                subtitlePath = null,
                durationMs = info.durationSeconds * 1000,
                fileSizeBytes = videoFile.length()
            )
            val videoId = shadowingVideoDao.insert(entity)

            // Step 5: 处理字幕
            var subtitleResult: SubtitleAcquireResult
            if (subtitlePath != null) {
                val subtitleFile = File(subtitlePath)
                if (subtitleFile.exists() && subtitleFile.length() > 0) {
                    try {
                        val content = subtitleFile.readText()
                        val entries = subtitleParser.detectAndParse(content, subtitleFile.name)
                        if (entries.isNotEmpty()) {
                            val count = saveSentences(videoId, entries)
                            subtitleResult = SubtitleAcquireResult(
                                subtitlePath = subtitleFile.absolutePath,
                                sentenceCount = count,
                                source = SubtitleSource.EXTERNAL
                            )
                        } else {
                            subtitleResult = SubtitleAcquireResult(null, 0, SubtitleSource.NONE)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "字幕解析失败: ${e.message}")
                        subtitleResult = SubtitleAcquireResult(null, 0, SubtitleSource.NONE)
                    }
                } else {
                    subtitleResult = SubtitleAcquireResult(null, 0, SubtitleSource.NONE)
                }
            } else {
                subtitleResult = acquireSubtitles(
                    videoFile = videoFile,
                    videoId = videoId,
                    subtitleDir = videoDir,
                    externalSubtitleFile = null
                )
            }

            // Step 6: 更新视频记录
            val updatedEntity = entity.copy(
                id = videoId,
                subtitlePath = subtitleResult.subtitlePath,
                sentenceCount = subtitleResult.sentenceCount
            )
            shadowingVideoDao.update(updatedEntity)

            _downloadProgress.value = DownloadProgress(
                videoId = videoId,
                status = DownloadStatus.COMPLETED,
                percentage = 100
            )

            Log.i(TAG, "YouTube 直接下载完成: ${info.title} (${videoFile.length()} bytes)")
            Result.success(shadowingVideoDao.getById(videoId) ?: updatedEntity)
        } catch (e: VideoImportException) {
            _downloadProgress.value = DownloadProgress(status = DownloadStatus.FAILED)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "YouTube 直接下载异常: ${e.message}", e)
            Log.w(TAG, "直接下载失败，回退 yt-dlp")
            return downloadFromBilibili(url)
        }
    }

    /**
     * 从 SAF Uri 导入本地视频及可选字幕
     */
    suspend fun importLocalVideo(uri: Uri, subtitleUri: Uri? = null): Result<ShadowingVideoEntity> {
        return try {
            // 复制视频文件到内部存储
            val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"
            val ext = when {
                mimeType.contains("mp4") -> "mp4"
                mimeType.contains("webm") -> "webm"
                mimeType.contains("mkv") -> "mkv"
                mimeType.contains("quicktime") -> "mov"
                mimeType.contains("x-msvideo") -> "avi"
                else -> "mp4"
            }

            val videoDir = File(shadowingDir, "local_${System.currentTimeMillis()}")
            videoDir.mkdirs()

            val fileName = "video.$ext"
            val file = File(videoDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            } ?: return Result.failure(VideoImportException.FileCopyException("无法读取URI: $uri"))

            // 复制外挂字幕（如果有）
            var externalSubtitleFile: File? = null
            if (subtitleUri != null) {
                val subExt = when {
                    subtitleUri.toString().endsWith(".vtt", ignoreCase = true) -> "vtt"
                    else -> "srt"
                }
                val subFile = File(videoDir, "${file.nameWithoutExtension}.$subExt")
                try {
                    context.contentResolver.openInputStream(subtitleUri)?.use { input ->
                        FileOutputStream(subFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    externalSubtitleFile = subFile
                } catch (e: Exception) {
                    Log.w(TAG, "外挂字幕复制失败: ${e.message}")
                }
            }

            // 创建视频实体
            val entity = ShadowingVideoEntity(
                title = file.nameWithoutExtension,
                sourceType = "local",
                filePath = file.absolutePath,
                subtitlePath = null,
                durationMs = 0L,
                fileSizeBytes = file.length()
            )
            val videoId = shadowingVideoDao.insert(entity)

            // ---- 字幕获取管线 ----
            val subtitleResult = acquireSubtitles(
                videoFile = file,
                videoId = videoId,
                subtitleDir = videoDir,
                externalSubtitleFile = externalSubtitleFile
            )

            // 更新视频信息
            val updatedEntity = entity.copy(
                id = videoId,
                subtitlePath = subtitleResult.subtitlePath,
                sentenceCount = subtitleResult.sentenceCount
            )
            shadowingVideoDao.update(updatedEntity)

            Result.success(shadowingVideoDao.getById(videoId) ?: updatedEntity)
        } catch (e: IOException) {
            Result.failure(VideoImportException.FileCopyException("文件复制失败: ${e.message}", e))
        } catch (e: SecurityException) {
            Result.failure(VideoImportException.FileCopyException("权限不足: ${e.message}", e))
        }
    }

    /**
     * 取消当前下载
     */
    fun cancelDownload() {
        currentProcessId?.let { processId ->
            try {
                YoutubeDL.getInstance().destroyProcessById(processId)
            } catch (_: Exception) { }
            currentProcessId = null
        }
        _downloadProgress.value = DownloadProgress(status = DownloadStatus.IDLE)
    }

    /**
     * 取消指定视频下载（重载，保持兼容）
     */
    fun cancelDownload(videoId: Long) {
        cancelDownload()
        _downloadProgress.value = DownloadProgress(videoId = videoId, status = DownloadStatus.IDLE)
    }

    // ==================== 字幕获取管线 ====================

    /**
     * 字幕获取结果
     */
    data class SubtitleAcquireResult(
        /** 最终字幕文件路径（null 表示无字幕） */
        val subtitlePath: String?,
        /** 句子数量 */
        val sentenceCount: Int,
        /** 字幕来源 */
        val source: SubtitleSource
    )

    /**
     * 字幕获取管线——按优先级依次尝试：
     *
     * 1. [externalSubtitleFile] 外挂字幕 → 解析入库
     * 2. [SubtitleExtractor] 内嵌字幕 → 提取为SRT → 解析入库
     * 3. [SubtitleGenerator] ASR生成  → SRT入库
     * 4. 全部失败 → subtitlePath = null（标记"需手动配字幕"）
     */
    private suspend fun acquireSubtitles(
        videoFile: File,
        videoId: Long,
        subtitleDir: File,
        externalSubtitleFile: File?
    ): SubtitleAcquireResult {
        // ---- 第1步：外挂字幕 ----
        if (externalSubtitleFile != null && externalSubtitleFile.exists()) {
            Log.i(TAG, "尝试外挂字幕: ${externalSubtitleFile.name}")
            try {
                val content = externalSubtitleFile.readText()
                val entries = subtitleParser.detectAndParse(content, externalSubtitleFile.name)
                if (entries.isNotEmpty()) {
                    val count = saveSentences(videoId, entries)
                    Log.i(TAG, "外挂字幕解析成功: $count 句")
                    return SubtitleAcquireResult(
                        subtitlePath = externalSubtitleFile.absolutePath,
                        sentenceCount = count,
                        source = SubtitleSource.EXTERNAL
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "外挂字幕解析失败: ${e.message}")
            }
        }

        // ---- 第2步：内嵌字幕 ----
        Log.i(TAG, "尝试提取内嵌字幕: ${videoFile.name}")
        try {
            val hasTrack = subtitleExtractor.hasSubtitleTrack(videoFile.absolutePath)
            if (hasTrack) {
                val srtFile = File(subtitleDir, "${videoFile.nameWithoutExtension}_embedded.srt")
                val extractResult = subtitleExtractor.extractSubtitle(
                    videoPath = videoFile.absolutePath,
                    outputPath = srtFile.absolutePath
                )
                if (extractResult.isSuccess && srtFile.exists() && srtFile.length() > 0) {
                    val content = srtFile.readText()
                    val entries = subtitleParser.parseSrt(content)
                    if (entries.isNotEmpty()) {
                        val count = saveSentences(videoId, entries)
                        Log.i(TAG, "内嵌字幕提取成功: $count 句")
                        return SubtitleAcquireResult(
                            subtitlePath = srtFile.absolutePath,
                            sentenceCount = count,
                            source = SubtitleSource.EMBEDDED
                        )
                    }
                }
            } else {
                Log.i(TAG, "视频无内嵌字幕轨道")
            }
        } catch (e: Exception) {
            Log.w(TAG, "内嵌字幕提取失败: ${e.message}")
        }

        // ---- 第3步：ASR 生成 ----
        Log.i(TAG, "尝试 ASR 生成字幕")
        try {
            val asrResult = tryGenerateAsrSubtitle(videoFile, subtitleDir)
            if (asrResult.isSuccess && asrResult.getOrNull() != null) {
                val asrPath = asrResult.getOrNull()!!
                val srtFile = File(asrPath)
                if (srtFile.exists() && srtFile.length() > 0) {
                    val content = srtFile.readText()
                    val entries = subtitleParser.parseSrt(content)
                    if (entries.isNotEmpty()) {
                        val count = saveSentences(videoId, entries)
                        Log.i(TAG, "ASR 字幕生成成功: $count 句")
                        return SubtitleAcquireResult(
                            subtitlePath = srtFile.absolutePath,
                            sentenceCount = count,
                            source = SubtitleSource.ASR_GENERATED
                        )
                    }
                }
            } else {
                val err = asrResult.exceptionOrNull()
                if (err is VideoImportException.AsrConfigurationException) {
                    Log.w(TAG, "ASR 未配置: ${err.message}")
                } else {
                    Log.w(TAG, "ASR 生成失败: ${err?.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "ASR 生成异常: ${e.message}")
        }

        // ---- 第4步：全部失败 ----
        Log.i(TAG, "所有字幕获取方式均失败，视频 $videoId 无可用字幕")
        return SubtitleAcquireResult(
            subtitlePath = null,
            sentenceCount = 0,
            source = SubtitleSource.NONE
        )
    }

    /**
     * 尝试 ASR 字幕生成。
     * 如果 ASR 未配置，返回清晰的失败信息。
     */
    private suspend fun tryGenerateAsrSubtitle(
        videoFile: File,
        subtitleDir: File
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            // 读取 ASR 配置
            val asrBaseUrl = try {
                appConfigDao.getValue(SubtitleGenerator.CONFIG_ASR_BASE_URL)
                    ?.value?.ifBlank { null }
            } catch (_: Exception) { null }

            val asrApiKey = try {
                appConfigDao.getValue(SubtitleGenerator.CONFIG_ASR_API_KEY)
                    ?.value?.ifBlank { null }
            } catch (_: Exception) { null }

            if (asrBaseUrl == null || asrApiKey == null) {
                return@withContext Result.failure(
                    VideoImportException.AsrConfigurationException(
                        "ASR 未配置。请提供 OpenAI Whisper 兼容 API 的地址和密钥。\n" +
                            "配置方式：设置页 → ASR 服务地址 + ASR API Key\n" +
                            "或自行准备 .srt 字幕文件导入。"
                    )
                )
            }

            // 提取音频
            val audioFile = File(subtitleDir, "${videoFile.nameWithoutExtension}_audio.wav")
            val audioResult = subtitleExtractor.extractAudio(
                videoPath = videoFile.absolutePath,
                outputPath = audioFile.absolutePath
            )

            if (audioResult.isFailure) {
                // 尝试 MP3
                val mp3File = File(subtitleDir, "${videoFile.nameWithoutExtension}_audio.mp3")
                val mp3Result = subtitleExtractor.extractAudio(
                    videoPath = videoFile.absolutePath,
                    outputPath = mp3File.absolutePath
                )
                if (mp3Result.isFailure) {
                    return@withContext Result.failure(
                        IOException("音频提取失败: ${audioResult.exceptionOrNull()?.message}")
                    )
                }
                // 用 MP3 生成字幕
                val srtFile = File(subtitleDir, "${videoFile.nameWithoutExtension}_asr.srt")
                return@withContext subtitleGenerator.generateSubtitle(
                    audioPath = mp3File.absolutePath,
                    outputPath = srtFile.absolutePath,
                    asrBaseUrl = asrBaseUrl,
                    asrApiKey = asrApiKey
                )
            }

            // 用 WAV 生成字幕
            val srtFile = File(subtitleDir, "${videoFile.nameWithoutExtension}_asr.srt")
            subtitleGenerator.generateSubtitle(
                audioPath = audioFile.absolutePath,
                outputPath = srtFile.absolutePath,
                asrBaseUrl = asrBaseUrl,
                asrApiKey = asrApiKey
            )
        }
    }

    /**
     * 为已存在的视频重新上传/替换字幕文件。
     *
     * 流程：
     * 1. 通过 SAF URI 复制字幕文件到视频所在目录
     * 2. 解析字幕内容
     * 3. 删除该视频下旧的句子数据
     * 4. 保存新的句子数据
     * 5. 更新视频记录的 subtitlePath 和 sentenceCount
     *
     * @param videoId 已有视频的 ID
     * @param subtitleUri SAF 字幕文件 URI
     * @return 更新后的视频实体
     */
    suspend fun reUploadSubtitle(videoId: Long, subtitleUri: Uri): Result<ShadowingVideoEntity> {
        return try {
            val video = shadowingVideoDao.getById(videoId)
                ?: return Result.failure(VideoImportException.SubtitleParseException("视频不存在: $videoId"))

            // 确定字幕文件存储路径（放在视频同级目录）
            val videoFile = File(video.filePath)
            val videoDir = videoFile.parentFile
                ?: return Result.failure(VideoImportException.SubtitleParseException("无法获取视频所在目录"))

            // 从 URI 判断扩展名
            val subExt = when {
                subtitleUri.toString().endsWith(".vtt", ignoreCase = true) -> "vtt"
                else -> "srt"
            }
            val subFile = File(videoDir, "${videoFile.nameWithoutExtension}_manual.$subExt")

            // 复制字幕文件
            context.contentResolver.openInputStream(subtitleUri)?.use { input ->
                FileOutputStream(subFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return Result.failure(VideoImportException.FileCopyException("无法读取字幕URI: $subtitleUri"))

            // 解析字幕
            val content = subFile.readText()
            val entries = subtitleParser.detectAndParse(content, subFile.name)
            if (entries.isEmpty()) {
                subFile.delete()
                return Result.failure(VideoImportException.SubtitleParseException("字幕文件解析失败，未找到有效字幕条目"))
            }

            // 删除旧的句子数据
            shadowingSentenceDao.deleteByVideoId(videoId)

            // 保存新的句子
            val count = saveSentences(videoId, entries)

            // 更新视频记录
            shadowingVideoDao.updateSubtitlePath(videoId, subFile.absolutePath)
            shadowingVideoDao.updateSentenceCount(videoId, count)

            Log.i(TAG, "字幕重新上传成功: $count 句, 路径=${subFile.absolutePath}")
            Result.success(shadowingVideoDao.getById(videoId) ?: video)
        } catch (e: IOException) {
            Result.failure(VideoImportException.FileCopyException("字幕文件复制失败: ${e.message}", e))
        } catch (e: SecurityException) {
            Result.failure(VideoImportException.FileCopyException("权限不足: ${e.message}", e))
        } catch (e: Exception) {
            Result.failure(VideoImportException.SubtitleParseException("字幕处理失败: ${e.message}", e))
        }
    }

    // ==================== 内部工具方法 ====================

    /**
     * 在下载目录中查找 yt-dlp 下载的外挂字幕文件
     */
    private fun findExternalSubtitle(dir: File): File? {
        return dir.listFiles()?.find { file ->
            file.isFile && file.extension in listOf("srt", "vtt")
        }
    }

    /**
     * 保存解析后的字幕条目到数据库
     */
    private suspend fun saveSentences(videoId: Long, entries: List<SubtitleEntry>): Int {
        val sentences = entries.mapIndexed { index, entry ->
            ShadowingSentenceEntity(
                videoId = videoId,
                sentenceIndex = index,
                text = entry.text,
                startTimeMs = entry.startTimeMs,
                endTimeMs = entry.endTimeMs,
                isMerged = false
            )
        }
        shadowingSentenceDao.insertBatch(sentences)
        return sentences.size
    }

    /**
     * 从 AppConfigDao 读取指定 key 的 cookie，写入临时文件供 yt-dlp 使用。
     * 如果没有配置 cookie，返回 null。
     */
    private suspend fun writeCookieFile(configKey: String): File? {
        return try {
            val cookieStr = appConfigDao.getValue(configKey)?.value
            if (cookieStr.isNullOrBlank()) {
                null
            } else {
                val cookieFile = File(context.cacheDir, "${configKey}.txt")
                cookieFile.writeText(cookieStr)
                Log.i(TAG, "已写入 cookie 文件: ${cookieFile.absolutePath}")
                cookieFile
            }
        } catch (e: Exception) {
            Log.w(TAG, "写入 cookie 文件失败: ${e.message}")
            null
        }
    }

    /**
     * 清理文件名中的非法字符。
     */
    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[/\\\\:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(200)  // 限制长度
    }
}
