package com.wordmemo.app.domain.usecase.review

import com.wordmemo.app.domain.model.Flashcard
import com.wordmemo.app.domain.repository.ReviewRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetReviewQueueUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository
) {
    suspend fun getDueCards(limit: Int = 20): List<Flashcard> {
        return reviewRepository.getDueCards(limit)
    }

    fun observeDueCount(): Flow<Int> {
        return reviewRepository.observeDueCount()
    }
}
