package com.wordmemo.app.domain.shadowing.usecase

import com.wordmemo.app.domain.shadowing.model.ShadowingSentence
import com.wordmemo.app.domain.shadowing.repository.ShadowingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSentencesUseCase @Inject constructor(
    private val shadowingRepository: ShadowingRepository
) {
    operator fun invoke(videoId: Long): Flow<List<ShadowingSentence>> =
        shadowingRepository.getSentencesForVideo(videoId)
}
