package com.wordmemo.app.data.network

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.wordmemo.app.domain.model.AiMnemonic
import com.wordmemo.app.domain.model.AiRelation
import com.wordmemo.app.domain.model.AiTranslation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI API 响应解析器。
 * 将 LLM 返回的 JSON 解析为域模型。
 * 自动清理 markdown 代码围栏。
 */
@Singleton
class AiApiResponseParser @Inject constructor(
    private val gson: Gson
) {
    data class TranslationResponse(
        val translation: String = "",
        val phonetic: String? = null,
        val examples: List<String> = emptyList(),
        val usage: String? = null
    )
    data class MnemonicItem(val method: String = "", val content: String = "")
    data class RelationItem(val word: String = "", val type: String = "", val definition: String? = null)

    /** 从 OpenAI 兼容 API 响应中提取 choices[0].message.content */
    private fun extractAiContent(text: String): String? {
        return try {
            val root = com.google.gson.JsonParser.parseString(text).asJsonObject
            val choices = root.getAsJsonArray("choices") ?: return text
            val msg = choices.first()?.asJsonObject?.get("message")?.asJsonObject ?: return text
            msg.get("content")?.asString ?: text
        } catch (_: Exception) { text }
    }

    /** 从文本中提取并修复 JSON 数组 */
    private fun extractJsonArray(text: String): String? {
        val trimmed = text.trim()
        // 尝试从 ```json 代码块提取
        val codeBlock = Regex("""```(?:json)?\s*\n?([\s\S]*?)\n?```""").find(trimmed)
        if (codeBlock != null) {
            val content = codeBlock.groupValues[1].trim()
            if (content.startsWith("[") || content.startsWith("{")) return content
        }
        // 找 [ 到 ]
        val startB = trimmed.indexOf('[')
        val endB = trimmed.lastIndexOf(']')
        if (startB >= 0 && endB > startB) {
            val candidate = trimmed.substring(startB, endB + 1)
            if (isValidJson(candidate)) return candidate
            val fixed = fixBrokenJson(candidate)
            if (fixed != null) return fixed
            val firstObj = candidate.indexOf('{')
            if (firstObj > 0) return trimmed.substring(startB + firstObj, endB + 1)
        }
        // 找 { 到 }
        val startO = trimmed.indexOf('{')
        val endO = trimmed.lastIndexOf('}')
        if (startO >= 0 && endO > startO) return trimmed.substring(startO, endO + 1)
        return null
    }

    private fun isValidJson(text: String): Boolean = try {
        com.google.gson.JsonParser.parseString(text); true
    } catch (_: Exception) { false }

    /** 修复以 [ 开头但紧接着 } 的残缺 JSON */
    private fun fixBrokenJson(text: String): String? {
        var cleaned = text.trim()
        if (cleaned.startsWith("[")) {
            val firstObj = cleaned.indexOf('{')
            if (firstObj > 0) cleaned = "[" + cleaned.substring(firstObj)
            val lastObj = cleaned.lastIndexOf('}')
            if (lastObj > 0) {
                val afterClose = cleaned.substring(lastObj + 1)
                if (afterClose.contains("]")) cleaned = cleaned.substring(0, lastObj + 1) + "]"
            }
            if (isValidJson(cleaned)) return cleaned
        }
        return null
    }

    fun parseTranslation(json: String?, word: String): AiTranslation {
        if (json == null) return AiTranslation(word = word, translation = "翻译不可用")
        val content = extractAiContent(json) ?: return AiTranslation(word = word, translation = "解析失败")
        // 翻译返回 JSON 对象，直接提取 {} 内容
        val cleaned = try {
            val start = content.indexOf('{')
            val end = content.lastIndexOf('}')
            if (start >= 0 && end > start) content.substring(start, end + 1) else content
        } catch (_: Exception) { content }
        return try {
            val parsed = gson.fromJson(cleaned, TranslationResponse::class.java)
            AiTranslation(
                word = word,
                translation = parsed.translation,
                phonetic = parsed.phonetic ?: "",
                examples = parsed.examples,
                usage = parsed.usage,
                source = "AI 翻译"
            )
        } catch (e: Exception) {
            AiTranslation(word = word, translation = "解析失败")
        }
    }

    fun parseMnemonics(json: String?, wordId: Long): List<AiMnemonic> {
        if (json == null) return emptyList()
        val content = extractAiContent(json) ?: return emptyList()
        val cleaned = extractJsonArray(content) ?: content
        return try {
            val items: List<MnemonicItem> = gson.fromJson(cleaned,
                object : com.google.gson.reflect.TypeToken<List<MnemonicItem>>() {}.type)
            if (items.isNotEmpty()) items.map { AiMnemonic(wordId = wordId, method = it.method, content = it.content) }
            else emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun parseRelations(json: String?, wordId: Long): List<AiRelation> {
        if (json == null) return emptyList()
        val content = extractAiContent(json) ?: return emptyList()
        val cleaned = extractJsonArray(content) ?: content
        return try {
            val items: List<RelationItem> = gson.fromJson(cleaned,
                object : com.google.gson.reflect.TypeToken<List<RelationItem>>() {}.type)
            if (items.isNotEmpty()) items.map { AiRelation(wordId = wordId, word = it.word, type = it.type, definition = it.definition) }
            else emptyList()
        } catch (_: Exception) { emptyList() }
    }
}
