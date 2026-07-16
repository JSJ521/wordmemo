package com.wordmemo.app.domain.repository

import com.wordmemo.app.domain.model.ImportResult
import com.wordmemo.app.domain.model.Word
import kotlinx.coroutines.flow.Flow

interface WordRepository {
    fun observeAll(): Flow<List<Word>>
    fun observeByGroup(groupId: Long): Flow<List<Word>>
    suspend fun getById(id: Long): Word?
    suspend fun getByEnglish(english: String): Word?
    suspend fun insert(word: Word): Long
    suspend fun update(word: Word)
    suspend fun delete(id: Long)
    suspend fun batchImport(words: List<Word>): ImportResult
    suspend fun search(query: String): List<Word>
}
