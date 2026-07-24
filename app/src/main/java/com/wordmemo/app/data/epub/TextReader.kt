package com.wordmemo.app.data.epub

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * 纯文本文件（.txt）解析器。
 *
 * 解析策略：
 * 1. 读取纯文本文件
 * 2. 按 "Actus primus" / "Actus Secundus" / "Actus Tertius" 等章节标记分割
 * 3. 若无章节标记则按空行段落分割
 * 4. 返回 Book 对象（与 EpubReader 兼容）
 *
 * 支持从 URI 或 assets 读取。
 */
class TextReader {

    companion object {
        // 章节标题匹配模式（拉丁语或英语）
        private val CHAPTER_PATTERNS = listOf(
            Regex("""^Actus\s+\w+\.?\s*$""", RegexOption.IGNORE_CASE),
            Regex("""^ACT\s+[IVXLCDM]+\.""", RegexOption.IGNORE_CASE),
            Regex("""^Scene\s+\d+""", RegexOption.IGNORE_CASE),
            Regex("""^Chapter\s+\d+""", RegexOption.IGNORE_CASE),
        )

        // 停止词 — 在这些行之后停止读取（正文开始前 / 结束后）
        private val START_MARKERS = listOf(
            "*** START OF",
            "*** START OF THE PROJECT GUTENBERG EBOOK"
        )
        private val END_MARKERS = listOf(
            "*** END OF",
            "*** END OF THE PROJECT GUTENBERG EBOOK"
        )

        // 戏剧角色列表标记
        private val DRAMATIS_PERSONAE = listOf(
            "DRAMATIS PERSONAE",
            "Dramatis Personae",
            "CHARACTERS"
        )
    }

    /**
     * 从 URI 解析 .txt 文件。
     */
    fun parse(context: Context, uri: Uri): Book {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法打开文件: $uri")

        val lines = inputStream.bufferedReader().use { it.readLines() }
        return parseLines(lines, uri.lastPathSegment ?: "未知文件")
    }

    /**
     * 从 assets 目录解析 .txt 文件。
     */
    fun parseFromAssets(context: Context, assetPath: String): Book {
        val inputStream = context.assets.open(assetPath)
        val lines = inputStream.bufferedReader().use { it.readLines() }
        return parseLines(lines, assetPath)
    }

    /**
     * 从文件解析 .txt 文件。
     */
    fun parseFromFile(file: File): Book {
        val lines = file.readLines()
        val title = file.nameWithoutExtension
        return parseLines(lines, title)
    }

    /**
     * 解析文本行。
     *
     * @param lines 文本行
     * @param sourceName 源文件名称（用于生成书名）
     * @return Book 对象
     */
    private fun parseLines(lines: List<String>, sourceName: String): Book {
        // 1. 去除 BOM 头
        val cleaned = lines.map { it.trimStart('\uFEFF') }

        // 2. 找到正文起始和结束标记
        val startIdx = findStartIndex(cleaned)
        val endIdx = findEndIndex(cleaned)

        val bodyLines = if (startIdx >= 0 && endIdx > startIdx) {
            cleaned.subList(startIdx + 1, endIdx)
        } else if (startIdx >= 0) {
            cleaned.subList(startIdx + 1, cleaned.size)
        } else {
            cleaned
        }

        // 3. 提取书名和作者
        val title = extractTitle(cleaned, sourceName)
        val author = extractAuthor(cleaned)

        // 4. 按章节分割
        val chapters = splitChapters(bodyLines)

        return Book(
            title = title,
            author = author,
            chapters = chapters
        )
    }

    /**
     * 查找正文起始位置（第一个 START 标记后）。
     */
    private fun findStartIndex(lines: List<String>): Int {
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            for (marker in START_MARKERS) {
                if (trimmed.startsWith(marker, ignoreCase = true)) {
                    return index
                }
            }
        }
        return -1
    }

    /**
     * 查找正文结束位置（第一个 END 标记）。
     */
    private fun findEndIndex(lines: List<String>): Int {
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            for (marker in END_MARKERS) {
                if (trimmed.startsWith(marker, ignoreCase = true)) {
                    return index
                }
            }
        }
        return lines.size
    }

    /**
     * 从文件头部提取标题。
     */
    private fun extractTitle(lines: List<String>, fallback: String): String {
        // 找 "Title:" 行
        for (line in lines.take(20)) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Title:", ignoreCase = true)) {
                return trimmed.removePrefix("Title:").trim().removePrefix(":").trim()
                    .removeSurrounding("\"").trim()
            }
        }
        return fallback.removeSuffix(".txt").removeSuffix(".TXT").trim()
    }

    /**
     * 从文件头部提取作者。
     */
    private fun extractAuthor(lines: List<String>): String {
        for (line in lines.take(20)) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Author:", ignoreCase = true)) {
                return trimmed.removePrefix("Author:").trim().removePrefix(":").trim()
            }
        }
        return "Unknown"
    }

    /**
     * 按章节标记分割文本。
     */
    private fun splitChapters(lines: List<String>): List<Chapter> {
        // 扫描所有章节边界
        val chapterBoundaries = mutableListOf<Pair<Int, String>>() // (lineIndex, title)

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            // 检查是否是章节标题
            for (pattern in CHAPTER_PATTERNS) {
                if (pattern.matches(trimmed)) {
                    chapterBoundaries.add(index to trimmed)
                    break
                }
            }

            // 也检查戏剧角色列表
            if (trimmed in DRAMATIS_PERSONAE) {
                chapterBoundaries.add(index to trimmed)
            }
        }

        // 如果没有找到章节标记，按空行段落分割
        if (chapterBoundaries.isEmpty()) {
            return splitByParagraphs(lines)
        }

        val chapters = mutableListOf<Chapter>()
        for (i in chapterBoundaries.indices) {
            val (startLine, title) = chapterBoundaries[i]
            val endLine = if (i + 1 < chapterBoundaries.size) {
                chapterBoundaries[i + 1].first
            } else {
                lines.size
            }

            // 提取章节文本（从下一行开始到下一个章节前）
            val chapterText = lines.subList(startLine + 1, endLine)
                .joinToString("\n")
                .trim()

            if (chapterText.isNotBlank()) {
                chapters.add(
                    Chapter(
                        index = i,
                        title = title,
                        rawHtml = "",  // .txt 没有 HTML
                        text = chapterText,
                        filePath = "text_$i"
                    )
                )
            }
        }

        return chapters
    }

    /**
     * 按空行段落分割文本。
     */
    private fun splitByParagraphs(lines: List<String>): List<Chapter> {
        val paragraphs = mutableListOf<String>()
        val currentParagraph = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) {
                if (currentParagraph.isNotEmpty()) {
                    paragraphs.add(currentParagraph.toString().trim())
                    currentParagraph.clear()
                }
            } else {
                if (currentParagraph.isNotEmpty()) currentParagraph.append(' ')
                currentParagraph.append(trimmed)
            }
        }
        if (currentParagraph.isNotEmpty()) {
            paragraphs.add(currentParagraph.toString().trim())
        }

        return paragraphs.mapIndexed { index, text ->
            Chapter(
                index = index,
                title = "段落 ${index + 1}",
                rawHtml = "",
                text = text,
                filePath = "text_$index"
            )
        }
    }
}
