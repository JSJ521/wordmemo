package com.wordmemo.app.domain.usecase.word

import com.wordmemo.app.domain.model.Word
import com.wordmemo.app.domain.repository.WordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchWordsUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend fun search(query: String): List<Word> {
        return wordRepository.search(query)
    }

    fun observeAll(): Flow<List<Word>> {
        return wordRepository.observeAll()
    }

    fun observeByGroup(groupId: Long): Flow<List<Word>> {
        return wordRepository.observeByGroup(groupId)
    }
}
