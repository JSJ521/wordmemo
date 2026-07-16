package com.wordmemo.app.domain.usecase.group

import com.wordmemo.app.domain.model.Word
import com.wordmemo.app.domain.repository.WordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FilterByGroupUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    operator fun invoke(groupId: Long): Flow<List<Word>> {
        return wordRepository.observeByGroup(groupId)
    }
}
