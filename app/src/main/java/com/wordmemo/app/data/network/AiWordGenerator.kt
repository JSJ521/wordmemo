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
            val apiKey = configs.firstOrNull { it.key == "api_key" }?.let {
                cipher.decrypt(it.value)
            } ?: return@withContext null
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

    /** 生成行业词汇 */
    suspend fun generateVocab(
        apiConfig: AiConfig,
        difficultyLevel: Int,
        count: Int = 8
    ): Result<List<GeneratedWord>> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildPrompt(difficultyLevel, count)
            val client = AiApiClient(gson)
            val raw = client.chatCompletion(
                apiConfig.baseUrl, apiConfig.apiKey, apiConfig.model,
                prompt, "请生成 $count 个单词"
            ) ?: return@withContext Result.failure(Exception("API 无响应"))

            val json = extractJson(raw)
            val words = parseWords(json ?: raw)

            if (words.isEmpty()) {
                return@withContext Result.failure(Exception("AI 返回格式异常，请重试"))
            }
            android.util.Log.i("AiWordGenerator", "生成 ${words.size} 个单词: ${words.joinToString { it.english }}")
            Result.success(words.take(count))
        } catch (e: Exception) {
            android.util.Log.e("AiWordGenerator", "生成失败", e)
            Result.failure(e)
        }
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
仅输出 JSON 数组，不要其他文字：
[
  {
    "english": "word_or_phrase",
    "chinese": "精确中文释义（含工程语境说明）",
    "phonetic": "/音标/",
    "example": "英文例句（体现 EPC 场景实际用法）",
    "example_chinese": "例句中文翻译",
    "collocations": "2-3个常用搭配短语"
  }
]
""".trimIndent()

    private fun extractJson(text: String): String? {
        val codeBlock = Regex("""```(?:json)?\s*([\s\S]*?)```""").find(text)
        if (codeBlock != null) return codeBlock.groupValues[1].trim()
        return try {
            JsonParser.parseString(text.trim())
            text.trim()
        } catch (_: Exception) {
            val start = text.indexOf('[')
            val end = text.lastIndexOf(']')
            if (start >= 0 && end > start) text.substring(start, end + 1) else null
        }
    }

    private fun parseWords(json: String): List<GeneratedWord> {
        return try {
            val arr = JsonParser.parseString(json).asJsonArray
            arr.mapNotNull { el ->
                val obj = el.asJsonObject
                val eng = obj.get("english")?.asString ?: return@mapNotNull null
                GeneratedWord(
                    english = eng,
                    chinese = obj.get("chinese")?.asString ?: "",
                    phonetic = obj.get("phonetic")?.asString ?: "",
                    example = obj.get("example")?.asString ?: "",
                    exampleChinese = obj.get("example_chinese")?.asString ?: "",
                    collocations = obj.get("collocations")?.asString ?: ""
                )
            }
        } catch (_: Exception) { emptyList() }
    }
}
