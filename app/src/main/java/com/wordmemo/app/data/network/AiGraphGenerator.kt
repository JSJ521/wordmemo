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
        val prompt = """请扮演一位语言学专家，根据以下要求帮我构建一个英文单词关系网状图。

【中心词】
请以单词 "$centerWord" 作为核心节点（L0）。

【层级结构】
- L0：中心词 "$centerWord"（1个）。
- L1：从L0直接扩展出 7 个关系最紧密的英文单词或短语。这些关系可以是同义词(synonym)、反义词(antonym)、常用搭配(collocation)、上下义词(concept)、派生词(similar)或常见语境关联词。请确保这7个词与L0有强语义连接。
- L2：针对L1层中的每一个词，再分别向外扩展出 2 个关系词（共 7×2 = 14 个）。这些L2词应与其对应的L1词有直接关联，且尽量不与L0、其他L1或L2重复（如果不可避免，可适当允许，但优先选择新词）。

【输出格式】
必须输出 JSON 数组，每项包含 word、chinese（含关系说明）、type、children：
[
  {
    "word": "l1_word",
    "chinese": "中文释义（说明与L0的关系，如同义/反义/搭配等）",
    "type": "synonym/antonym/collocation/concept/similar",
    "children": [
      {"word": "l2_word_a", "chinese": "中文释义（说明与父级L1的关系）", "type": "关系类型"},
      {"word": "l2_word_b", "chinese": "中文释义（说明与父级L1的关系）", "type": "关系类型"}
    ]
  }
]

示例——中心词 "abandon":
[
  {"word":"desert","chinese":"抛弃（abandon的同义词，指离开某人或某地）","type":"synonym","children":[
    {"word":"forsake","chinese":"遗弃（desert的同义词，更强调永远放弃）","type":"synonym"},
    {"word":"retain","chinese":"保留（desert的反义词）","type":"antonym"}]},
  {"word":"keep","chinese":"保留（abandon的反义词）","type":"antonym","children":[
    {"word":"maintain","chinese":"维持（keep的同义词）","type":"synonym"},
    {"word":"discard","chinese":"丢弃（keep的反义词）","type":"antonym"}]},
  {"word":"give up","chinese":"放弃（abandon的同义短语，指放弃尝试）","type":"synonym","children":[
    {"word":"surrender","chinese":"投降（give up的同义词）","type":"synonym"},
    {"word":"persist","chinese":"坚持（give up的反义词）","type":"antonym"}]},
  {"word":"abolish","chinese":"废除（与abandon相似的「放弃」概念，多指制度/法律）","type":"similar","children":[
    {"word":"eliminate","chinese":"消除（abolish的同义词）","type":"synonym"},
    {"word":"establish","chinese":"建立（abolish的反义词）","type":"antonym"}]},
  {"word":"leave","chinese":"离开（abandon的相关概念，指离开某地/某人）","type":"concept","children":[
    {"word":"depart","chinese":"出发（leave的同义词）","type":"synonym"},
    {"word":"arrive","chinese":"到达（leave的反义词）","type":"antonym"}]},
  {"word":"quit","chinese":"退出（abandon的同义词，指退出组织/习惯）","type":"synonym","children":[
    {"word":"resign","chinese":"辞职（quit的同义词，正式场合）","type":"synonym"},
    {"word":"continue","chinese":"继续（quit的反义词）","type":"antonym"}]},
  {"word":"adopt","chinese":"采纳（abandon的反义概念，指接受而非放弃）","type":"antonym","children":[
    {"word":"embrace","chinese":"拥抱/接受（adopt的同义词）","type":"synonym"},
    {"word":"reject","chinese":"拒绝（adopt的反义词）","type":"antonym"}]}
]

【额外要求】
- 所选单词必须为英文，且为常见或实用词汇。
- 如果某个方向难以扩展，可以适当放宽关系类型，但必须保证每条关系都有明确的语义依据。
- 最终输出请只包含 JSON 数组，不要额外添加无关说明。

请开始为 "$centerWord" 构建。"""

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
