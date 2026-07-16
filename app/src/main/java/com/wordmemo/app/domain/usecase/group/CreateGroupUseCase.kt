package com.wordmemo.app.domain.usecase.group

import com.wordmemo.app.domain.model.Group
import com.wordmemo.app.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CreateGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(name: String, color: String = "#2196F3"): Long {
        return groupRepository.create(name, color)
    }

    fun observeAll(): Flow<List<Group>> {
        return groupRepository.observeAll()
    }
}
