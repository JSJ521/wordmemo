package com.wordmemo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wordmemo.app.data.local.entity.GroupEntity
import com.wordmemo.app.data.local.entity.WordGroupCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM `groups` ORDER BY created_at DESC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun create(group: GroupEntity): Long

    @Query("UPDATE `groups` SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM `groups` WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun assignWordToGroup(crossRef: WordGroupCrossRef)

    @Query("DELETE FROM word_groups WHERE word_id = :wordId AND group_id = :groupId")
    suspend fun removeWordFromGroup(wordId: Long, groupId: Long)

    @Query("SELECT g.* FROM `groups` g INNER JOIN word_groups wg ON g.id = wg.group_id WHERE wg.word_id = :wordId")
    suspend fun getGroupsForWord(wordId: Long): List<GroupEntity>

    @Query("SELECT COUNT(*) FROM `groups`")
    suspend fun count(): Int
}
