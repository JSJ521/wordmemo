package com.wordmemo.app.data.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ConnectionPool
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiApiClient @Inject constructor(
    private val gson: Gson
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
        .retryOnConnectionFailure(true)
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userMessage: String
    ): String? {
        val url = "${baseUrl.trimEnd('/')}/chat/completions"
        val requestBody = ChatCompletionRequest(
            model = model,
            messages = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = userMessage)
            ),
            temperature = 0.7,
            maxTokens = 2000
        )
        val jsonBody = gson.toJson(requestBody)
        val body = jsonBody.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        return withContext(Dispatchers.IO) {
            // 指数退避重试：最多3次，间隔1s/3s/5s
            val delays = listOf(1000L, 3000L, 5000L)
            var lastError: String? = null
            for (attempt in 1..3) {
                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val raw = response.body?.string()
                        if (raw != null) return@withContext raw
                    } else {
                        lastError = "HTTP ${response.code}: ${response.body?.string()?.take(100) ?: "no body"}"
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: "unknown error"
                }
                if (attempt < 3) {
                    kotlinx.coroutines.delay(delays[attempt - 1])
                }
            }
            android.util.Log.e("AiApiClient", "All 3 retries failed: $lastError")
            null
        }
    }

    suspend fun testConnection(baseUrl: String, apiKey: String, model: String): Boolean {
        return chatCompletion(baseUrl, apiKey, model, "Respond with: OK", "Test") != null
    }

    private data class ChatCompletionRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Double = 0.7,
        @SerializedName("max_tokens") val maxTokens: Int = 2000
    )
    private data class Message(val role: String, val content: String)
}
