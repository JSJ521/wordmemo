package com.wordmemo.app.data.shadowing.service

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用 ffmpeg 检测和提取视频文件中的内嵌字幕轨道。
 *
 * 依赖 [FFmpeg] 初始化后方可使用——ffmpeg 二进制被解压到
 * `noBackupFilesDir/youtubedl-android/packages/ffmpeg/` 下。
 *
 * 注意：该模块仅处理*内嵌*（软字幕 / 硬字幕流）轨道。
 * 外挂字幕（独立 .srt/.vtt 文件）由 [VideoImportService] 直接处理。
 */
@Singleton
class SubtitleExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "SubtitleExtractor"

    /** 外部传参，用于单元测试注入替代路径 */
    private var _ffmpegDir: File? = null

    /** ffmpeg 可执行文件的目录 */
    private val ffmpegDir: File
        get() {
            _ffmpegDir?.let { return it }
            val baseDir = File(context.noBackupFilesDir, "youtubedl-android")
            return File(baseDir, "packages/ffmpeg")
        }

    /** ffmpeg 二进制路径 */
    private val ffmpegPath: String
        get() = File(ffmpegDir, "ffmpeg").absolutePath

    /** ffprobe 二进制路径（可能不存在——部分构建未包含） */
    private val ffprobePath: String
        get() = File(ffmpegDir, "ffprobe").absolutePath

    // ----- 内部测试辅助 ------------------------------------------

    /** 仅测试用：替换 ffmpeg 目录路径 */
    internal fun setFfmpegDirForTest(dir: File) {
        _ffmpegDir = dir
    }

    // ----- 公开 API ----------------------------------------------

    /**
     * 检测视频是否包含内嵌字幕轨道。
     *
     * @param videoPath 视频文件绝对路径
     * @return true = 存在至少一个字幕流
     */
    suspend fun hasSubtitleTrack(videoPath: String): Boolean {
        return try {
            // 优先尝试 ffprobe（更快、更准确）
            if (File(ffprobePath).exists()) {
                val result = execCommand(
                    ffprobePath,
                    "-v", "error",
                    "-select_streams", "s",
                    "-show_entries", "stream=index:stream_tags=language",
                    "-of", "csv=p=0",
                    videoPath
                )
                result.isNotEmpty()
            } else {
                // 回退：解析 ffmpeg -i 的 stderr 输出
                val output = execCommand(
                    ffmpegPath, "-i", videoPath,
                    "-f", "null", "-"
                )
                output.contains("Subtitle:", ignoreCase = true) ||
                    output.matches(Regex("(?s).*Stream.*Subtitle.*"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "检测字幕轨道失败: ${e.message}", e)
            false
        }
    }

    /**
     * 提取第一个字幕轨道为 SRT 格式。
     *
     * @param videoPath  视频文件路径
     * @param outputPath 输出的 .srt 文件路径
     * @return Success(outputPath) 或失败信息
     */
    suspend fun extractSubtitle(videoPath: String, outputPath: String): Result<String> {
        return try {
            val outputFile = File(outputPath)
            // 确保父目录存在
            outputFile.parentFile?.mkdirs()

            val exitCode = execCommandWithExitCode(
                ffmpegPath,
                "-y",                     // 覆盖已有文件
                "-i", videoPath,
                "-map", "0:s:0",         // 取第一个字幕流
                "-f", "srt",
                outputPath
            )

            if (exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
                Log.i(TAG, "字幕提取成功: $outputPath (${outputFile.length()} bytes)")
                Result.success(outputPath)
            } else {
                // 尝试用编解码器转换
                val retryExitCode = execCommandWithExitCode(
                    ffmpegPath,
                    "-y",
                    "-i", videoPath,
                    "-map", "0:s:0",
                    "-c:s", "srt",
                    outputPath
                )
                if (retryExitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
                    Log.i(TAG, "字幕提取成功(c:s srt): $outputPath")
                    Result.success(outputPath)
                } else {
                    Result.failure(IOException(
                        "字幕提取失败，exitCode=$exitCode，retryExitCode=$retryExitCode，" +
                            "output存在=${outputFile.exists()}，size=${outputFile.length()}"
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取字幕异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 提取视频中的音频为 WAV 格式（用于 ASR 语音识别）。
     *
     * @param videoPath  视频文件路径
     * @param outputPath 输出的音频文件路径（建议 .wav 或 .mp3）
     * @return Success(outputPath) 或失败信息
     */
    suspend fun extractAudio(videoPath: String, outputPath: String): Result<String> {
        return try {
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()

            // 输出格式取扩展名
            val ext = outputPath.substringAfterLast('.', "wav").lowercase()
            val codec = when (ext) {
                "wav"  -> "pcm_s16le"
                "mp3"  -> "libmp3lame"
                "m4a"  -> "aac"
                "ogg"  -> "libvorbis"
                "opus" -> "libopus"
                else   -> "pcm_s16le" // 默认 WAV
            }

            val exitCode = execCommandWithExitCode(
                ffmpegPath,
                "-y",
                "-i", videoPath,
                "-vn",                    // 无视频
                "-acodec", codec,
                "-ar", "16000",           // 16kHz（ASR 最佳采样率）
                "-ac", "1",               // 单声道
                outputPath
            )

            if (exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
                Log.i(TAG, "音频提取成功: $outputPath (${outputFile.length()} bytes)")
                Result.success(outputPath)
            } else {
                // 回退：使用默认编码器
                val fallbackExitCode = execCommandWithExitCode(
                    ffmpegPath,
                    "-y",
                    "-i", videoPath,
                    "-vn",
                    "-ar", "16000",
                    "-ac", "1",
                    outputPath
                )
                if (fallbackExitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
                    Result.success(outputPath)
                } else {
                    Result.failure(IOException(
                        "音频提取失败，exitCode=$exitCode，fallbackExitCode=$fallbackExitCode"
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取音频异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 列出视频中所有内嵌字幕流信息。
     */
    suspend fun listSubtitleStreams(videoPath: String): List<SubtitleStreamInfo> {
        return try {
            // 优先 ffprobe
            if (File(ffprobePath).exists()) {
                val raw = execCommand(
                    ffprobePath,
                    "-v", "error",
                    "-select_streams", "s",
                    "-show_entries", "stream=index:stream_tags=language,codec_name",
                    "-of", "csv=p=0",
                    videoPath
                )
                raw.lines().filter { it.isNotBlank() }.mapIndexed { idx, line ->
                    val parts = line.split(",")
                    SubtitleStreamInfo(
                        index = idx.toString(),
                        codec = parts.getOrElse(0) { "unknown" },
                        language = parts.getOrElse(1) { "und" }
                    )
                }
            } else {
                // 回退：解析 ffmpeg -i 输出
                val raw = execCommand(ffmpegPath, "-i", videoPath)
                val streamRegex = Regex(
                    """Stream\s+#(\d+:\d+).*?Subtitle:\s*(\w+).*?(\w{2,3}(?:-\w+)?)?"""
                )
                raw.lines().mapNotNull { line ->
                    streamRegex.find(line)?.let { match ->
                        SubtitleStreamInfo(
                            index = match.groupValues[1],
                            codec = match.groupValues[2],
                            language = match.groupValues.getOrElse(3) { "und" }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "列出字幕流失败: ${e.message}", e)
            emptyList()
        }
    }

    // ----- 内部工具方法 ------------------------------------------

    /**
     * 执行命令并返回 stdout + stderr 合并输出。
     */
    private fun execCommand(vararg cmd: String): String {
        val process = ProcessBuilder(*cmd)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(30, TimeUnit.SECONDS)
        return output
    }

    /**
     * 执行命令并返回退出码，同时收集日志。
     */
    private fun execCommandWithExitCode(vararg cmd: String): Int {
        val pb = ProcessBuilder(*cmd)
            .redirectErrorStream(true)
        val process = pb.start()
        val output = process.inputStream.bufferedReader().readText()
        val exited = process.waitFor(30, TimeUnit.SECONDS)
        val exitCode = if (exited) process.exitValue() else {
            process.destroyForcibly()
            Log.w(TAG, "命令超时(30s): ${cmd.take(3).joinToString(" ")}...")
            -1
        }
        if (exitCode != 0) {
            Log.w(TAG, "命令 exit=$exitCode:\n${output.take(500)}")
        }
        return exitCode
    }
}

/**
 * 字幕流信息
 */
data class SubtitleStreamInfo(
    val index: String,
    val codec: String,
    val language: String = "und"
)
