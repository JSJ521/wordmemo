package com.wordmemo.app.data.pronunciation.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wordmemo.app.data.pronunciation.entity.PhonemeScoreEntity

@Dao
interface PhonemeScoreDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(scores: List<PhonemeScoreEntity>): List<Long>

    @Query("SELECT * FROM phoneme_scores WHERE assessment_id = :assessmentId")
    suspend fun getByAssessmentId(assessmentId: Long): List<PhonemeScoreEntity>
}
