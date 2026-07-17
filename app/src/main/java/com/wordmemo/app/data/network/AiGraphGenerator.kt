package com.wordmemo.app.data.network

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.wordmemo.app.data.local.WordMemoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

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

    /** 从数据库读取 API 配置（缺失时自动恢复） */
    suspend fun loadApiConfig(db: WordMemoDatabase): Triple<String, String, String>? {
        try {
            val configs = db.appConfigDao().getAll()
            val cipher = com.wordmemo.app.data.encryption.ApiKeyCipher()
            var apiKey = configs.firstOrNull { it.key == "api_key" }?.let {
                cipher.decrypt(it.value)
            } ?: ""

            // 检测数据库中的截断 Key（仅识别 ... 标记）
            if (!apiKey.isBlank() && apiKey.contains("...")) {
                android.util.Log.w("AiGraphGenerator", "⚠️ 检测到截断 Key")
                apiKey = ""
            }

            // 如果 Key 缺失，返回 null（让调用方显示错误提示）
            if (apiKey.isBlank()) {
                android.util.Log.e("AiGraphGenerator", "API Key 未配置")
                return null
            }
            // 同时确保 baseUrl 和 model 有默认值
            if (configs.none { it.key == "api_base_url" })
                db.appConfigDao().setValue(com.wordmemo.app.data.local.entity.AppConfigEntity("api_base_url", "https://api.deepseek.com"))
            if (configs.none { it.key == "api_model" })
                db.appConfigDao().setValue(com.wordmemo.app.data.local.entity.AppConfigEntity("api_model", "deepseek-chat"))

            val baseUrl = configs.firstOrNull { it.key == "api_base_url" }?.value
                ?: "https://api.deepseek.com"
            val model = configs.firstOrNull { it.key == "api_model" }?.value
                ?: "deepseek-chat"
            return Triple(apiKey, baseUrl, model)
        } catch (e: Exception) {
            android.util.Log.e("AiGraphGenerator", "loadApiConfig 异常", e)
            return null
        }
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
    "chinese": "中文释义（说明与L0的关系）",
    "type": "synonym/antonym/collocation/concept/similar",
    "children": [
      {"word": "l2_word_a", "chinese": "中文释义（说明与父级L1的关系）", "type": "关系类型"},
      {"word": "l2_word_b", "chinese": "中文释义（说明与父级L1的关系）", "type": "关系类型"}
    ]
  }
]

示例——中心词 "abandon":
[
  {"word":"desert","chinese":"抛弃（abandon的同义词）","type":"synonym","children":[
    {"word":"forsake","chinese":"遗弃","type":"synonym"},
    {"word":"retain","chinese":"保留","type":"antonym"}]},
  {"word":"keep","chinese":"保留（abandon的反义词）","type":"antonym","children":[
    {"word":"maintain","chinese":"维持","type":"synonym"},
    {"word":"discard","chinese":"丢弃","type":"antonym"}]}
]

请开始为 "$centerWord" 构建。"""

        // 直接使用 OkHttp 调用，不经过 AiApiClient
        return withContext(Dispatchers.IO) {
            // 先尝试标准调用
            try {
                val urlStr = "${baseUrl.trimEnd('/')}/chat/completions"
                val jsonBody = """{
                    "model": "$model",
                    "messages": [{"role": "user", "content": ${gson.toJson(prompt)}}],
                    "temperature": 0.7,
                    "max_tokens": 4000
                }""".trimIndent()
                
                val result = doApiCall(urlStr, apiKey, jsonBody)
                if (result != null) return@withContext result
            } catch (_: Exception) { }
            
            // 兜底：用宽松 SSL 再试一次
            try {
                val urlStr = "${baseUrl.trimEnd('/')}/chat/completions"
                val jsonBody = """{
                    "model": "$model",
                    "messages": [{"role": "user", "content": ${gson.toJson(prompt)}}],
                    "temperature": 0.7,
                    "max_tokens": 4000
                }""".trimIndent()
                val relaxedResult = doApiCallRelaxed(urlStr, apiKey, jsonBody)
                if (relaxedResult != null) return@withContext relaxedResult
            } catch (_: Exception) { }
            
            null
        }
    }
    
    /** 标准 OkHttp 调用（每次新建连接，避免池复用问题） */
    private suspend fun doApiCall(urlStr: String, apiKey: String, jsonBody: String): String? {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(0, 1, java.util.concurrent.TimeUnit.MILLISECONDS))
            .retryOnConnectionFailure(true)
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .build()
        val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = okhttp3.Request.Builder()
            .url(urlStr)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: "no body"
            throw java.io.IOException("HTTP ${response.code}: ${errBody.take(100)}")
        }
        val raw = response.body?.string()
        val content = raw?.let { extractAiContent(it) }
        return content?.let { extractJsonArray(it) }
    }
    
    /** 宽松 SSL 调用（兼容部分运营商证书问题） */
    private suspend fun doApiCallRelaxed(urlStr: String, apiKey: String, jsonBody: String): String? {
        val trustAll = object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        }
        val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustAll), java.security.SecureRandom())
        
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .connectionPool(okhttp3.ConnectionPool(0, 1, java.util.concurrent.TimeUnit.MILLISECONDS))
            .retryOnConnectionFailure(true)
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .build()
        val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = okhttp3.Request.Builder()
            .url(urlStr)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null
        val raw = response.body?.string()
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
        
        // 方式1: 从 ```json 代码块中提取
        val codeBlock = Regex("""```(?:json)?\s*\n?([\s\S]*?)\n?```""").find(trimmed)
        if (codeBlock != null) {
            val content = codeBlock.groupValues[1].trim()
            if (content.startsWith("[") || content.startsWith("{")) return content
        }
        
        // 方式2: 找第一个 [ 到最后一个 ]
        val startB = trimmed.indexOf('[')
        val endB = trimmed.lastIndexOf(']')
        if (startB >= 0 && endB > startB) {
            val candidate = trimmed.substring(startB, endB + 1)
            // 验证候选 JSON 是否能解析
            if (isValidJsonArray(candidate)) return candidate
            // 如果开头有残渣（如 [} 这种），尝试修复
            val fixed = fixBrokenJsonArray(candidate)
            if (fixed != null) return fixed
            // 尝试找真正的 [ 开始的第一个 { 
            val firstObj = candidate.indexOf('{')
            if (firstObj > 0) {
                val reExtract = trimmed.substring(startB + firstObj, endB + 1)
                if (isValidJsonArray(reExtract)) return reExtract
            }
        }
        
        // 方式3: 找 { 到最后一个 }
        val startO = trimmed.indexOf('{')
        val endO = trimmed.lastIndexOf('}')
        if (startO >= 0 && endO > startO) {
            val candidate = trimmed.substring(startO, endO + 1)
            if (isValidJsonArray(candidate)) return candidate
        }
        
        return null
    }

    /** 检查字符串是否是合法的 JSON 数组 */
    private fun isValidJsonArray(text: String): Boolean {
        val t = text.trim()
        if (!t.startsWith("[")) return false
        return try {
            JsonParser.parseString(t)
            true
        } catch (_: Exception) { false }
    }

    /** 修复以 [ 开头但紧接着 } 的残缺 JSON */
    private fun fixBrokenJsonArray(text: String): String? {
        // 去掉 [ 开头的非 { 字符（如 [} 变成 [）
        var cleaned = text.trim()
        if (cleaned.startsWith("[")) {
            // 找到第一个真正的 {
            val firstObj = cleaned.indexOf('{')
            if (firstObj > 0) {
                cleaned = "[" + cleaned.substring(firstObj)
            }
            // 找到最后一个 } 并确保后面是 ]
            val lastObj = cleaned.lastIndexOf('}')
            if (lastObj > 0) {
                val beforeClose = cleaned.substring(0, lastObj + 1)
                val afterClose = cleaned.substring(lastObj + 1)
                if (afterClose.contains("]")) {
                    cleaned = beforeClose + "]"
                }
            }
            if (isValidJsonArray(cleaned)) return cleaned
        }
        return null
    }

    /** 解析 AI 返回的 JSON 为图谱数据 */
    fun parseResult(json: String): AiGraphResult? {
        return try {
            val root = JsonParser.parseString(json)
            val relations = mutableListOf<AiRelation>()

            if (root.isJsonArray) {
                val arr = root.asJsonArray
                for (el in arr) {
                    val obj = el.asJsonObject
                    val word = obj.get("word")?.asString ?: continue
                    val type = obj.get("type")?.asString ?: ""
                    val children = parseChildren(obj.getAsJsonArray("children"))

                    if (word.lowercase() == centerWordForRelations.lowercase()) {
                        // 格式0: 中心词作为第一项，children 是真正的 L1
                        relations.addAll(children)
                    } else {
                        // 格式1: 扁平数组 [L1, L1, ...]
                        relations.add(AiRelation(word, obj.get("chinese")?.asString ?: "", type, children))
                    }
                }
            } else if (root.isJsonObject) {
                val obj = root.asJsonObject
                // 格式2: 嵌套对象 {word, children: [L1{w, children:[L2]}]}
                val childrenArr = obj.getAsJsonArray("children")
                if (childrenArr != null) {
                    for (el in childrenArr) {
                        val co = el.asJsonObject
                        val word = co.get("word")?.asString ?: continue
                        val chinese = co.get("chinese")?.asString ?: ""
                        val type = co.get("type")?.asString ?: ""
                        val l2 = parseChildren(co.getAsJsonArray("children"))
                        relations.add(AiRelation(word, chinese, type, l2))
                    }
                }
                // 格式3: 对象含 relations/data/words 数组字段
                if (relations.isEmpty()) {
                    for (key in listOf("relations", "data", "words")) {
                        val arr = obj.getAsJsonArray(key)
                        if (arr != null) {
                            for (el in arr) {
                                val co = el.asJsonObject
                                val word = co.get("word")?.asString ?: co.get("english")?.asString ?: continue
                                val chinese = co.get("chinese")?.asString ?: co.get("meaning")?.asString ?: ""
                                val type = co.get("type")?.asString ?: co.get("relation")?.asString ?: ""
                                val children = parseChildren(co.getAsJsonArray("children") ?: co.getAsJsonArray("l2"))
                                relations.add(AiRelation(word, chinese, type, children))
                            }
                            break
                        }
                    }
                }
            }

            if (relations.size < 3) return null
            AiGraphResult(centerWordForRelations, relations.distinctBy { it.word.lowercase() })
        } catch (_: Exception) { null }
    }

    /** 解析 children 数组 */
    private fun parseChildren(arr: com.google.gson.JsonArray?): List<AiRelation> {
        if (arr == null) return emptyList()
        return arr.mapNotNull { el ->
            try {
                val obj = el.asJsonObject
                val word = obj.get("word")?.asString ?: return@mapNotNull null
                val chinese = obj.get("chinese")?.asString ?: ""
                val type = obj.get("type")?.asString ?: ""
                // 递归处理三层 children（AI 有时会继续嵌套）
                val grandChildren = parseChildren(obj.getAsJsonArray("children"))
                AiRelation(word, chinese, type, grandChildren)
            } catch (_: Exception) { null }
        }
    }

    private var centerWordForRelations: String = ""
    fun setCenterWord(word: String) { centerWordForRelations = word }
}
