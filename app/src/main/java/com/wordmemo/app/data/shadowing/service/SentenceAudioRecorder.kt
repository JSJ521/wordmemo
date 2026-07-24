package com.wordmemo.app.data.shadowing.service

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 逐句录音服务 — 使用 MediaRecorder 录制用户跟读声音。
 *
 * 存储路径: {filesDir}/shadowing/{videoId}/{sentenceId}.mp3
 * 每个句子独立文件，方便回放对比和清理。
 */
@Singleton
class SentenceAudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioPath: String? = null

    companion object {
        private const val TAG = "SentenceAudioRecorder"
        private const val SHADOWING_DIR = "shadowing"
    }

    /**
     * 开始录音。
     * @param videoId 视频 ID，用于组织目录
     * @param sentenceId 句子 ID，用于命名文件
     * @return 录音文件的绝对路径
     */
    fun startRecording(videoId: Long, sentenceId: Long): String {
        releaseQuietly()

        val dir = File(context.filesDir, "$SHADOWING_DIR/$videoId")
        if (!dir.exists()) dir.mkdirs()

        val audioFile = File(dir, "sentence_${sentenceId}.mp3")
        val filePath = audioFile.absolutePath

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            try {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioChannels(1)
                setOutputFile(filePath)

                prepare()
                start()

                currentAudioPath = filePath
                Log.d(TAG, "录音开始: $filePath")
            } catch (e: IOException) {
                Log.e(TAG, "录音启动失败", e)
                releaseQuietly()
                throw IOException("录音启动失败: ${e.message}", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "录音状态异常", e)
                releaseQuietly()
                throw IllegalStateException("录音状态异常: ${e.message}", e)
            }
        }

        return filePath
    }

    /**
     * 停止录音。
     * @return Pair(音频文件路径, 录音时长毫秒)，若未在录音则返回 null
     */
    fun stopRecording(): Pair<String, Long>? {
        val recorder = mediaRecorder ?: return null
        val path = currentAudioPath ?: return null

        return try {
            var durationMs = 0L

            // 先取时间戳
            try {
                // 在 stop 前取 maxAmplitude 作为录音活动指示
                recorder.apply {
                    stop()
                    reset()
                }
            } catch (e: Exception) {
                Log.w(TAG, "停止录音时异常", e)
                // 释放后重新用文件长度估算时长
            }

            // 计算实际录音时长
            val audioFile = File(path)
            if (audioFile.exists() && audioFile.length() > 0) {
                // 对于 AAC/MPEG4，用文件大小估算
                // AAC 128kbps → 16 KB/s
                durationMs = (audioFile.length() * 8L / 128L)
                Log.d(TAG, "录音文件: ${audioFile.length()} bytes, 估算时长: ${durationMs}ms")
            }

            releaseQuietly()
            currentAudioPath = null

            Log.d(TAG, "录音结束: $path, ${durationMs}ms")
            Pair(path, durationMs)
        } catch (e: Exception) {
            Log.e(TAG, "停止录音异常", e)
            releaseQuietly()
            currentAudioPath = null
            null
        }
    }

    /**
     * 取消当前录音（不保存文件）。
     */
    fun cancelRecording() {
        val path = currentAudioPath
        releaseQuietly()
        currentAudioPath = null
        // 清理未完成的文件
        if (path != null) {
            try {
                val f = File(path)
                if (f.exists()) f.delete()
            } catch (_: Exception) {}
        }
    }

    /**
     * 获取当前录音状态。
     */
    fun isRecording(): Boolean = mediaRecorder != null

    /**
     * 获取录音过程中的最大振幅（可用于实时显示音量指示）。
     */
    fun getMaxAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
    }

    /**
     * 删除指定视频的所有录音文件。
     */
    fun deleteAllRecordingsForVideo(videoId: Long) {
        val dir = File(context.filesDir, "$SHADOWING_DIR/$videoId")
        if (dir.exists()) {
            dir.deleteRecursively()
            Log.d(TAG, "已删除视频 $videoId 全部录音文件")
        }
    }

    /**
     * 删除指定句子的录音文件。
     */
    fun deleteRecordingForSentence(videoId: Long, sentenceId: Long) {
        val file = File(context.filesDir, "$SHADOWING_DIR/$videoId/sentence_${sentenceId}.mp3")
        if (file.exists()) file.delete()
    }

    private fun releaseQuietly() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
    }
}
