package com.wordmemo.app.data.shadowing.service

import android.content.ContentResolver
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音频波形振幅提取器。
 *
 * 从音频文件（MP3/AAC/WAV）中解码 PCM 数据，然后计算分段振幅。
 * 输出 BAR_COUNT 个振幅值（0.0 ~ 1.0），用于 Compose Canvas 波形绘制。
 */
@Singleton
class AudioWaveformExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AudioWaveformExtractor"
        /** 输出波形条数 */
        private const val BAR_COUNT = 120
        /** 每段采样数（用于平滑） */
        private const val SAMPLES_PER_BAR = 256
    }

    /**
     * 从录音文件提取波形振幅。
     * @param filePath 音频文件路径
     * @return 振幅列表 (0.0~1.0)，空列表表示提取失败
     */
    fun extractFromFile(filePath: String): List<Float> {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "文件不存在或为空: $filePath")
            return emptyList()
        }
        return try {
            val pcm = decodeToPcm(filePath) ?: return emptyList()
            computeAmplitudes(pcm, BAR_COUNT)
        } catch (e: Exception) {
            Log.e(TAG, "音频提取失败: $filePath", e)
            fallbackExtractFromRaw(file)
        }
    }

    /**
     * 从 Content URI 提取波形振幅。
     */
    fun extractFromUri(uri: Uri): List<Float> {
        return try {
            val pcm = decodeToPcm(uri) ?: return emptyList()
            computeAmplitudes(pcm, BAR_COUNT)
        } catch (e: Exception) {
            Log.e(TAG, "URI 音频提取失败: $uri", e)
            emptyList()
        }
    }

    /**
     * 从 PCM 字节数组计算波形振幅。
     * @param pcmData 16-bit PCM 数据（小端序）
     * @param barCount 输出条数
     */
    private fun computeAmplitudes(pcmData: ShortArray, barCount: Int): List<Float> {
        if (pcmData.isEmpty()) return emptyList()

        val samplesPerBar = maxOf(1, pcmData.size / barCount)
        val amplitudes = mutableListOf<Float>()

        for (i in 0 until barCount) {
            val start = i * samplesPerBar
            val end = minOf(start + samplesPerBar, pcmData.size)
            if (start >= pcmData.size) break

            var sumSquared = 0.0
            for (j in start until end) {
                val sample = pcmData[j].toDouble() / Short.MAX_VALUE
                sumSquared += sample * sample
            }
            val rms = kotlin.math.sqrt(sumSquared / (end - start))
            // 对数压缩，让小声音也可见
            val compressed = if (rms > 0.001f) {
                (kotlin.math.log10(1.0 + 9.0 * rms)).toFloat()
            } else {
                0f
            }
            amplitudes.add(compressed.coerceIn(0f, 1f))
        }

        // 如果不足 barCount，补零
        while (amplitudes.size < barCount) {
            amplitudes.add(0f)
        }

        return amplitudes
    }

    /**
     * 用 MediaExtractor + MediaCodec 解码音频文件到 16-bit PCM。
     */
    private fun decodeToPcm(filePath: String): ShortArray? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(filePath)
            decodeInternal(extractor)
        } catch (e: Exception) {
            Log.e(TAG, "decodeToPcm 失败: $filePath", e)
            null
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun decodeToPcm(uri: Uri): ShortArray? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            decodeInternal(extractor)
        } catch (e: Exception) {
            Log.e(TAG, "decodeToPcm(uri) 失败: $uri", e)
            null
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun decodeInternal(extractor: MediaExtractor): ShortArray? {
        // 找到音频轨道
        var audioTrackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                break
            }
        }
        if (audioTrackIndex < 0) {
            Log.w(TAG, "未找到音频轨道")
            return null
        }

        extractor.selectTrack(audioTrackIndex)
        val format = extractor.getTrackFormat(audioTrackIndex)

        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        val codec = MediaCodec.createDecoderByType(mime)
        try {
            codec.configure(format, null, null, 0)
            codec.start()

            val allPcm = mutableListOf<Short>()
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inputIndex = codec.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                if (outputIndex >= 0) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEos = true
                    }

                    if (bufferInfo.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex) ?: continue
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        // 转换为 ShortArray
                        val shorts = ByteBufferToShorts(outputBuffer)
                        allPcm.addAll(shorts.asList())
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }

            return allPcm.toShortArray()
        } catch (e: Exception) {
            Log.e(TAG, "解码异常", e)
            return null
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
        }
    }

    private fun ByteBufferToShorts(buffer: ByteBuffer): ShortArray {
        val remaining = buffer.remaining()
        val shortCount = remaining / 2
        val result = ShortArray(shortCount)
        // 确保是小端序
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until shortCount) {
            result[i] = buffer.getShort()
        }
        return result
    }

    /**
     * 兜底：直接从文件头解析 WAV，或从 MP3 ID3 数据估算。
     * 当 MediaCodec 解码失败时使用。
     */
    private fun fallbackExtractFromRaw(file: File): List<Float> {
        return try {
            // 尝试作为 WAV 文件读取
            val input = FileInputStream(file)
            val header = ByteArray(44)
            if (input.read(header) < 44) {
                input.close()
                return emptyList()
            }
            input.close()

            // 检查 RIFF/WAV 头
            val riff = String(header.sliceArray(0..3))
            if (riff != "RIFF") return emptyList()

            // 读取数据块大小
            val dataSize = byteArrayOf(
                header[40], header[41], header[42], header[43]
            ).let {
                ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).int
            }
            if (dataSize <= 0) return emptyList()

            val pcmInput = FileInputStream(file)
            pcmInput.skip(44)
            val pcmData = ByteArray(minOf(dataSize, 1_000_000)) // 最多读 1MB
            val read = pcmInput.read(pcmData)
            pcmInput.close()
            if (read <= 0) return emptyList()

            val buffer = ByteBuffer.wrap(pcmData, 0, read).order(ByteOrder.LITTLE_ENDIAN)
            val shortCount = read / 2
            val shorts = ShortArray(shortCount)
            for (i in 0 until shortCount) {
                shorts[i] = buffer.getShort()
            }

            computeAmplitudes(shorts, BAR_COUNT)
        } catch (e: Exception) {
            Log.e(TAG, "fallback 提取失败", e)
            emptyList()
        }
    }
}
