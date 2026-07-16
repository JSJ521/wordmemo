package com.wordmemo.app.domain.model

data class AiRelation(
    val id: Long = 0,
    val wordId: Long = 0,
    val word: String,
    val type: String, // 同义词/反义词/搭配词组/形近词
    val definition: String? = null
)

data class AiRelationGroup(
    val wordId: Long,
    val relations: List<AiRelation>,
    val createdAt: Long = System.currentTimeMillis()
)
