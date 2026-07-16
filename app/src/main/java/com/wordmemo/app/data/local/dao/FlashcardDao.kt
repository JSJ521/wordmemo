package com.wordmemo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wordmemo.app.data.local.entity.FlashcardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards WHERE due <= :now ORDER BY state ASC, due ASC LIMIT :limit")
    suspend fun getDueCards(now: Long, limit: Int = 20): List<FlashcardEntity>

    @Query("SELECT * FROM flashcards WHERE word_id = :wordId LIMIT 1")
    suspend fun getByWordId(wordId: Long): FlashcardEntity?

    @Query("SELECT * FROM flashcards WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FlashcardEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(card: FlashcardEntity): Long

    @Update
    suspend fun update(card: FlashcardEntity)

    @Query("SELECT COUNT(*) FROM flashcards WHERE due <= :now AND state != 'New'")
    suspend fun countDue(now: Long): Int

    @Query("SELECT COUNT(*) FROM flashcards WHERE state = 'New' AND due <= :now")
    suspend fun countNewDue(now: Long): Int

    @Query("SELECT COUNT(*) FROM flashcards")
    suspend fun countTotal(): Int

    @Query("SELECT COUNT(*) FROM flashcards WHERE state = 'Review' AND stability >= 21.0")
    suspend fun countMastered(): Int

    @Query("SELECT word_id FROM flashcards WHERE state = 'Review' AND stability >= 21.0")
    suspend fun getMasteredWordIds(): List<Long>

    @Query("SELECT * FROM flashcards WHERE state != 'New' ORDER BY due ASC")
    fun observeAllDue(): Flow<List<FlashcardEntity>>

    @Query("SELECT COUNT(*) FROM flashcards WHERE due <= :now AND state != 'New'")
    fun observeDueCount(now: Long): Flow<Int>
}
