package com.wordmemo.app.domain.usecase.group

import com.wordmemo.app.domain.repository.GroupRepository
import javax.inject.Inject

class AssignGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(wordId: Long, groupId: Long) {
        groupRepository.assignWordToGroup(wordId, groupId)
    }

    suspend fun removeWordFromGroup(wordId: Long, groupId: Long) {
        groupRepository.removeWordFromGroup(wordId, groupId)
    }
}
