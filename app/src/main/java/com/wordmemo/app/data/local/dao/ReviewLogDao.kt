package com.wordmemo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wordmemo.app.data.local.entity.ReviewLogEntity

@Dao
interface ReviewLogDao {
    @Insert
    suspend fun insert(log: ReviewLogEntity): Long

    @Query("SELECT * FROM review_logs ORDER BY reviewed_at DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int): List<ReviewLogEntity>

    @Query("SELECT COUNT(*) FROM review_logs")
    suspend fun countTotal(): Int

    @Query("SELECT COUNT(*) FROM review_logs WHERE reviewed_at >= :dayStart AND reviewed_at < :dayEnd")
    suspend fun countTodayReviews(dayStart: Long, dayEnd: Long): Int

    @Query("""
        SELECT COUNT(*) FROM review_logs 
        WHERE reviewed_at >= :dayStart AND reviewed_at < :dayEnd 
        AND rating >= 3
    """)
    suspend fun countTodaySuccessful(dayStart: Long, dayEnd: Long): Int

    @Query("SELECT COUNT(DISTINCT card_id) FROM review_logs WHERE reviewed_at >= :dayStart AND reviewed_at < :dayEnd")
    suspend fun countTodayNewCards(dayStart: Long, dayEnd: Long): Int

    @Query("SELECT * FROM review_logs ORDER BY reviewed_at ASC")
    suspend fun getAllLogs(): List<ReviewLogEntity>
}
