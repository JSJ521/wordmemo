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

    /** 清理 LLM 输出中的 markdown 代码围栏和多余前缀 */
    private fun cleanJson(raw: String): String {
        var s = raw.trim()
        // 去掉 ```json ... ``` 围栏
        if (s.startsWith("```")) {
            s = s.removePrefix("```").removePrefix("json").removePrefix("JSON").trim()
            val end = s.lastIndexOf("```")
            if (end >= 0) s = s.substring(0, end).trim()
        }
        // 去掉首尾多余的空白和不可见字符
        return s.trim()
    }

    fun parseTranslation(json: String?, word: String): AiTranslation {
        if (json == null) return AiTranslation(word = word, translation = "翻译不可用")
        val cleaned = cleanJson(json)
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
            // 尝试从流式响应（完整 API wrapper）中提取 content
            try {
                val obj = JsonParser.parseString(json).asJsonObject
                val choices = obj.getAsJsonArray("choices")
                if (choices != null && choices.size() > 0) {
                    val msg = choices[0].asJsonObject.getAsJsonObject("message")
                    val content = msg?.get("content")?.asString ?: ""
                    return parseTranslation(content, word)
                }
            } catch (_: Exception) {}
            AiTranslation(word = word, translation = "解析失败")
        }
    }

    fun parseMnemonics(json: String?, wordId: Long): List<AiMnemonic> {
        if (json == null) return emptyList()
        val cleaned = cleanJson(json)
        // 直接解析：LLM 返回纯 JSON 数组
        try {
            val items: List<MnemonicItem> = gson.fromJson(cleaned,
                object : com.google.gson.reflect.TypeToken<List<MnemonicItem>>() {}.type)
            if (items.isNotEmpty()) return items.map { AiMnemonic(wordId = wordId, method = it.method, content = it.content) }
        } catch (_: Exception) {}
        // 回退：从 OpenAI 格式 API 响应中提取 choices[0].message.content
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            val choices = obj.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val msg = choices[0].asJsonObject.getAsJsonObject("message")
                val content = msg?.get("content")?.asString ?: ""
                parseMnemonics(content, wordId)
            } else emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun parseRelations(json: String?, wordId: Long): List<AiRelation> {
        if (json == null) return emptyList()
        val cleaned = cleanJson(json)
        try {
            val items: List<RelationItem> = gson.fromJson(cleaned,
                object : com.google.gson.reflect.TypeToken<List<RelationItem>>() {}.type)
            if (items.isNotEmpty()) return items.map { AiRelation(wordId = wordId, word = it.word, type = it.type, definition = it.definition) }
        } catch (_: Exception) {}
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            val choices = obj.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val msg = choices[0].asJsonObject.getAsJsonObject("message")
                val content = msg?.get("content")?.asString ?: ""
                parseRelations(content, wordId)
            } else emptyList()
        } catch (_: Exception) { emptyList() }
    }
}
