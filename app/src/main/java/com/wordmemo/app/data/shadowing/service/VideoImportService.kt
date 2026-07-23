package com.wordmemo.app.data.shadowing.service

import android.content.Context
import android.net.Uri
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
}

@Singleton
class VideoImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shadowingVideoDao: ShadowingVideoDao,
    private val shadowingSentenceDao: ShadowingSentenceDao
) {
    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: Flow<DownloadProgress> = _downloadProgress.asStateFlow()

    private val subtitleParser = SubtitleParser()

    private val shadowingDir: File
        get() = File(context.filesDir, "shadowing_videos").also { it.mkdirs() }

    private var currentProcessId: String? = null

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

            val processId = UUID.randomUUID().toString()
            currentProcessId = processId

            // 配置下载请求
            val request = YoutubeDLRequest(url).apply {
                addOption("-o", "${videoDir.absolutePath}/%(title)s.%(ext)s")
                addOption("--no-mtime")
                addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
                // 下载字幕
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

            // 查找下载的视频文件和字幕文件
            val videoFile = videoDir.listFiles()?.find { file ->
                file.isFile && file.extension in listOf("mp4", "mkv", "webm", "avi", "mov")
            } ?: throw VideoImportException.VideoDownloadException("未找到下载的视频文件")

            val videoTitle = videoFile.nameWithoutExtension

            // 查找字幕文件
            val subtitleFile = videoDir.listFiles()?.find { file ->
                file.isFile && file.extension in listOf("srt", "vtt")
            }

            // 创建视频实体
            val entity = ShadowingVideoEntity(
                title = videoTitle,
                sourceType = "bilibili",
                sourceUrl = url,
                filePath = videoFile.absolutePath,
                subtitlePath = subtitleFile?.absolutePath,
                durationMs = 0L, // 后续通过 ffmpeg 或播放器获取，初始为0
                fileSizeBytes = videoFile.length()
            )
            val videoId = shadowingVideoDao.insert(entity)

            // 解析字幕并保存句子
            if (subtitleFile != null) {
                try {
                    val subtitleContent = subtitleFile.readText()
                    val entries = subtitleParser.detectAndParse(subtitleContent, subtitleFile.name)

                    if (entries.isNotEmpty()) {
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

                        // 更新视频的句子计数
                        shadowingVideoDao.update(videoId.let {
                            entity.copy(
                                id = it,
                                sentenceCount = sentences.size,
                                subtitlePath = subtitleFile.absolutePath
                            )
                        })
                    }
                } catch (e: Exception) {
                    // 字幕解析失败不影响视频下载，但记录异常
                    // 使用 updateSubtitlePath 后续再试
                }
            }

            _downloadProgress.value = DownloadProgress(
                videoId = videoId,
                status = DownloadStatus.COMPLETED,
                percentage = 100
            )

            Result.success(shadowingVideoDao.getById(videoId) ?: entity.copy(id = videoId))
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

            val fileName = "local_${System.currentTimeMillis()}.$ext"
            val file = File(shadowingDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            } ?: return Result.failure(VideoImportException.FileCopyException("无法读取URI: $uri"))

            // 复制字幕（如果有）
            var subtitlePath: String? = null
            if (subtitleUri != null) {
                val subExt = when {
                    subtitleUri.toString().endsWith(".vtt", ignoreCase = true) -> "vtt"
                    else -> "srt"
                }
                val subFile = File(shadowingDir, "${file.nameWithoutExtension}.$subExt")
                try {
                    context.contentResolver.openInputStream(subtitleUri)?.use { input ->
                        FileOutputStream(subFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    subtitlePath = subFile.absolutePath
                } catch (e: Exception) {
                    // 字幕复制失败不影响视频导入
                }
            }

            val entity = ShadowingVideoEntity(
                title = file.nameWithoutExtension,
                sourceType = "local",
                filePath = file.absolutePath,
                subtitlePath = subtitlePath,
                durationMs = 0L, // 后续通过 ffmpeg 或播放器获取
                fileSizeBytes = file.length()
            )
            val videoId = shadowingVideoDao.insert(entity)

            // 解析字幕并保存句子
            if (subtitlePath != null) {
                try {
                    val subFile = File(subtitlePath)
                    val subtitleContent = subFile.readText()
                    val entries = subtitleParser.detectAndParse(subtitleContent, subFile.name)

                    if (entries.isNotEmpty()) {
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

                        shadowingVideoDao.update(videoId.let {
                            entity.copy(
                                id = it,
                                sentenceCount = sentences.size,
                                subtitlePath = subtitlePath
                            )
                        })
                    }
                } catch (e: Exception) {
                    // 字幕解析失败不影响导入
                }
            }

            Result.success(shadowingVideoDao.getById(videoId) ?: entity.copy(id = videoId))
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
}
