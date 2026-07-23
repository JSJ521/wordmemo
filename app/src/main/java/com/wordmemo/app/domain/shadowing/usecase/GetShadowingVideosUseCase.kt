package com.wordmemo.app.domain.shadowing.usecase

import com.wordmemo.app.domain.shadowing.model.ShadowingVideo
import com.wordmemo.app.domain.shadowing.repository.ShadowingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetShadowingVideosUseCase @Inject constructor(
    private val shadowingRepository: ShadowingRepository
) {
    operator fun invoke(): Flow<List<ShadowingVideo>> =
        shadowingRepository.observeVideos()
}
