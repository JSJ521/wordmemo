package com.wordmemo.app.data.io

import com.wordmemo.app.domain.model.ImportResult
import com.wordmemo.app.domain.model.Word

/**
 * CSV 导入器。
 * 支持格式：英文,中文（每行）或英文（中文由本地词典自动填充）。
 */
class CsvImporter {
    fun parseCsv(content: String): List<Word> {
        val words = mutableListOf<Word>()
        val now = System.currentTimeMillis()

        content.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("//") }
            .forEach { line ->
                val parts = line.split(",", "\t")
                val english = parts[0].trim()
                val chinese = if (parts.size > 1) parts[1].trim() else "[待补充]"
                if (english.isNotBlank()) {
                    words.add(Word(english = english, chinese = chinese, createdAt = now, updatedAt = now))
                }
            }

        return words
    }

    fun parseTxt(content: String): List<Word> {
        val words = mutableListOf<Word>()
        val now = System.currentTimeMillis()

        content.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("//") }
            .forEach { line ->
                words.add(Word(english = line, chinese = "[待补充]", createdAt = now, updatedAt = now))
            }

        return words
    }
}
