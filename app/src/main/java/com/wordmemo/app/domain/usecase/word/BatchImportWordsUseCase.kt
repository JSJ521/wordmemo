package com.wordmemo.app.domain.usecase.word

import com.wordmemo.app.domain.model.ImportResult
import com.wordmemo.app.domain.model.Word
import com.wordmemo.app.domain.repository.WordRepository
import javax.inject.Inject

class BatchImportWordsUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(words: List<Word>): ImportResult {
        return wordRepository.batchImport(words)
    }
}
