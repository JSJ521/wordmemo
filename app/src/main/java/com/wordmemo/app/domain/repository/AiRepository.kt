package com.wordmemo.app.domain.repository

import com.wordmemo.app.domain.model.AiMnemonic
import com.wordmemo.app.domain.model.AiRelation
import com.wordmemo.app.domain.model.AiTranslation

interface AiRepository {
    suspend fun translate(word: String): AiTranslation
    suspend fun generateMnemonics(word: String): List<AiMnemonic>
    suspend fun getRelatedWords(word: String): List<AiRelation>
    suspend fun testConnection(apiKey: String, baseUrl: String, model: String): Boolean
}
