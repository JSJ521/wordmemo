package com.wordmemo.app.data.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI API 请求构建器。
 * 构建向 LLM 发送的提示词和请求结构。
 */
@Singleton
class AiApiRequestBuilder @Inject constructor() {

    fun buildTranslatePrompt(word: String): String {
        return """
            You are a professional English-Chinese translator. Translate the word or phrase "$word".
            Provide:
            1. The phonetic transcription (音标) in IPA format
            2. The most common Chinese translation
            3. 2-3 example sentences with Chinese translations
            4. Usage notes
            Format as JSON: {"translation": "...", "phonetic": "...", "examples": ["..."], "usage": "..."}
        """.trimIndent()
    }

    fun buildMnemonicsPrompt(word: String): String {
        return """
            Generate creative mnemonics for the word "$word" to help Chinese speakers remember it.
            Provide 3 different methods:
            1. Phonetic (谐音) - Chinese sound-alike association
            2. Root analysis (词根) - etymological breakdown
            3. Story (故事) - a memorable story
            Format as JSON array: [{"method": "谐音", "content": "..."}, ...]
        """.trimIndent()
    }

    fun buildRelationsPrompt(word: String): String {
        return """
            Find related words for "$word": synonyms (同义词), antonyms (反义词), common collocations (搭配词组).
            Return at least 5 items.
            Format as JSON array: [{"word": "...", "type": "同义词", "definition": "..."}, ...]
        """.trimIndent()
    }

    fun buildTestPrompt(): String = "Respond with a single word: OK"
}
