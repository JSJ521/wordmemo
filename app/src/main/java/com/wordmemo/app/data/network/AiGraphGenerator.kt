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
        val prompt = """请扮演语言学专家，以单词 "$centerWord" 为中心构建两层关系网状图。

【核心要求】
- L0：中心词 "$centerWord"（1个）
- L1：从L0直接扩展出 **7个** 关系最紧密的英文单词或短语。
  关系类型包括：同义词(synonym)、反义词(antonym)、常用搭配(collocation)、上下义词(concept)、派生词(similar)、常见语境关联词
- L2：每个L1再扩展出 **2个** L2关联词（共7×2=14个）。
  L2必须与其父级L1有直接语义关联，尽量不与L0或其他节点重复

【质量要求】
- 每个词必须是**真实英语单词/短语**，且为常见实用词汇
- 每条关系必须有明确的语义依据，不能硬凑
- 如果某个方向难以扩展，可适当放宽关系类型，但必须保证语义合理

【输出格式】
必须输出 JSON 数组（不要其他文字），每项包含 word、chinese、type、children：
[
  {
    "word": "l1_word",
    "chinese": "中文释义（含关系说明）",
    "type": "synonym/antonym/collocation/similar/concept",
    "children": [
      {"word": "l2_word_a", "chinese": "中文释义", "type": "关系类型"},
      {"word": "l2_word_b", "chinese": "中文释义", "type": "关系类型"}
    ]
  }
]

示例——中心词 "abandon":
[
  {"word":"desert","chinese":"抛弃（放弃某人/某事）","type":"synonym","children":[
    {"word":"forsake","chinese":"遗弃","type":"synonym"},
    {"word":"retain","chinese":"保留","type":"antonym"}]},
  {"word":"keep","chinese":"保留（与放弃相反）","type":"antonym","children":[
    {"word":"maintain","chinese":"维持","type":"synonym"},
    {"word":"discard","chinese":"丢弃","type":"antonym"}]},
  {"word":"give up","chinese":"放弃尝试/努力","type":"synonym","children":[
    {"word":"surrender","chinese":"投降","type":"synonym"},
    {"word":"persist","chinese":"坚持","type":"antonym"}]},
  {"word":"abolish","chinese":"废除（制度/法律）","type":"similar","children":[
    {"word":"eliminate","chinese":"消除","type":"synonym"},
    {"word":"establish","chinese":"建立","type":"antonym"}]},
  {"word":"leave","chinese":"离开（某个地方/人）","type":"concept","children":[
    {"word":"depart","chinese":"出发","type":"synonym"},
    {"word":"arrive","chinese":"到达","type":"antonym"}]},
  {"word":"quit","chinese":"退出（组织/习惯）","type":"synonym","children":[
    {"word":"resign","chinese":"辞职","type":"synonym"},
    {"word":"continue","chinese":"继续","type":"antonym"}]},
  {"word":"adopt","chinese":"采纳（与拒绝相对）","type":"antonym","children":[
    {"word":"embrace","chinese":"拥抱/接受","type":"synonym"},
    {"word":"reject","chinese":"拒绝","type":"antonym"}]}
]

请按上述格式为 "$centerWord" 生成 7 组关系。只输出 JSON 数组，不要额外文字。"""

        val client = AiApiClient(gson)
        val raw = client.chatCompletion(baseUrl, apiKey, model, prompt, "请生成")
        val content = raw?.let { extractAiContent(it) }
        return content?.let { extractJsonArray(it) }
    }

    /** 从 AI 回复中提取 content（处理 OpenAI API 包裹格式） */
    private fun extractAiContent(text: String): String? {
        return try {
            val root = JsonParser.parseString(text).asJsonObject
            val choices = root.getAsJsonArray("choices") ?: return text
            val msg = choices.first()?.asJsonObject?.get("message")?.asJsonObject ?: return text
            msg.get("content")?.asString ?: text
        } catch (_: Exception) { text }
    }

    /** 从文本中提取 JSON 数组 */
    private fun extractJsonArray(text: String): String? {
        val trimmed = text.trim()
        val codeBlock = Regex("""```(?:json)?\s*\n?([\s\S]*?)\n?```""").find(trimmed)
        if (codeBlock != null) {
            val content = codeBlock.groupValues[1].trim()
            if (content.startsWith("[") || content.startsWith("{")) return content
        }
        if (trimmed.startsWith("[")) return trimmed
        if (trimmed.startsWith("{")) return trimmed
        val start = trimmed.indexOf('[')
        val end = trimmed.lastIndexOf(']')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
        val startB = trimmed.indexOf('{')
        val endB = trimmed.lastIndexOf('}')
        if (startB >= 0 && endB > startB) return trimmed.substring(startB, endB + 1)
        return null
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
