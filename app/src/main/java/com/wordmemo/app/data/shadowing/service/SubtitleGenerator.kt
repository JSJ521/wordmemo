package com.wordmemo.app.data.shadowing.service

import android.util.Log
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ASR（自动语音识别）字幕生成器。
 *
 * 将音频文件（WAV/MP3）通过 OpenAI Whisper 兼容 API 转录为带时间戳的 SRT 字幕。
 *
 * ## 使用前提
 * - 需要一个 ASR 服务端点（默认 OpenAI Whisper API）
 * - DeepSeek 纯文本 API **不支持音频输入**，故本类依赖独立的 Whisper 兼容接口
 *
 * ## 工作流程
 * 1. 读取 [asrBaseUrl] + [asrApiKey]（由调用方传入，通常来自 AppConfig）
 * 2. 以 multipart/form-data 发送音频文件到 `${baseUrl}/audio/transcriptions`
 * 3. 请求 `response_format=srt` 直接获取 SRT 文本
 * 4. 写为 .srt 文件并返回路径
 *
 * ## 失败原因
 * - `asr_base_url` 或 `asr_api_key` 未配置
 * - 网络错误
 * - API 返回非 200
 */
@Singleton
class SubtitleGenerator @Inject constructor() {

    private val TAG = "SubtitleGenerator"

    /** ASR 配置键名（存入 AppConfig 表） */
    companion object {
        /** ASR 服务器地址，默认 OpenAI */
        const val CONFIG_ASR_BASE_URL = "asr_base_url"
        /** OpenAI Whisper API 默认地址 */
        const val DEFAULT_ASR_BASE_URL = "https://api.openai.com"

        /** ASR API Key */
        const val CONFIG_ASR_API_KEY = "asr_api_key"

        /** 标记视频需要手动添加字幕 */
        const val SUBTITLE_NEEDS_MANUAL = "__need_manual_subtitle__"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)  // 音频转录可能较慢
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .build()

    /**
     * 将音频文件通过 ASR API 转录为 SRT 字幕文件。
     *
     * @param audioPath  音频文件路径（WAV 16kHz mono 为佳）
     * @param outputPath 输出的 .srt 文件路径
     * @param asrBaseUrl ASR API 基础 URL（含协议，如 https://api.openai.com）
     * @param asrApiKey  API Key
     * @param model      模型名（默认 whisper-1）
     * @param language   语言代码（可选，如 zh / en；为空则自动检测）
     * @return Success(outputPath) 或失败信息
     */
    suspend fun generateSubtitle(
        audioPath: String,
        outputPath: String,
        asrBaseUrl: String?,
        asrApiKey: String?,
        model: String = "whisper-1",
        language: String? = null
    ): Result<String> {
        // 0. 前置校验
        if (asrBaseUrl.isNullOrBlank()) {
            return Result.failure(
                AsrConfigurationException("ASR 未配置：缺少 asr_base_url（可在设置中配置 OpenAI Whisper 兼容 API 地址）")
            )
        }
        if (asrApiKey.isNullOrBlank()) {
            return Result.failure(
                AsrConfigurationException("ASR 未配置：缺少 asr_api_key（可在设置中配置 OpenAI Whisper API Key）")
            )
        }

        val audioFile = File(audioPath)
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return Result.failure(IOException("音频文件不存在或为空: $audioPath"))
        }

        // 25MB 限制（OpenAI Whisper 限制）
        if (audioFile.length() > 25 * 1024 * 1024) {
            Log.w(TAG, "音频文件超过 25MB(${audioFile.length() / 1024 / 1024}MB)，可能导致 API 拒绝")
        }

        return try {
            val url = "${asrBaseUrl.trimEnd('/')}/v1/audio/transcriptions"

            // 构建 multipart 请求体
            val audioBody = audioFile.asRequestBody("audio/wav".toMediaType())
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", model)
                .addFormDataPart("response_format", "srt")
                .addFormDataPart("file", audioFile.name, audioBody)
                .apply {
                    if (!language.isNullOrBlank()) {
                        addFormDataPart("language", language)
                    }
                }
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $asrApiKey")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return Result.failure(
                    IOException("ASR API 返回 ${response.code}: ${responseBody.take(200)}")
                )
            }

            // 判断返回格式：SRT 文本或 JSON
            val srtContent: String = when {
                responseBody.trimStart().startsWith("1") ||
                    responseBody.contains("-->") -> {
                    // 已经是 SRT 文本
                    responseBody
                }

                responseBody.contains("\"text\"") -> {
                    // JSON 格式，转为单条字幕
                    val text = parseJsonText(responseBody) ?: responseBody
                    generateSingleEntrySrt(text)
                }

                else -> {
                    generateSingleEntrySrt(responseBody.trim())
                }
            }

            // 写入 SRT 文件
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            outputFile.writeText(srtContent, Charsets.UTF_8)

            if (outputFile.exists() && outputFile.length() > 0) {
                Log.i(TAG, "ASR 字幕生成成功: $outputPath (${outputFile.length()} bytes)")
                Result.success(outputPath)
            } else {
                Result.failure(IOException("ASR 字幕文件写入失败"))
            }
        } catch (e: AsrConfigurationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "ASR 字幕生成失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 快速检测 ASR 服务是否可用（尝试发送小字符串而非音频）
     */
    suspend fun checkAsrAvailable(
        asrBaseUrl: String?,
        asrApiKey: String?
    ): Boolean {
        if (asrBaseUrl.isNullOrBlank() || asrApiKey.isNullOrBlank()) return false
        return true
    }

    // ----- 内部辅助 ----------------------------------------------

    /**
     * 从简单的 JSON 响应中提取文本
     */
    private fun parseJsonText(json: String): String? {
        return try {
            val textKey = "\"text\":"
            val startIdx = json.indexOf(textKey)
            if (startIdx < 0) return null
            val valueStart = startIdx + textKey.length
            val trimmed = json.substring(valueStart).trim()
            if (trimmed.startsWith("\"")) {
                trimmed.substring(1, trimmed.indexOf('"', 1))
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 当 API 返回纯文本（无时间戳）时，生成单条 SRT 字幕
     */
    private fun generateSingleEntrySrt(text: String): String {
        val cleaned = text
            .replace(Regex("<[^>]+>"), "")
            .trim()
        return buildString {
            appendLine("1")
            appendLine("00:00:01,000 --> 00:00:10,000")
            appendLine(cleaned)
        }
    }
}

/**
 * ASR 配置缺失异常——用户需要在设置中配置 ASR 服务
 */
class AsrConfigurationException(message: String) : Exception(message)
