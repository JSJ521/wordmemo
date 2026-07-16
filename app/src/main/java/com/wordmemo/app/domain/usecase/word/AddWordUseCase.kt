package com.wordmemo.app.domain.usecase.word

import com.wordmemo.app.domain.model.Word
import com.wordmemo.app.domain.repository.WordRepository
import javax.inject.Inject

class AddWordUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(word: Word): Long {
        return wordRepository.insert(word)
    }
}
