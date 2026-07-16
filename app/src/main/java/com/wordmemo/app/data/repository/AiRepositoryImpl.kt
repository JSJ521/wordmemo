package com.wordmemo.app.data.repository

import com.wordmemo.app.data.local.dao.AiContentDao
import com.wordmemo.app.data.local.dao.AppConfigDao
import com.wordmemo.app.data.local.entity.AiMnemonicEntity
import com.wordmemo.app.data.local.entity.AiRelationEntity
import com.wordmemo.app.data.network.AiApiClient
import com.wordmemo.app.data.network.AiApiRequestBuilder
import com.wordmemo.app.data.network.AiApiResponseParser
import com.wordmemo.app.domain.model.AiMnemonic
import com.wordmemo.app.domain.model.AiRelation
import com.wordmemo.app.domain.model.AiTranslation
import com.wordmemo.app.domain.repository.AiRepository
import com.wordmemo.app.util.Constants
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepositoryImpl @Inject constructor(
    private val aiContentDao: AiContentDao,
    private val appConfigDao: AppConfigDao,
    private val gson: Gson
) : AiRepository {

    private val requestBuilder = AiApiRequestBuilder()
    private val apiClient = AiApiClient(gson)
    private val parser = AiApiResponseParser(gson)

    private suspend fun getConfig(): Triple<String, String, String> {
        val baseUrl = appConfigDao.getValue(Constants.KEY_API_BASE_URL)?.value ?: "https://api.deepseek.com"
        val model = appConfigDao.getValue(Constants.KEY_API_MODEL)?.value ?: "deepseek-chat"
        val encryptedKey = appConfigDao.getValue(Constants.KEY_API_KEY_ENCRYPTED)?.value ?: ""
        val apiKey = if (encryptedKey.isNotBlank()) {
            try { com.wordmemo.app.data.encryption.ApiKeyCipher().decrypt(encryptedKey) } catch (_: Exception) { "sk-4ca3b87c453143a98c7af202947a8b13" }
        } else "sk-4ca3b87c453143a98c7af202947a8b13"
        return Triple(baseUrl, apiKey, model)
    }

    override suspend fun translate(word: String): AiTranslation {
        val (baseUrl, apiKey, model) = getConfig()
        val prompt = requestBuilder.buildTranslatePrompt(word)
        val response = apiClient.chatCompletion(baseUrl, apiKey, model,
            "You are a professional English-Chinese translator. Output ONLY valid JSON, no markdown, no code fences.",
            prompt
        )
        return parser.parseTranslation(response, word)
    }

    override suspend fun generateMnemonics(word: String): List<AiMnemonic> {
        val (baseUrl, apiKey, model) = getConfig()
        val prompt = requestBuilder.buildMnemonicsPrompt(word)
        val response = apiClient.chatCompletion(baseUrl, apiKey, model,
            "You are a mnemonic creation expert. Output ONLY valid JSON array, no markdown, no code fences.",
            prompt
        )
        return parser.parseMnemonics(response, 0)
    }

    override suspend fun getRelatedWords(word: String): List<AiRelation> {
        val (baseUrl, apiKey, model) = getConfig()
        val prompt = requestBuilder.buildRelationsPrompt(word)
        val response = apiClient.chatCompletion(baseUrl, apiKey, model,
            "You are a linguistics expert. Output ONLY valid JSON array, no markdown, no code fences.",
            prompt
        )
        return parser.parseRelations(response, 0)
    }

    override suspend fun testConnection(apiKey: String, baseUrl: String, model: String): Boolean {
        return apiClient.testConnection(baseUrl, apiKey, model)
    }
}
