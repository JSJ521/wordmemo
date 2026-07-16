package com.wordmemo.app.domain.usecase.review

import com.wordmemo.app.domain.model.Flashcard
import com.wordmemo.app.domain.repository.ReviewRepository
import javax.inject.Inject

class GetDueCardsUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository
) {
    suspend operator fun invoke(limit: Int = 20): List<Flashcard> {
        return reviewRepository.getDueCards(limit)
    }
}
