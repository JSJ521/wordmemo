package com.wordmemo.app.domain.usecase.word

import com.wordmemo.app.domain.repository.WordRepository
import javax.inject.Inject

class DeleteWordUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(id: Long) {
        wordRepository.delete(id)
    }
}
