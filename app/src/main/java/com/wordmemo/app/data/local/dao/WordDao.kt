package com.wordmemo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wordmemo.app.data.local.entity.WordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT * FROM words ORDER BY created_at DESC")
    fun observeAll(): Flow<List<WordEntity>>

    @Query("""
        SELECT w.* FROM words w
        INNER JOIN word_groups wg ON w.id = wg.word_id
        WHERE wg.group_id = :groupId
        ORDER BY w.created_at DESC
    """)
    fun observeByGroup(groupId: Long): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getById(id: Long): WordEntity?

    @Query("SELECT * FROM words WHERE english = :english LIMIT 1")
    suspend fun getByEnglish(english: String): WordEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: WordEntity): Long

    @Update
    suspend fun update(word: WordEntity)

    @Delete
    suspend fun delete(word: WordEntity)

    @Query("DELETE FROM words WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM words WHERE english LIKE '%' || :query || '%' OR chinese LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<WordEntity>

    @Query("SELECT COUNT(*) FROM words")
    suspend fun count(): Int
}
