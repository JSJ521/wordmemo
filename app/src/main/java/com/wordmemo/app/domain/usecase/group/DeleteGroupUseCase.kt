package com.wordmemo.app.domain.usecase.group

import com.wordmemo.app.domain.model.Group
import com.wordmemo.app.domain.repository.GroupRepository
import javax.inject.Inject

class DeleteGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(id: Long) {
        groupRepository.delete(id)
    }
}
