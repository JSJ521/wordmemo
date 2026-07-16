package com.wordmemo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wordmemo.app.data.local.entity.AiMnemonicEntity
import com.wordmemo.app.data.local.entity.AiRelationEntity

@Dao
interface AiContentDao {
    // Mnemonics
    @Query("SELECT * FROM ai_mnemonics WHERE word_id = :wordId ORDER BY created_at ASC")
    suspend fun getMnemonicsForWord(wordId: Long): List<AiMnemonicEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMnemonic(mnemonic: AiMnemonicEntity): Long

    @Query("DELETE FROM ai_mnemonics WHERE word_id = :wordId")
    suspend fun deleteMnemonicsForWord(wordId: Long)

    // Relations
    @Query("SELECT * FROM ai_relations WHERE word_id = :wordId LIMIT 1")
    suspend fun getRelationsForWord(wordId: Long): AiRelationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelations(relation: AiRelationEntity): Long

    @Query("DELETE FROM ai_relations WHERE word_id = :wordId")
    suspend fun deleteRelationsForWord(wordId: Long)
}
