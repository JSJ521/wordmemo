package com.wordmemo.app.data.repository

import com.wordmemo.app.data.local.dao.GroupDao
import com.wordmemo.app.data.local.entity.GroupEntity
import com.wordmemo.app.data.local.entity.WordGroupCrossRef
import com.wordmemo.app.domain.model.Group
import com.wordmemo.app.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val groupDao: GroupDao
) : GroupRepository {

    override fun observeAll(): Flow<List<Group>> =
        groupDao.observeAll().map { list ->
            list.map { Group(id = it.id, name = it.name, color = it.color, createdAt = it.createdAt) }
        }

    override suspend fun create(name: String, color: String): Long =
        groupDao.create(GroupEntity(name = name, color = color))

    override suspend fun rename(id: Long, name: String) =
        groupDao.rename(id, name)

    override suspend fun delete(id: Long) =
        groupDao.delete(id)

    override suspend fun assignWordToGroup(wordId: Long, groupId: Long) =
        groupDao.assignWordToGroup(WordGroupCrossRef(wordId, groupId))

    override suspend fun removeWordFromGroup(wordId: Long, groupId: Long) =
        groupDao.removeWordFromGroup(wordId, groupId)

    override suspend fun getGroupsForWord(wordId: Long): List<Group> =
        groupDao.getGroupsForWord(wordId).map {
            Group(id = it.id, name = it.name, color = it.color, createdAt = it.createdAt)
        }
}
