package com.wordmemo.app.data.epub

/**
 * 句子切分引擎。
 *
 * 功能：将段落文本按句分割。
 * 策略：正则匹配句尾标点 + 缩写白名单防误切。
 *
 * 缩写白名单覆盖：
 * - 英语常用尊称缩写（Mr., Mrs., Dr., etc.）
 * - 学术/技术缩写（e.g., i.e., et al., vs.）
 * - 组织/地理缩写（U.S., U.K.）
 *
 * 句点规则：句点后跟空格+大写字母/数字/引号 → 切句。
 * 缩写保护：先用占位符替换缩写中的句点，分割后再还原。
 */
class SentenceSplitter {

    companion object {
        // 常见英语尊称缩写（匹配时自动加 . 后缀）
        private val HONORIFICS = setOf(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "rev", "hon",
            "gov", "pres", "dept", "col", "gen", "lt", "capt", "sgt", "maj",
            "cmdr", "adm", "fr", "br", "bl", "sir", "madam", "mx"
        )

        // 常见拉丁/学术缩写
        private val LATIN_ABBR = setOf(
            "e.g", "i.e", "etc", "et al", "vs", "viz", "cf", "ca",
            "circa", "q.v", "n.b", "ibid", "op cit", "loc cit",
            "p.s", "a.m", "p.m", "vol", "sec",
            "fig", "ed", "eds", "trans", "est", "approx",
            "inc", "ltd", "co", "corp", "plc", "llc", "assn", "bros"
        )

        // 组织/地理缩写
        private val ORG_ABBR = setOf(
            "u.s", "u.k", "u.n", "e.u",
            "b.c", "a.d", "b.c.e", "c.e",
            "d.c", "n.y", "l.a", "s.f", "n.j", "n.c", "d.f"
        )

        // 生成所有缩写匹配模式 —— 匹配 "缩写." 后跟空白或标点
        private val ABBR_PATTERNS: List<Regex> by lazy {
            val all = mutableListOf<String>()
            // 无点缩写：加 . 匹配
            for (w in HONORIFICS) all.add("$w\\.")
            // 带点缩写：直接加 .
            for (w in LATIN_ABBR) all.add("${w}\\.")
            for (w in ORG_ABBR) all.add("${w}\\.")
            all.map { Regex("""\b${it}(?=\s|[,;:)!?\-])""", RegexOption.IGNORE_CASE) }
        }

        // 占位符
        private const val DOT_PLACEHOLDER = '\ue000'

        // 句子边界：句尾标点后跟空格 + 大写字母/数字
        private val SENTENCE_BOUNDARY = Regex("""(?<=[.!?])\s+(?=[\p{Lu}0-9\"\'(])""")
    }

    /**
     * 将文本按句分割。
     */
    fun split(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        // 1. 统一换行/空白
        val normalized = text
            .replace(Regex("""\r\n|\r|\n"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (normalized.isBlank()) return emptyList()

        // 2. 保护缩写句点
        val protected = protectAbbreviations(normalized)

        // 3. 按句边界分割
        val parts = SENTENCE_BOUNDARY
            .split(protected)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // 4. 还原缩写
        return parts.map { restoreAbbreviations(it) }
    }

    /**
     * 缩写保护：将缩写句点替换为占位符。
     */
    private fun protectAbbreviations(text: String): String {
        var result = text
        for (pattern in ABBR_PATTERNS) {
            result = pattern.replace(result) { match ->
                match.value.replace('.', DOT_PLACEHOLDER)
            }
        }
        return result
    }

    /**
     * 还原占位符为句点。
     */
    private fun restoreAbbreviations(text: String): String {
        return text.replace(DOT_PLACEHOLDER, '.')
    }

    /**
     * 判断句子是否为有效句子。
     */
    fun isValidSentence(sentence: String): Boolean {
        val trimmed = sentence.trim()
        if (trimmed.length < 2) return false
        // 纯数字/符号不算有效句子
        if (trimmed.all { it.isDigit() || it.isWhitespace() || it == '.' || it == ',' || it == '-' }) return false
        return true
    }

    /**
     * 完整分割，自动过滤无效项。
     */
    fun splitChapter(text: String): List<String> {
        return split(text).filter { isValidSentence(it) }
    }

    /**
     * 分割为带序号的句子。
     */
    fun splitWithIndex(text: String): List<IndexedSentence> {
        return splitChapter(text).mapIndexed { index, sentence ->
            IndexedSentence(index = index, text = sentence)
        }
    }

    data class IndexedSentence(
        val index: Int,
        val text: String
    )
}
