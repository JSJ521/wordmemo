package com.wordmemo.app.domain.repository

import com.wordmemo.app.domain.model.Group
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    fun observeAll(): Flow<List<Group>>
    suspend fun create(name: String, color: String): Long
    suspend fun rename(id: Long, name: String)
    suspend fun delete(id: Long)
    suspend fun assignWordToGroup(wordId: Long, groupId: Long)
    suspend fun removeWordFromGroup(wordId: Long, groupId: Long)
    suspend fun getGroupsForWord(wordId: Long): List<Group>
}
