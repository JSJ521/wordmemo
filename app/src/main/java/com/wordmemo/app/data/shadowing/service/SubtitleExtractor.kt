package com.wordmemo.app.data.shadowing.service

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
     * 提取视频音频为 16kHz 单声道 16-bit PCM WAV 文件（用于 Vosk ASR）。
     *
     * 用 Android 原生 MediaExtractor + MediaCodec 解码，
     * 绕过 Android 10+ noexec 限制（旧版 FFmpeg ProcessBuilder 方案不可用）。
     *
     * 处理流程：
     * 1. MediaExtractor 选择音频轨道
     * 2. MediaCodec 解码为 PCM 16-bit shorts
     * 3. 立体声 → 下混为单声道
     * 4. 重采样至 16000Hz（线性插值）
     * 5. 写标准 WAV 头（44 bytes）+ PCM 数据体
     */
    @Suppress("InlinedApi")
    suspend fun extractAudio(videoPath: String, outputPath: String): Result<String> {
        return withContext(Dispatchers.IO) {
            var extractor: MediaExtractor? = null
            var decoder: MediaCodec? = null
            try {
                File(outputPath).parentFile?.mkdirs()

                // ── 1. MediaExtractor 选择音频轨道 ──
                extractor = MediaExtractor()
                extractor.setDataSource(videoPath)

                var audioTrackIdx = -1
                var audioMime = ""
                for (i in 0 until extractor.trackCount) {
                    val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIdx = i
                        audioMime = mime
                        break
                    }
                }
                if (audioTrackIdx < 0) {
                    return@withContext Result.failure(
                        IOException("视频中未找到音频轨道: $videoPath")
                    )
                }

                extractor.selectTrack(audioTrackIdx)
                Log.i(TAG, "选中音频轨道 #$audioTrackIdx: $audioMime")

                // ── 2. MediaCodec 解码器 ──
                decoder = MediaCodec.createDecoderByType(audioMime)
                decoder.configure(extractor.getTrackFormat(audioTrackIdx), null, null, 0)
                decoder.start()

                val allFrames = mutableListOf<ShortArray>()
                val bufferInfo = BufferInfo()
                var outputFormat: MediaFormat? = null
                var inputEOS = false
                var outputEOS = false

                while (!outputEOS) {
                    // ── 2a. 喂入数据 ──
                    if (!inputEOS) {
                        val inputIdx = decoder.dequeueInputBuffer(10_000L)
                        if (inputIdx >= 0) {
                            val inputBuf = decoder.getInputBuffer(inputIdx)!!
                            val sampleSize = extractor.readSampleData(inputBuf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(
                                    inputIdx, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputEOS = true
                            } else {
                                decoder.queueInputBuffer(
                                    inputIdx, 0, sampleSize,
                                    extractor.sampleTime, 0
                                )
                                extractor.advance()
                            }
                        }
                    }

                    // ── 2b. 取出输出 ──
                    val outputIdx = decoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                    when {
                        outputIdx >= 0 -> {
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputEOS = true
                            }
                            if (bufferInfo.size > 0) {
                                val outBuf = decoder.getOutputBuffer(outputIdx)!!
                                outBuf.position(bufferInfo.offset)
                                // 读取 PCM 16-bit shorts（绝大多数据解码器输出 16-bit PCM）
                                val shortCount = bufferInfo.size / 2
                                val shorts = ShortArray(shortCount)
                                outBuf.order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts)
                                allFrames.add(shorts)
                            }
                            decoder.releaseOutputBuffer(outputIdx, false)
                        }
                        outputIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            outputFormat = decoder.outputFormat
                        }
                        outputIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // 无可用输出，继续循环
                        }
                    }
                }

                decoder.stop()
                decoder.release()
                decoder = null
                extractor.release()
                extractor = null

                if (allFrames.isEmpty()) {
                    return@withContext Result.failure(IOException("未解码出音频数据"))
                }

                // ── 3. 拼接所有 PCM 帧 ──
                val totalShorts = allFrames.sumOf { it.size }
                val allSamples = ShortArray(totalShorts)
                var writePos = 0
                for (frame in allFrames) {
                    System.arraycopy(frame, 0, allSamples, writePos, frame.size)
                    writePos += frame.size
                }

                // ── 4. 获取原始格式信息 ──
                val outFmt = outputFormat ?: run {
                    return@withContext Result.failure(IOException("未获取到输出格式"))
                }
                val srcSampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100)
                val srcChannels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)

                // ── 5. 转换为 16kHz 单声道并写 WAV ──
                writeAudioAsWav(outputPath, allSamples, srcSampleRate, srcChannels)

                if (File(outputPath).exists() && File(outputPath).length() > 44L) {
                    Log.i(TAG, "音频提取成功: $outputPath " +
                            "(${File(outputPath).length()} bytes, ${srcSampleRate}Hz/${srcChannels}ch " +
                            "→ 16000Hz/1ch)")
                    Result.success(outputPath)
                } else {
                    Result.failure(IOException("WAV 文件写入失败或为空"))
                }

            } catch (e: Exception) {
                Log.e(TAG, "音频提取异常: ${e.message}", e)
                Result.failure(e)
            } finally {
                try { decoder?.stop() } catch (_: Exception) {}
                try { decoder?.release() } catch (_: Exception) {}
                try { extractor?.release() } catch (_: Exception) {}
            }
        }
    }

    // ==================== 音频处理辅助方法 ====================

    /**
     * 将原始 PCM 数据转换为 16kHz 单声道 WAV 文件。
     * 处理：立体声下混、重采样、WAV 头写入。
     */
    private fun writeAudioAsWav(
        outputPath: String,
        srcSamples: ShortArray,
        srcSampleRate: Int,
        srcChannels: Int
    ) {
        // 下混为单声道
        val mono = if (srcChannels > 1) {
            downmixToMono(srcSamples, srcChannels)
        } else {
            srcSamples
        }

        // 重采样至 16000Hz
        val resampled = if (srcSampleRate != 16000) {
            resampleTo16000(mono, srcSampleRate)
        } else {
            mono
        }

        // 写 WAV 文件
        writeWavFile(outputPath, resampled, 16000)
    }

    /**
     * 立体声/多声道 → 单声道下混。
     * 对每帧各声道求和取平均值。
     */
    private fun downmixToMono(samples: ShortArray, channels: Int): ShortArray {
        val frameCount = samples.size / channels
        val mono = ShortArray(frameCount)
        for (i in 0 until frameCount) {
            var sum = 0
            for (ch in 0 until channels) {
                sum += samples[i * channels + ch].toInt()
            }
            mono[i] = (sum / channels).toShort()
        }
        return mono
    }

    /**
     * 线性插值重采样至 16000Hz。
     * 支持任意源采样率 → 16kHz。
     */
    private fun resampleTo16000(input: ShortArray, srcSampleRate: Int): ShortArray {
        if (srcSampleRate == 16000) return input

        val ratio = srcSampleRate.toDouble() / 16000.0
        val outputLen = (input.size / ratio + 0.5).toInt().coerceAtLeast(1)
        val output = ShortArray(outputLen)

        for (i in 0 until outputLen) {
            val srcPos = i * ratio
            val srcIdx = srcPos.toInt()
            val frac = srcPos - srcIdx

            if (srcIdx + 1 < input.size) {
                // 线性插值
                val s0 = input[srcIdx].toInt() and 0xFFFF
                val s1 = input[srcIdx + 1].toInt() and 0xFFFF
                val interpolated = (s0 * (1.0 - frac) + s1 * frac + 0.5).toInt()
                output[i] = (interpolated.coerceIn(0, 0xFFFF) - 32768).toShort()
            } else if (srcIdx < input.size) {
                output[i] = input[srcIdx]
            }
        }
        return output
    }

    /**
     * 写标准 44 字节 WAV 头 + 16-bit PCM 数据体。
     * 格式: PCM, 单声道, 16-bit, 16000Hz (little-endian)。
     */
    private fun writeWavFile(path: String, samples: ShortArray, sampleRate: Int) {
        val file = File(path).also { it.parentFile?.mkdirs() }
        val dataSize = samples.size * 2       // 16-bit = 2 bytes/sample
        val fileSize = 44 + dataSize

        RandomAccessFile(file, "rw").use { raf ->
            // RIFF header
            raf.writeBytes("RIFF")
            raf.writeInt(Integer.reverseBytes(fileSize - 8))
            raf.writeBytes("WAVE")

            // fmt chunk (16 bytes)
            raf.writeBytes("fmt ")
            raf.writeInt(Integer.reverseBytes(16))          // chunk size
            raf.writeShort(Integer.reverseBytes(1) shr 16)   // PCM format (LE)
            raf.writeShort(Integer.reverseBytes(1) shr 16)   // mono (LE)
            raf.writeInt(Integer.reverseBytes(sampleRate))  // sample rate
            raf.writeInt(Integer.reverseBytes(sampleRate * 2)) // byte rate
            raf.writeShort(Integer.reverseBytes(2) shr 16)   // block align (LE)
            raf.writeShort(Integer.reverseBytes(16) shr 16)  // bits per sample (LE)

            // data chunk
            raf.writeBytes("data")
            raf.writeInt(Integer.reverseBytes(dataSize))

            // PCM data (little-endian 16-bit signed)
            val buf = ByteBuffer.allocate(dataSize)
            buf.order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) {
                buf.putShort(s)
            }
            raf.write(buf.array())
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
