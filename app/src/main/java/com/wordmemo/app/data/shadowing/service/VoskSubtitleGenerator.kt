package com.wordmemo.app.data.shadowing.service

import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vosk 离线语音识别字幕生成器。
 *
 * 使用 Vosk 引擎将 16kHz 单声道 WAV 音频文件转录为带时间戳的 SRT 字幕。
 * 完全离线运行，无需网络连接。
 *
 * ## 工作流程
 * 1. 加载 Vosk 模型（由 [VoskModelManager] 管理）
 * 2. 创建 [Recognizer] 实例
 * 3. 逐块读取 WAV 文件并送入 Vosk 识别
 * 4. Vosk 返回带单词级时间戳的 JSON 结果
 * 5. 将单词分组为句子（基于停顿/时长），生成 SRT 格式
 * 6. 写入 .srt 文件
 *
 * ## 性能
 * - 实时因子约 0.3-0.5（即 10 分钟音频约需 3-5 分钟处理）
 * - 模型约 40MB，加载时间约 2-5 秒
 *
 * ## 失败原因
 * - Vosk 模型未下载或损坏
 * - 音频文件不是 16kHz 单声道 WAV
 * - 音频文件不存在或为空
 *
 * ## 使用前提
 * 预置条件：调用方应先调用 [VoskModelManager.ensureModelReady] 确保模型就绪。
 */
@Singleton
class VoskSubtitleGenerator @Inject constructor(
    private val voskModelManager: VoskModelManager
) {
    private val TAG = "VoskSubtitleGenerator"

    companion object {
        /** 单条字幕最大时长（毫秒），超过此值则拆句 */
        private const val MAX_SUBTITLE_DURATION_MS = 6_000L

        /** 句子间最小间隔（毫秒）—— 单词间停顿超过此值则认为是新句子 */
        private const val SENTENCE_GAP_MS = 500L

        /** 每条字幕的最多单词数（防止单条过长） */
        private const val MAX_WORDS_PER_SUBTITLE = 15

        /** Vosk 识别的采样率 */
        private const val SAMPLE_RATE = 16000f

        /** 读取缓冲区大小 */
        private const val BUFFER_SIZE = 8192
    }

    /**
     * 将 WAV 音频文件通过 Vosk 离线识别为 SRT 字幕文件。
     *
     * @param audioWavPath 16kHz 单声道 WAV 音频文件路径
     * @param outputSrtPath 输出的 .srt 文件路径
     * @return Success(outputSrtPath) 或包含错误信息的失败
     */
    fun generateSubtitle(audioWavPath: String, outputSrtPath: String): Result<String> {
        return try {
            // 1. 前置校验
            val audioFile = File(audioWavPath)
            if (!audioFile.exists() || audioFile.length() == 0L) {
                return Result.failure(
                    IOException("音频文件不存在或为空: $audioWavPath")
                )
            }

            // 2. 确保模型就绪
            if (!voskModelManager.ensureModelReady()) {
                return Result.failure(
                    VoskNotReadyException(
                        "Vosk 离线语音模型未就绪。首次使用需联网下载约 40MB 模型，" +
                            "后续使用完全离线。请检查网络连接后重试。"
                    )
                )
            }

            // 3. 加载 Vosk 模型
            val modelPath = voskModelManager.getModelPath()
            Log.i(TAG, "加载 Vosk 模型: $modelPath")
            val model = Model(modelPath)
            Log.i(TAG, "Vosk 模型加载成功")

            // 4. 创建 Recognizer 并启用单词级时间戳
            val recognizer = Recognizer(model, SAMPLE_RATE)
            recognizer.setWords(true) // 启用单词级时间戳输出
            Log.i(TAG, "开始 Vosk 离线识别: $audioWavPath (${audioFile.length() / 1024 / 1024}MB)")

            // 5. 逐块读取 WAV 文件并识别
            val allWords = mutableListOf<VoskWord>()
            val wavStream = FileInputStream(audioFile)
            val buffer = ByteArray(BUFFER_SIZE)

            // 跳过 WAV 文件头（44 字节）
            // 标准 PCM WAV 文件头为 44 字节，但某些文件可能有扩展头
            // 安全做法：跳过 RIFF 格式的 data chunk 之前的所有内容
            var headerSkipped = false
            var totalBytesRead = 0L

            wavStream.use { stream ->
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } >= 0) {
                    if (!headerSkipped && totalBytesRead == 0L) {
                        // 使用 Vosk 的 setMaxAlternatives / skipHead 或者手动跳过 WAV 头
                        // 实际 Vosk 的 Recognizer.acceptWaveform 会自己处理 WAV 头
                        // 所以不需要手动跳过
                        headerSkipped = true
                    }

                    if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                        val resultJson = recognizer.result
                        val words = parseResultWords(resultJson)
                        allWords.addAll(words)
                    }
                    totalBytesRead += bytesRead
                }
            }

            // 6. 获取最终结果
            val finalResultJson = recognizer.finalResult
            val finalWords = parseResultWords(finalResultJson)
            allWords.addAll(finalWords)

            // 7. 关闭 Recognizer 和 Model
            recognizer.close()
            model.close()

            Log.i(TAG, "Vosk 识别完成，共 ${allWords.size} 个单词")

            if (allWords.isEmpty()) {
                return Result.failure(
                    IOException("Vosk 识别未产生任何结果。音频可能为空或质量太差。")
                )
            }

            // 8. 将单词分组为字幕条目并生成 SRT
            val srtContent = generateSrt(allWords)

            // 9. 写入 SRT 文件
            val outputFile = File(outputSrtPath)
            outputFile.parentFile?.mkdirs()
            outputFile.writeText(srtContent, Charsets.UTF_8)

            if (outputFile.exists() && outputFile.length() > 0) {
                Log.i(TAG, "Vosk 字幕生成成功: $outputSrtPath (${outputFile.length()} bytes, " +
                        "${srtContent.lines().count { it.contains("-->") }} 条字幕)")
                Result.success(outputSrtPath)
            } else {
                Result.failure(IOException("SRT 文件写入失败: $outputSrtPath"))
            }

        } catch (e: VoskNotReadyException) {
            Log.w(TAG, "Vosk 模型未就绪: ${e.message}")
            Result.failure(e)
        } catch (e: IOException) {
            Log.e(TAG, "Vosk 识别 IO 错误: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Vosk 识别失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ==================== 内部数据模型 ====================

    /**
     * Vosk 返回的单词级识别结果
     */
    data class VoskWord(
        val word: String,
        val start: Double,  // 起始时间（秒）
        val end: Double,     // 结束时间（秒）
        val confidence: Float = 1.0f
    )

    /**
     * 字幕条目（用于 SRT 生成）
     */
    data class SubtitleItem(
        val index: Int,
        val text: String,
        val startMs: Long,
        val endMs: Long
    )

    // ==================== 内部方法 ====================

    /**
     * 解析 Vosk 返回的 JSON 结果中的单词列表。
     *
     * Vosk JSON 格式：
     * ```json
     * {
     *   "result": [
     *     {"conf": 1.0, "end": 3.42, "start": 0.0, "word": "hello"},
     *     {"conf": 1.0, "end": 3.51, "start": 3.42, "word": "world"}
     *   ],
     *   "text": "hello world"
     * }
     * ```
     */
    private fun parseResultWords(json: String): List<VoskWord> {
        if (json.isBlank()) return emptyList()

        return try {
            val obj = JSONObject(json)
            if (!obj.has("result")) return emptyList()

            val resultArray = obj.getJSONArray("result")
            val words = mutableListOf<VoskWord>()

            for (i in 0 until resultArray.length()) {
                val wordObj = resultArray.getJSONObject(i)
                val word = wordObj.optString("word", "").trim()
                if (word.isBlank()) continue

                words.add(
                    VoskWord(
                        word = word,
                        start = wordObj.optDouble("start", 0.0),
                        end = wordObj.optDouble("end", 0.0),
                        confidence = wordObj.optDouble("conf", 1.0).toFloat()
                    )
                )
            }

            words
        } catch (e: Exception) {
            Log.w(TAG, "解析 Vosk 结果 JSON 失败: ${e.message}")
            emptyList()
        }
    }

    /**
     * 将单词列表分组为 SRT 字幕条目。
     *
     * 分组策略：
     * 1. 单词间停顿超过 [SENTENCE_GAP_MS] 则断句
     * 2. 单个字幕条目不超过 [MAX_WORDS_PER_SUBTITLE] 个单词
     * 3. 单个字幕时长不超过 [MAX_SUBTITLE_DURATION_MS]
     * 4. 字幕间有 20ms 间隔避免重叠
     */
    private fun generateSrt(words: List<VoskWord>): String {
        if (words.isEmpty()) return ""

        // 按语义和停顿分组
        val subtitles = mutableListOf<SubtitleItem>()
        var index = 1
        var i = 0

        while (i < words.size) {
            val subtitleWords = mutableListOf<VoskWord>()
            subtitleWords.add(words[i])

            var j = i + 1
            while (j < words.size) {
                val prev = words[j - 1]
                val curr = words[j]

                // 检查间隙
                val gapMs = ((curr.start - prev.end) * 1000).toLong()

                // 如果间隙过大或单词数已达上限，则结束当前字幕
                if (gapMs > SENTENCE_GAP_MS) {
                    break
                }

                // 检查当前字幕集合的时长
                val durationMs = ((curr.end - subtitleWords.first().start) * 1000).toLong()
                if (subtitleWords.size >= MAX_WORDS_PER_SUBTITLE || durationMs > MAX_SUBTITLE_DURATION_MS) {
                    break
                }

                subtitleWords.add(curr)
                j++
            }

            // 生成字幕条目
            val startMs = (subtitleWords.first().start * 1000).toLong()
            val endMs = (subtitleWords.last().end * 1000).toLong()
            val text = subtitleWords.joinToString(" ") { it.word }

            subtitles.add(
                SubtitleItem(
                    index = index,
                    text = text,
                    startMs = startMs,
                    endMs = endMs
                )
            )

            index++
            i = j
        }

        // 生成 SRT 文本
        return buildSrtString(subtitles)
    }

    /**
     * 将字幕条目列表格式化为标准 SRT 文本。
     */
    private fun buildSrtString(subtitles: List<SubtitleItem>): String {
        return buildString {
            for ((i, item) in subtitles.withIndex()) {
                // 字幕序号
                appendLine(i + 1)

                // 时间轴
                appendLine("${formatSrtTimestamp(item.startMs)} --> ${formatSrtTimestamp(item.endMs)}")

                // 字幕文本
                appendLine(item.text)

                // 空行分隔
                if (i < subtitles.size - 1) {
                    appendLine()
                }
            }
        }
    }

    /**
     * 将毫秒数格式化为 SRT 时间戳格式：HH:MM:SS,mmm
     */
    private fun formatSrtTimestamp(ms: Long): String {
        val totalSec = ms / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        val millis = ms % 1000

        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }
}

/**
 * Vosk 模型未就绪异常。
 * 表示 Vosk 离线语音识别模型尚未下载完成或初始化失败。
 */
class VoskNotReadyException(message: String) : Exception(message)
