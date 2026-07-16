package com.wordmemo.app.data.network

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.wordmemo.app.data.local.WordMemoDatabase

/**
 * 用 AI API 动态生成单词关系图谱。
 * 任何词都能出真实语义关联，不依赖预定义数据。
 */
class AiGraphGenerator(private val gson: Gson = Gson()) {

    data class AiRelation(
        val word: String,
        val chinese: String,
        val type: String,
        val children: List<AiRelation> = emptyList()
    )

    data class AiGraphResult(
        val center: String,
        val relations: List<AiRelation>
    )

    /** 从数据库读取 API 配置 */
    suspend fun loadApiConfig(db: WordMemoDatabase): Triple<String, String, String>? {
        try {
            val configs = db.appConfigDao().getAll()
            val cipher = com.wordmemo.app.data.encryption.ApiKeyCipher()
            val apiKey = configs.firstOrNull { it.key == "api_key" }?.let {
                cipher.decrypt(it.value)
            } ?: return null
            val baseUrl = configs.firstOrNull { it.key == "api_base_url" }?.value
                ?: "https://api.deepseek.com"
            val model = configs.firstOrNull { it.key == "api_model" }?.value
                ?: "deepseek-chat"
            if (apiKey.isBlank()) return null
            return Triple(apiKey, baseUrl, model)
        } catch (_: Exception) { return null }
    }

    /** 调用 AI 生成关系，返回 JSON 字符串 */
    suspend fun generateRelations(
        centerWord: String,
        apiKey: String,
        baseUrl: String,
        model: String
    ): String? {
        val prompt = """你是一位英语词汇专家。请为单词 "$centerWord" 生成 7 个直接相关的 L1 关联词。

要求：
- 每个 L1 词必须是与中心词有真实语义关联的**真实英语单词**（同义词、反义词、常用搭配、形近词、相关概念）
- 每个 L1 词附带 2 个 L2 子关联词（与 L1 有直接关系）
- 所有词必须附带中文释义
- 关系类型：synonym（同义词）、antonym（反义词）、collocation（搭配）、similar（形近词）、concept（相关概念）

输出格式（仅 JSON，不要其他文字）：
{
  "center": "$centerWord",
  "relations": [
    {
      "word": "l1_word_1",
      "chinese": "中文释义",
      "type": "synonym",
      "children": [
        {"word": "l2_word_1a", "chinese": "中文释义", "type": "similar"},
        {"word": "l2_word_1b", "chinese": "中文释义", "type": "antonym"}
      ]
    }
  ]
}"""

        val client = AiApiClient(gson)
        val raw = client.chatCompletion(baseUrl, apiKey, model, prompt, "请生成")
        return raw?.let { extractJson(it) }
    }

    /** 从 AI 回复中提取 JSON（处理被 markdown 包裹的情况） */
    private fun extractJson(text: String): String? {
        // 尝试提取 ```json ... ``` 包裹的内容
        val codeBlock = Regex("""```(?:json)?\s*([\s\S]*?)```""").find(text)
        if (codeBlock != null) return codeBlock.groupValues[1].trim()
        // 直接解析
        return try {
            JsonParser.parseString(text.trim())
            text.trim()
        } catch (_: Exception) {
            // 找第一个 { 到最后一个 }
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start >= 0 && end > start) text.substring(start, end + 1) else null
        }
    }

    /** 解析 AI 返回的 JSON 为图谱数据 */
    fun parseResult(json: String): AiGraphResult? {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val center = root.get("center")?.asString ?: return null
            val relations = root.getAsJsonArray("relations")?.map { el ->
                val obj = el.asJsonObject
                val word = obj.get("word")?.asString ?: return@map null
                val chinese = obj.get("chinese")?.asString ?: ""
                val type = obj.get("type")?.asString ?: ""
                val children = obj.getAsJsonArray("children")?.mapNotNull { c ->
                    val co = c.asJsonObject
                    val cw = co.get("word")?.asString ?: return@mapNotNull null
                    AiRelation(cw, co.get("chinese")?.asString ?: "", co.get("type")?.asString ?: "")
                } ?: emptyList()
                AiRelation(word, chinese, type, children)
            }?.filterNotNull() ?: emptyList()

            if (relations.size < 3) return null // 太少了说明 AI 没理解好
            AiGraphResult(center, relations)
        } catch (_: Exception) { null }
    }
}
