package com.wordmemo.app.data.repository

import com.wordmemo.app.data.local.dao.FlashcardDao
import com.wordmemo.app.data.local.dao.ReviewLogDao
import com.wordmemo.app.data.local.entity.FlashcardEntity
import com.wordmemo.app.data.local.mapper.toDomain
import com.wordmemo.app.data.local.mapper.toEntity
import com.wordmemo.app.domain.model.Flashcard
import com.wordmemo.app.domain.model.ReviewLog
import com.wordmemo.app.domain.repository.ReviewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewRepositoryImpl @Inject constructor(
    private val flashcardDao: FlashcardDao,
    private val reviewLogDao: ReviewLogDao
) : ReviewRepository {

    override suspend fun getDueCards(limit: Int): List<Flashcard> =
        flashcardDao.getDueCards(System.currentTimeMillis(), limit).map { it.toDomain() }

    override suspend fun getCardByWordId(wordId: Long): Flashcard? =
        flashcardDao.getByWordId(wordId)?.toDomain()

    override suspend fun createCard(wordId: Long): Flashcard {
        val now = System.currentTimeMillis()
        val entity = FlashcardEntity(
            wordId = wordId,
            state = "New",
            due = now // New cards are due immediately
        )
        val id = flashcardDao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    override suspend fun saveCard(card: Flashcard) =
        flashcardDao.update(card.toEntity())

    override suspend fun saveReviewLog(log: ReviewLog) {
        reviewLogDao.insert(log.toEntity())
        Unit
    }

    override suspend fun getRecentLogs(minCount: Int): List<ReviewLog> {
        val logs = reviewLogDao.getRecentLogs(minCount.coerceAtLeast(20))
        return logs.map { it.toDomain() }
    }

    override fun observeDueCount(): Flow<Int> =
        flashcardDao.observeDueCount(System.currentTimeMillis())

    override suspend fun countTodayNewCards(): Int {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + 86400000L
        return reviewLogDao.countTodayNewCards(dayStart, dayEnd)
    }

    override suspend fun countTotalReviews(): Int =
        reviewLogDao.countTotal()

    override suspend fun countTodayReviews(): Int {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + 86400000L
        return reviewLogDao.countTodayReviews(dayStart, dayEnd)
    }

    override suspend fun countDueCards(): Int =
        flashcardDao.countDue(System.currentTimeMillis())

    override suspend fun getMasteredWordIds(): List<Long> =
        flashcardDao.getMasteredWordIds()
}
