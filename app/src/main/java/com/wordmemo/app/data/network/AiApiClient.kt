package com.wordmemo.app.data.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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
            maxTokens = 1000
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
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) response.body?.string() else null
            } catch (e: Exception) { null }
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
