package com.wordmemo.app.data.repository

import com.wordmemo.app.data.local.dao.FlashcardDao
import com.wordmemo.app.data.local.dao.ReviewLogDao
import com.wordmemo.app.data.local.dao.WordDao
import com.wordmemo.app.domain.model.DailyStats
import com.wordmemo.app.domain.model.Stats
import com.wordmemo.app.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val wordDao: WordDao,
    private val flashcardDao: FlashcardDao,
    private val reviewLogDao: ReviewLogDao
) : StatsRepository {

    override fun observeStats(): Flow<Stats> = flow {
        while (true) {
            emit(refreshStats())
            kotlinx.coroutines.delay(5000) // Refresh every 5s
        }
    }

    override suspend fun refreshStats(): Stats {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + 86400000L

        return Stats(
            totalWords = wordDao.count(),
            dueCards = flashcardDao.countDue(now),
            masteredWords = flashcardDao.countMastered(),
            totalReviews = reviewLogDao.countTotal(),
            todayReviews = reviewLogDao.countTodayReviews(dayStart, dayEnd),
            todayNewCards = reviewLogDao.countTodayNewCards(dayStart, dayEnd)
        )
    }
}
