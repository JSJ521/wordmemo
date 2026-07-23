package com.wordmemo.app.domain.shadowing.usecase

import com.wordmemo.app.data.shadowing.entity.ShadowingVideoEntity
import com.wordmemo.app.data.shadowing.service.VideoImportService
import com.wordmemo.app.domain.shadowing.model.ShadowingVideo
import com.wordmemo.app.domain.shadowing.repository.ShadowingRepository
import javax.inject.Inject

class ImportVideoUseCase @Inject constructor(
    private val videoImportService: VideoImportService,
    private val shadowingRepository: ShadowingRepository
) {
    suspend operator fun invoke(url: String): Result<ShadowingVideo> {
        return videoImportService.downloadFromBilibili(url).map { entity ->
            shadowingRepository.getVideoById(entity.id) ?: ShadowingVideo(
                title = entity.title,
                sourceType = entity.sourceType,
                sourceUrl = entity.sourceUrl,
                filePath = entity.filePath,
                durationMs = entity.durationMs
            )
        }
    }
}
