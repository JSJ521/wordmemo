package com.wordmemo.app.data.local.mapper

import com.wordmemo.app.data.local.entity.WordEntity
import com.wordmemo.app.domain.model.Word

fun WordEntity.toDomain(): Word = Word(
    id = id,
    english = english,
    chinese = chinese,
    phonetic = phonetic,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Word.toEntity(): WordEntity = WordEntity(
    id = id,
    english = english,
    chinese = chinese,
    phonetic = phonetic,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt
)
