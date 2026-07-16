package com.wordmemo.app.domain.repository

import com.wordmemo.app.domain.model.Flashcard
import com.wordmemo.app.domain.model.ReviewLog
import kotlinx.coroutines.flow.Flow

interface ReviewRepository {
    suspend fun getDueCards(limit: Int = 20): List<Flashcard>
    suspend fun getCardByWordId(wordId: Long): Flashcard?
    suspend fun createCard(wordId: Long): Flashcard
    suspend fun saveCard(card: Flashcard)
    suspend fun saveReviewLog(log: ReviewLog)
    suspend fun getRecentLogs(minCount: Int): List<ReviewLog>
    fun observeDueCount(): Flow<Int>
    suspend fun countTodayNewCards(): Int
    suspend fun countTotalReviews(): Int
    suspend fun countTodayReviews(): Int
    suspend fun countDueCards(): Int
    suspend fun getMasteredWordIds(): List<Long>
}
