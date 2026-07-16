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
- 每个 L1 词必须是与中心词有**真实可解释**的语义关系
- 关系必须具体说明，不能泛泛而谈
- 每个 L1 附带 2 个 L2 子关联词（与 L1 有直接关系）
- 所有词必须附带中文释义
- 关系类型：synonym（同义词）、antonym（反义词）、collocation（搭配）、similar（形近词）、concept（相关概念）

示例——中心词 "abandon"：
[
  {"word":"desert","chinese":"抛弃","type":"synonym","children":[
    {"word":"forsake","chinese":"遗弃","type":"synonym"},
    {"word":"retain","chinese":"保留","type":"antonym"}]},
  {"word":"keep","chinese":"保留","type":"antonym","children":[
    {"word":"maintain","chinese":"维持","type":"synonym"},
    {"word":"discard","chinese":"丢弃","type":"antonym"}]},
  {"word":"give up","chinese":"放弃","type":"collocation","children":[
    {"word":"surrender","chinese":"投降","type":"synonym"},
    {"word":"persist","chinese":"坚持","type":"antonym"}]},
  {"word":"abolish","chinese":"废除","type":"similar","children":[
    {"word":"eliminate","chinese":"消除","type":"synonym"},
    {"word":"establish","chinese":"建立","type":"antonym"}]},
  {"word":"leave","chinese":"离开","type":"concept","children":[
    {"word":"depart","chinese":"出发","type":"synonym"},
    {"word":"arrive","chinese":"到达","type":"antonym"}]},
  {"word":"quit","chinese":"退出","type":"synonym","children":[
    {"word":"resign","chinese":"辞职","type":"synonym"},
    {"word":"continue","chinese":"继续","type":"antonym"}]},
  {"word":"adopt","chinese":"采纳","type":"antonym","children":[
    {"word":"embrace","chinese":"拥抱","type":"synonym"},
    {"word":"reject","chinese":"拒绝","type":"antonym"}]}
]

请按相同格式为 "$centerWord" 生成 7 组关系。
输出仅 JSON 数组，不要其他文字："""

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

    /** 解析 AI 返回的 JSON 为图谱数据（新版：纯数组格式） */
    fun parseResult(json: String): AiGraphResult? {
        return try {
            val arr = JsonParser.parseString(json).asJsonArray
            val relations = arr.mapNotNull { el ->
                val obj = el.asJsonObject
                val word = obj.get("word")?.asString ?: return@mapNotNull null
                val chinese = obj.get("chinese")?.asString ?: ""
                val type = obj.get("type")?.asString ?: ""
                val children = obj.getAsJsonArray("children")?.mapNotNull { c ->
                    val co = c.asJsonObject
                    AiRelation(
                        co.get("word")?.asString ?: return@mapNotNull null,
                        co.get("chinese")?.asString ?: "",
                        co.get("type")?.asString ?: ""
                    )
                } ?: emptyList()
                AiRelation(word, chinese, type, children)
            }
            if (relations.size < 3) return null
            AiGraphResult(centerWordForRelations, relations)
        } catch (_: Exception) { null }
    }

    private var centerWordForRelations: String = ""
    fun setCenterWord(word: String) { centerWordForRelations = word }
}
