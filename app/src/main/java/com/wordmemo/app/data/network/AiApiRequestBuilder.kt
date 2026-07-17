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
        return """你是一位专业英汉翻译专家。请翻译单词或短语 "$word"。

请输出以下内容（必须使用**国际音标(IPA)**，不要用中文拼音）：
1. 国际音标 phonetic（如 /əˈbændən/，不是拼音 fàng qì）
2. 最常用的中文翻译
3. 2-3个例句（中英对照）
4. 用法说明（如适用场景、搭配）

格式为 JSON：
{"translation": "...", "phonetic": "/.../", "examples": ["en: ... 中: ..."], "usage": "..."}

**重要**：phonetic 字段必须是英语 IPA 音标，不是中文拼音。"""
    }

    fun buildMnemonicsPrompt(word: String): String {
        return """你是一位专业的英语词汇记忆法专家，专精于为中文母语者（尤其是海外工程项目管理者）设计高效记忆方案。

请为单词 "${word}" 设计 3 种不同的记忆方法，每种方法必须包含完整的记忆逻辑和具体内容。

【输出要求】
必须输出 JSON 数组，不要额外文字：
[
  {
    "method": "谐音联想法",
    "content": "详细描述该单词的中文谐音、联想场景、以及与词义的关联逻辑。不少于30字。"
  },
  {
    "method": "词根词缀法",
    "content": "拆解单词的词根/前缀/后缀，解释每个部分的含义，说明如何组合出单词的实际意思。不少于30字。"
  },
  {
    "method": "场景故事法",
    "content": "编造一个简短但难忘的故事或画面，将单词的音、形、义融合在一起。不少于40字。"
  }
]

【质量要求】
- 每种方法必须有具体的联想内容，不能泛泛而谈
- 谐音要贴切，不能强行谐音
- 词根分析要有语言学依据
- 故事要有画面感，容易记住

请开始为 "${word}" 设计。"""
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
