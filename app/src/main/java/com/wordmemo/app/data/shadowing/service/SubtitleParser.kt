package com.wordmemo.app.data.shadowing.service

data class SubtitleEntry(
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

/**
 * SRT/VTT 字幕解析器
 *
 * SRT 格式:
 *   1
 *   00:00:01,000 --> 00:00:04,000
 *   Hello, how are you?
 *
 * VTT 格式:
 *   WEBVTT
 *
 *   00:00:01.000 --> 00:00:04.000
 *   Hello, how are you?
 */
class SubtitleParser {

    /**
     * 解析 SRT 格式字幕
     */
    fun parseSrt(content: String): List<SubtitleEntry> {
        val entries = mutableListOf<SubtitleEntry>()
        val blocks = content.trim().split(Regex("\\n\\s*\\n"))

        for (block in blocks) {
            val lines = block.trim().lines()
            if (lines.size < 3) continue

            val indexLine = lines[0].trim()
            val index = indexLine.toIntOrNull() ?: continue

            val timeLine = lines[1].trim()
            val (startMs, endMs) = parseSrtTimeLine(timeLine) ?: continue

            val text = lines.drop(2)
                .joinToString("\n")
                .trim()
                .replace(Regex("<[^>]+>"), "") // strip HTML tags
                .trim()

            if (text.isNotBlank()) {
                entries.add(SubtitleEntry(index, startMs, endMs, text))
            }
        }

        return entries
    }

    /**
     * 解析 VTT 格式字幕
     */
    fun parseVtt(content: String): List<SubtitleEntry> {
        val entries = mutableListOf<SubtitleEntry>()

        // Remove WEBVTT header and metadata lines
        var text = content.trim()
        if (text.startsWith("WEBVTT", ignoreCase = true)) {
            val firstNewline = text.indexOf('\n')
            if (firstNewline > 0) {
                text = text.substring(firstNewline + 1).trim()
            } else {
                text = ""
            }
        }

        // Remove style/note blocks
        text = text.replace(Regex("STYLE[\\s\\S]*?\\n\\n"), "")
        text = text.replace(Regex("NOTE[\\s\\S]*?\\n\\n"), "")

        val blocks = text.split(Regex("\\n\\s*\\n"))
        var cueIndex = 0

        for (block in blocks) {
            val lines = block.trim().lines()
            if (lines.isEmpty()) continue

            // VTT cues may have an optional cue identifier before the time line
            var timeLineIndex = -1
            for (i in lines.indices) {
                if (lines[i].contains("-->")) {
                    timeLineIndex = i
                    break
                }
            }
            if (timeLineIndex < 0) continue

            cueIndex++

            val timeLine = lines[timeLineIndex].trim()
            val (startMs, endMs) = parseVttTimeLine(timeLine) ?: continue

            val textLines = lines.drop(timeLineIndex + 1)
                .joinToString("\n")
                .trim()
                .replace(Regex("<[^>]+>"), "")
                .trim()

            if (textLines.isNotBlank()) {
                entries.add(SubtitleEntry(cueIndex, startMs, endMs, textLines))
            }
        }

        return entries
    }

    /**
     * 自动检测字幕格式并解析
     */
    fun detectAndParse(content: String, fileName: String): List<SubtitleEntry> {
        val trimmed = content.trim()

        return when {
            fileName.endsWith(".vtt", ignoreCase = true) ||
            trimmed.startsWith("WEBVTT", ignoreCase = true) -> parseVtt(trimmed)
            fileName.endsWith(".srt", ignoreCase = true) ||
            trimmed.contains(Regex("^\\d+\\s*$")) -> parseSrt(trimmed)
            else -> {
                // Fallback: try SRT first, then VTT
                val srtResult = parseSrt(trimmed)
                if (srtResult.isNotEmpty()) srtResult
                else parseVtt(trimmed)
            }
        }
    }

    /**
     * 解析 SRT 时间行: 00:00:01,000 --> 00:00:04,000
     */
    private fun parseSrtTimeLine(line: String): Pair<Long, Long>? {
        val parts = line.split("-->")
        if (parts.size != 2) return null

        val startMs = parseSrtTimestamp(parts[0].trim()) ?: return null
        val endMs = parseSrtTimestamp(parts[1].trim()) ?: return null

        return Pair(startMs, endMs)
    }

    /**
     * 解析 VTT 时间行: 00:00:01.000 --> 00:00:04.000
     */
    private fun parseVttTimeLine(line: String): Pair<Long, Long>? {
        val parts = line.split("-->")
        if (parts.size != 2) return null

        val startMs = parseVttTimestamp(parts[0].trim()) ?: return null
        val endMs = parseVttTimestamp(parts[1].trim()) ?: return null

        return Pair(startMs, endMs)
    }

    /**
     * 解析 SRT 时间戳: HH:MM:SS,mmm
     */
    private fun parseSrtTimestamp(ts: String): Long? {
        return try {
            // Handle both HH:MM:SS,mmm and HH:MM:SS.mmm
            val normalized = ts.replace('.', ',')
            val parts = normalized.split(",")
            if (parts.size != 2) return null

            val timeParts = parts[0].split(":")
            if (timeParts.size != 3) return null

            val hours = timeParts[0].toLong()
            val minutes = timeParts[1].toLong()
            val seconds = timeParts[2].toLong()
            val millis = parts[1].padEnd(3, '0').take(3).toLong()

            hours * 3600000L + minutes * 60000L + seconds * 1000L + millis
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * 解析 VTT 时间戳: HH:MM:SS.mmm
     */
    private fun parseVttTimestamp(ts: String): Long? {
        return try {
            // VTT also supports HH:MM:SS.mmm like SRT with dot
            val normalized = ts.replace(',', '.')
            val parts = normalized.split(".")
            if (parts.size != 2) return null

            val timeParts = parts[0].split(":")
            if (timeParts.size != 3) return null

            val hours = timeParts[0].toLong()
            val minutes = timeParts[1].toLong()
            val seconds = timeParts[2].toLong()
            val millis = parts[1].padEnd(3, '0').take(3).toLong()

            hours * 3600000L + minutes * 60000L + seconds * 1000L + millis
        } catch (e: NumberFormatException) {
            null
        }
    }
}
