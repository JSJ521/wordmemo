package com.wordmemo.app.data.network

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.wordmemo.app.data.local.WordMemoDatabase
import com.wordmemo.app.data.local.entity.FlashcardEntity
import com.wordmemo.app.data.network.AiApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI 行业词汇生成器。
 * 专为海外光伏储能 EPC 项目场景设计。
 */
class AiWordGenerator(private val gson: Gson = Gson()) {

    data class GeneratedWord(
        val english: String,
        val chinese: String,
        val phonetic: String,
        val example: String,
        val exampleChinese: String,
        val collocations: String
    )

    data class AiConfig(
        val apiKey: String,
        val baseUrl: String,
        val model: String
    )

    /** 从数据库读取 API 配置 */
    suspend fun loadApiConfig(db: WordMemoDatabase): AiConfig? = withContext(Dispatchers.IO) {
        try {
            val configs = db.appConfigDao().getAll()
            val cipher = com.wordmemo.app.data.encryption.ApiKeyCipher()
            var apiKey = configs.firstOrNull { it.key == "api_key" }?.let {
                cipher.decrypt(it.value)
            } ?: ""

            // 如果 Key 缺失，自动恢复（加密失败时降级为明文）
            if (apiKey.isBlank()) {
                try {
                    val restored = cipher.encrypt("sk-8287a50c5c9d46a680b44e61e0f424f6")
                    db.appConfigDao().setValue(com.wordmemo.app.data.local.entity.AppConfigEntity("api_key", restored))
                    android.util.Log.i("AiWordGenerator", "✅ API Key 自动恢复（加密存储）")
                    apiKey = "sk-8287a50c5c9d46a680b44e61e0f424f6"
                } catch (_: Exception) {
                    db.appConfigDao().setValue(com.wordmemo.app.data.local.entity.AppConfigEntity("api_key", "sk-8287a50c5c9d46a680b44e61e0f424f6"))
                    android.util.Log.w("AiWordGenerator", "⚠️ API Key 明文存储（加密不可用）")
                    apiKey = "sk-8287a50c5c9d46a680b44e61e0f424f6"
                }
                if (configs.none { it.key == "api_base_url" })
                    db.appConfigDao().setValue(com.wordmemo.app.data.local.entity.AppConfigEntity("api_base_url", "https://api.deepseek.com"))
                if (configs.none { it.key == "api_model" })
                    db.appConfigDao().setValue(com.wordmemo.app.data.local.entity.AppConfigEntity("api_model", "deepseek-chat"))
            }

            val baseUrl = configs.firstOrNull { it.key == "api_base_url" }?.value
                ?: "https://api.deepseek.com"
            val model = configs.firstOrNull { it.key == "api_model" }?.value
                ?: "deepseek-chat"
            if (apiKey.isBlank()) null else AiConfig(apiKey, baseUrl, model)
        } catch (_: Exception) { null }
    }

    /** 估算用户当前难度等级 1-10 */
    suspend fun estimateDifficultyLevel(db: WordMemoDatabase): Int = withContext(Dispatchers.IO) {
        try {
            val total = db.flashcardDao().countTotal()
            if (total < 5) return@withContext 6 // 新手默认中级
            val mastered = db.flashcardDao().countMastered()
            val ratio = mastered.toFloat() / total
            when {
                ratio < 0.1f -> 4  // 初级
                ratio < 0.3f -> 6  // 中级
                ratio < 0.5f -> 7  // 中高级
                else -> 8          // 高级
            }
        } catch (_: Exception) { 6 }
    }

    /** 生成行业词汇（带质量过滤 + 自动重试） */
    suspend fun generateVocab(
        apiConfig: AiConfig,
        difficultyLevel: Int,
        count: Int = 8
    ): Result<List<GeneratedWord>> = withContext(Dispatchers.IO) {
        for (attempt in 1..2) {
            try {
                val prompt = buildPrompt(difficultyLevel, count)
                val client = AiApiClient(gson)
                val raw = client.chatCompletion(
                    apiConfig.baseUrl, apiConfig.apiKey, apiConfig.model,
                    prompt, "请生成 $count 个单词"
                ) ?: continue

                android.util.Log.i("AiWordGenerator", "尝试 $attempt, 原始响应前200字: ${raw.take(200)}")

                // 先解析对象，失败则兜底字符串
                val allWords = parseWordsRobust(raw)
                val fallback = if (allWords.isEmpty()) parseStringArray(raw) else emptyList()
                val combined = if (allWords.isNotEmpty()) allWords else fallback

                // 质量过滤
                val valid = filterValidWords(combined)
                android.util.Log.i("AiWordGenerator", "尝试 $attempt: ${combined.size}个原始, ${valid.size}个合格")

                if (valid.size >= 5) {
                    return@withContext Result.success(valid.take(count))
                }
                // 合格词太少 → 重试
                android.util.Log.w("AiWordGenerator", "合格词不足 ${valid.size}/$count, 重试...")
            } catch (e: Exception) {
                android.util.Log.e("AiWordGenerator", "尝试 $attempt 失败", e)
            }
        }
        Result.failure(Exception("AI 生成失败，请重试"))
    }

    private fun buildPrompt(level: Int, count: Int): String = """
你是一位英语词汇专家，专精于海外 EPC（Engineering-Procurement-Construction）工程项目的商务与技术英语。

你的任务：为一位中国籍海外光伏/风电/储能 EPC 项目的项目经理/总工，生成 $count 个实用英语词汇。

【用户画像】
- 身份：海外 EPC 项目技术负责人/项目经理
- 工作场景：与国外业主开会（协调会/进度会/技术交底）、现场施工沟通、合同与变更管理、质量验收、安全巡检
- 当前英语水平：**等级 $level/10**（7=中高级，能独立主持会议但专业术语不足）

【词汇要求】
1. 必须是海外 EPC 项目真实使用的词汇，涵盖：
   - 合同与商务（LOA, Variation Order, Milestone Payment）
   - 技术规格（PV module rating, BESS capacity, SCADA system）
   - 施工管理（civil works, mechanical completion, punch list）
   - 质量安全（NCR, ITP, HSE inspection, method statement）
   - 会议沟通（kick-off meeting, progress review, site walk-down）
2. 不得生成过于基础的词汇（如 hello, meeting, project 等）
3. 每个词附带真实例句，体现 EPC 场景

【输出格式】
必须输出 JSON 数组，每项是**对象**（不是字符串）：
```json
[
  {
    "english": "Variation Order",
    "chinese": "变更令（合同金额或工期的变更指令）",
    "phonetic": "/ˌveəriˈeɪʃən ˈɔːdər/",
    "example": "The contractor submitted a variation order for the additional PV panels.",
    "example_chinese": "承包商就额外光伏板提交了变更令。",
    "collocations": "issue a variation order, approve a variation order, variation order request"
  }
]
```

⚠️ **重要：必须输出对象数组，不是字符串数组。每项必须有 english 字段。不要输出 ["word1","word2"] 这种格式。**
""".trimIndent()
    /** 健壮的 JSON 解析 */
    private fun parseWordsRobust(text: String): List<GeneratedWord> {
        val content = extractAiContent(text) ?: return emptyList()
        val jsonStr = extractJsonArray(content) ?: return emptyList()

        return try {
            val arr = JsonParser.parseString(jsonStr).asJsonArray
            arr.mapNotNull { el ->
                try {
                    val obj = el.asJsonObject
                    // 兼容多种字段命名：english/word/name，chinese/meaning/translation
                    val eng = obj.get("english")?.asString
                        ?: obj.get("word")?.asString
                        ?: obj.get("name")?.asString
                        ?: return@mapNotNull null
                    val chi = obj.get("chinese")?.asString
                        ?: obj.get("meaning")?.asString
                        ?: obj.get("translation")?.asString ?: ""
                    val phone = obj.get("phonetic")?.asString
                        ?: obj.get("pronunciation")?.asString ?: ""
                    val ex = obj.get("example")?.asString ?: ""
                    val exCn = obj.get("example_chinese")?.asString
                        ?: obj.get("example_cn")?.asString ?: ""
                    val coll = obj.get("collocations")?.asString
                        ?: obj.get("collocation")?.asString ?: ""
                    GeneratedWord(eng, chi, phone, ex, exCn, coll)
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) {
            // 2. 可能是单层 JSON 对象包在数组外
            try {
                val obj = JsonParser.parseString(jsonStr).asJsonObject
                val arr2 = obj.getAsJsonArray("words") ?: obj.getAsJsonArray("data")
                    ?: obj.getAsJsonArray("result") ?: return emptyList()
                arr2.mapNotNull { el ->
                    val o = el.asJsonObject
                    GeneratedWord(
                        o.get("english")?.asString ?: o.get("word")?.asString ?: return@mapNotNull null,
                        o.get("chinese")?.asString ?: o.get("meaning")?.asString ?: "",
                        o.get("phonetic")?.asString ?: "",
                        o.get("example")?.asString ?: "",
                        o.get("example_chinese")?.asString ?: "",
                        o.get("collocations")?.asString ?: ""
                    )
                }
            } catch (_: Exception) { emptyList() }
        }
    }

    /** 从 OpenAI 兼容 API 响应中提取 choices[0].message.content */
    private fun extractAiContent(text: String): String? {
        return try {
            val root = JsonParser.parseString(text).asJsonObject
            val choices = root.getAsJsonArray("choices") ?: return text // 不是 API 包裹格式，直接返回
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

    /** 兜底：解析纯字符串数组 ["word1","word2"] */
    private fun parseStringArray(text: String): List<GeneratedWord> {
        val content = extractAiContent(text) ?: return emptyList()
        try {
            val arr = JsonParser.parseString(content).asJsonArray
            val words = arr.mapNotNull { el ->
                try {
                    val s = el.asString
                    if (s.isBlank()) null else s
                } catch (_: Exception) { null }
            }
            return words.map { GeneratedWord(it, "", "", "", "", "") }
        } catch (_: Exception) {
            val cleaned = content.trim().removeSurrounding("[", "]").removeSurrounding("\"")
            val parts = cleaned.split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotBlank() }
            return parts.map { GeneratedWord(it, "", "", "", "", "") }
        }
    }

    /** 质量过滤器：只保留看起来像真实英语词汇的词 */
    private fun filterValidWords(words: List<GeneratedWord>): List<GeneratedWord> {
        val commonLower = setOf("a", "an", "the", "in", "on", "at", "of", "to", "for", "with",
            "and", "or", "but", "not", "by", "from", "as", "is", "it", "be", "are", "was", "were")
        val garbage = setOf("json", "array", "output", "format", "```", "english", "chinese",
            "please", "generate", "vocabulary", "example", "phonetic", "collocations",
            "系统", "用户", "assistant", "你是一位", "你的任务", "输出格式")

        return words.filter { w ->
            val eng = w.english.trim()
            if (eng.length < 2 || eng.length > 60) return@filter false
            if (eng.all { it.isDigit() || it == '.' || it == '-' }) return@filter false
            // 含代码或中文特征 → 过滤
            for (c in eng) {
                if (c in "{}<>;\\`=") return@filter false
                if (c.code in 0x4E00..0x9FFF) return@filter false
            }
            // 必须至少有一个英文字母
            if (eng.none { it.isLetter() && it.isLowerCase() || it.isLetter() && it.isUpperCase() }) return@filter false
            // 排除提示词/代码片段
            val lower = eng.lowercase()
            if (garbage.any { lower.contains(it) }) return@filter false
            // 以小写开头但不是常见小写词 → 可能不是规范英语
            if (eng[0].isLowerCase() && eng[0].isLetter() && lower !in commonLower) return@filter false
            true
        }
    }
}
