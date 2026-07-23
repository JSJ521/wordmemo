package com.wordmemo.app.data.pronunciation.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wordmemo.app.data.pronunciation.entity.AssessmentRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssessmentRecordDao {

    @Query("SELECT * FROM assessment_records WHERE record_id = :recordId")
    fun observeByRecordId(recordId: Long): Flow<AssessmentRecordEntity?>

    @Query("SELECT * FROM assessment_records WHERE sentence_id = :sentenceId ORDER BY created_at DESC")
    fun observeBySentenceId(sentenceId: Long): Flow<List<AssessmentRecordEntity>>

    @Query("SELECT * FROM assessment_records ORDER BY created_at DESC")
    fun observeAll(): Flow<List<AssessmentRecordEntity>>

    @Query("SELECT * FROM assessment_records WHERE id = :id")
    suspend fun getById(id: Long): AssessmentRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assessment: AssessmentRecordEntity): Long
}
