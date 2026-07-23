package com.wordmemo.app.shadowing

import com.wordmemo.app.data.shadowing.entity.ShadowingVideoEntity
import com.wordmemo.app.data.shadowing.service.VideoImportService
import com.wordmemo.app.domain.shadowing.usecase.ImportVideoUseCase
import com.wordmemo.app.domain.shadowing.repository.ShadowingRepository
import com.wordmemo.app.domain.shadowing.model.ShadowingVideo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * ImportVideoUseCase 单元测试 — 验证视频导入业务编排逻辑。
 */
class ImportVideoUseCaseTest {

    private val videoImportService = mockk<VideoImportService>()
    private val shadowingRepository = mockk<ShadowingRepository>()
    private val useCase = ImportVideoUseCase(videoImportService, shadowingRepository)

    @Test
    fun `成功下载时返回ShadowingVideo`() = runBlocking {
        val entity = ShadowingVideoEntity(
            id = 1,
            title = "测试B站视频",
            sourceType = "bilibili",
            sourceUrl = "https://bilibili.com/video/BV1Test",
            filePath = "/tmp/video.mp4",
            durationMs = 120000L
        )

        coEvery { videoImportService.downloadFromBilibili("https://bilibili.com/video/BV1Test") } returns Result.success(entity)
        coEvery { shadowingRepository.getVideoById(1L) } returns ShadowingVideo(
            id = 1,
            title = "测试B站视频",
            sourceType = "bilibili",
            sourceUrl = "https://bilibili.com/video/BV1Test",
            filePath = "/tmp/video.mp4",
            durationMs = 120000L
        )

        val result = useCase("https://bilibili.com/video/BV1Test")

        assertTrue(result.isSuccess)
        val video = result.getOrNull()
        assertNotNull(video)
        assertEquals("测试B站视频", video!!.title)
        assertEquals("bilibili", video!!.sourceType)
    }

    @Test
    fun `下载失败时返回失败结果`() = runBlocking {
        coEvery { videoImportService.downloadFromBilibili(any()) } returns Result.failure(
            com.wordmemo.app.data.shadowing.service.VideoImportException.VideoDownloadException("下载失败")
        )

        val result = useCase("https://bilibili.com/video/bad")

        assertTrue(result.isFailure)
    }

    @Test
    fun `不支持的URL返回失败`() = runBlocking {
        coEvery { videoImportService.downloadFromBilibili("https://youtube.com/watch?v=test") } returns Result.failure(
            com.wordmemo.app.data.shadowing.service.VideoImportException.UnsupportedUrlException("不支持的URL")
        )

        val result = useCase("https://youtube.com/watch?v=test")

        assertTrue(result.isFailure)
    }

    @Test
    fun `下载返回null的videoId时返回默认ShadowingVideo`() = runBlocking {
        val entity = ShadowingVideoEntity(
            id = 2,
            title = "新视频",
            sourceType = "bilibili",
            sourceUrl = "https://b23.tv/abc123",
            filePath = "/tmp/new.mp4",
            durationMs = 60000L
        )

        coEvery { videoImportService.downloadFromBilibili(any()) } returns Result.success(entity)
        coEvery { shadowingRepository.getVideoById(2L) } returns null // Repository returns null

        val result = useCase("https://b23.tv/abc123")

        assertTrue(result.isSuccess)
        val video = result.getOrNull()
        assertNotNull(video)
        // When repository returns null, UseCase falls back to constructing from entity
        assertEquals("新视频", video!!.title)
    }
}
