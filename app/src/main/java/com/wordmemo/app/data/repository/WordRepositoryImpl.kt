package com.wordmemo.app.data.repository

import com.wordmemo.app.data.local.dao.WordDao
import com.wordmemo.app.data.local.mapper.toDomain
import com.wordmemo.app.data.local.mapper.toEntity
import com.wordmemo.app.domain.model.ImportResult
import com.wordmemo.app.domain.model.Word
import com.wordmemo.app.domain.repository.WordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordRepositoryImpl @Inject constructor(
    private val wordDao: WordDao
) : WordRepository {

    override fun observeAll(): Flow<List<Word>> =
        wordDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeByGroup(groupId: Long): Flow<List<Word>> =
        wordDao.observeByGroup(groupId).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): Word? =
        wordDao.getById(id)?.toDomain()

    override suspend fun getByEnglish(english: String): Word? =
        wordDao.getByEnglish(english)?.toDomain()

    override suspend fun insert(word: Word): Long =
        wordDao.insert(word.toEntity())

    override suspend fun update(word: Word) =
        wordDao.update(word.toEntity())

    override suspend fun delete(id: Long) =
        wordDao.deleteById(id)

    override suspend fun batchImport(words: List<Word>): ImportResult {
        var success = 0
        val errors = mutableListOf<String>()

        for (word in words) {
            try {
                val id = wordDao.insert(word.toEntity())
                if (id > 0) success++ else errors.add("重复: ${word.english}")
            } catch (e: Exception) {
                errors.add("导入失败: ${word.english} - ${e.message}")
            }
        }

        return ImportResult(
            totalInput = words.size,
            successCount = success,
            errorCount = errors.size,
            errors = errors
        )
    }

    override suspend fun search(query: String): List<Word> =
        wordDao.search(query).map { it.toDomain() }
}
