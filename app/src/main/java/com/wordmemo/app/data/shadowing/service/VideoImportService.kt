package com.wordmemo.app.data.shadowing.service

import android.content.Context
import android.net.Uri
import com.wordmemo.app.data.shadowing.dao.ShadowingVideoDao
import com.wordmemo.app.data.shadowing.entity.ShadowingVideoEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

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
}

@Singleton
class VideoImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shadowingVideoDao: ShadowingVideoDao
) {
    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: Flow<DownloadProgress> = _downloadProgress.asStateFlow()

    private val shadowingDir: File
        get() = File(context.filesDir, "shadowing_videos").also { it.mkdirs() }

    /**
     * 从B站URL下载视频（v1: yt-dlp wrapper 骨架）
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

            // v1 骨架实现 — yt-dlp 实际下载将在后续迭代接入
            val title = extractTitleFromUrl(url) ?: "B站视频"
            val fileName = "bilibili_${System.currentTimeMillis()}.mp4"
            val file = File(shadowingDir, fileName)

            // 占位: 实际使用 youtubedl-android 下载
            file.createNewFile()

            val entity = ShadowingVideoEntity(
                title = title,
                sourceType = "bilibili",
                sourceUrl = url,
                filePath = file.absolutePath,
                durationMs = 0L,
                fileSizeBytes = 0L
            )
            val id = shadowingVideoDao.insert(entity)

            _downloadProgress.value = DownloadProgress(
                videoId = id,
                status = DownloadStatus.COMPLETED,
                percentage = 100
            )

            Result.success(entity.copy(id = id))
        } catch (e: Exception) {
            _downloadProgress.value = DownloadProgress(status = DownloadStatus.FAILED)
            Result.failure(VideoImportException.VideoDownloadException("下载失败: ${e.message}", e))
        }
    }

    /**
     * 从SAF Uri导入本地视频
     */
    suspend fun importLocalVideo(uri: Uri): Result<ShadowingVideoEntity> {
        return try {
            val fileName = "local_${System.currentTimeMillis()}.mp4"
            val file = File(shadowingDir, fileName)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(VideoImportException.FileCopyException("无法读取URI: $uri"))

            inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            val entity = ShadowingVideoEntity(
                title = file.nameWithoutExtension,
                sourceType = "local",
                filePath = file.absolutePath,
                durationMs = 0L,
                fileSizeBytes = file.length()
            )
            val id = shadowingVideoDao.insert(entity)
            Result.success(entity.copy(id = id))
        } catch (e: IOException) {
            Result.failure(VideoImportException.FileCopyException("文件复制失败: ${e.message}", e))
        } catch (e: SecurityException) {
            Result.failure(VideoImportException.FileCopyException("权限不足: ${e.message}", e))
        }
    }

    fun cancelDownload(videoId: Long) {
        _downloadProgress.value = DownloadProgress(videoId = videoId, status = DownloadStatus.IDLE)
    }

    private fun extractTitleFromUrl(url: String): String? {
        // v1: 占位实现 — 后续通过yt-dlp解析页面标题
        return null
    }
}
