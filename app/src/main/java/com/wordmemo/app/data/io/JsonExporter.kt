package com.wordmemo.app.data.io

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wordmemo.app.domain.model.Flashcard
import com.wordmemo.app.domain.model.ReviewLog
import com.wordmemo.app.domain.model.Word

/**
 * JSON 导出器。
 * 将用户数据导出为 JSON 格式用于备份。
 */
class JsonExporter(private val gson: Gson = Gson()) {

    data class BackupData(
        val version: Int = 1,
        val exportedAt: Long = System.currentTimeMillis(),
        val words: List<Word> = emptyList(),
        val flashcards: List<Flashcard> = emptyList(),
        val reviewLogs: List<ReviewLog> = emptyList()
    )

    fun exportToJson(
        words: List<Word>,
        flashcards: List<Flashcard>,
        reviewLogs: List<ReviewLog>
    ): String {
        val data = BackupData(
            words = words,
            flashcards = flashcards,
            reviewLogs = reviewLogs
        )
        return gson.toJson(data)
    }

    fun parseBackup(json: String): BackupData? {
        return try {
            gson.fromJson(json, BackupData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 导出为 CSV 格式（可用 Excel/Google Sheets 打开）。
     */
    fun exportToCsv(words: List<Word>): String {
        val sb = StringBuilder()
        sb.appendLine("英文,中文,备注,添加时间")
        for (word in words) {
            val escapedEnglish = "\"${word.english.replace("\"", "\"\"")}\""
            val escapedChinese = "\"${word.chinese.replace("\"", "\"\"")}\""
            val escapedNote = "\"${(word.note ?: "").replace("\"", "\"\"")}\""
            sb.appendLine("$escapedEnglish,$escapedChinese,$escapedNote,${word.createdAt}")
        }
        return sb.toString()
    }
}
