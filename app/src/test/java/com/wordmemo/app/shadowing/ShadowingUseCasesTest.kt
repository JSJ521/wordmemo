package com.wordmemo.app.shadowing

import com.wordmemo.app.domain.shadowing.model.ShadowingSentence
import com.wordmemo.app.domain.shadowing.repository.ShadowingRepository
import com.wordmemo.app.domain.shadowing.usecase.GetSentencesUseCase
import com.wordmemo.app.domain.shadowing.usecase.GetShadowingVideosUseCase
import com.wordmemo.app.domain.shadowing.model.ShadowingVideo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

/**
 * GetSentencesUseCase 和 GetShadowingVideosUseCase 单元测试。
 */
class ShadowingUseCasesTest {

    @Test
    fun `GetSentencesUseCase 委托给 repository`() = runBlocking {
        val repository = mockk<ShadowingRepository>()
        val useCase = GetSentencesUseCase(repository)

        val expected = listOf(
            ShadowingSentence(id = 1, videoId = 1, sentenceIndex = 0, text = "Hello", startTimeMs = 0L, endTimeMs = 1000L)
        )

        coEvery { repository.getSentencesForVideo(1L) } returns flowOf(expected)

        val result = useCase(1L).first()
        assertEquals(1, result.size)
        assertEquals("Hello", result[0].text)
    }

    @Test
    fun `GetSentencesUseCase 视频无句子返回空列表`() = runBlocking {
        val repository = mockk<ShadowingRepository>()
        val useCase = GetSentencesUseCase(repository)

        coEvery { repository.getSentencesForVideo(999L) } returns flowOf(emptyList())

        val result = useCase(999L).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `GetShadowingVideosUseCase 委托给 repository`() = runBlocking {
        val repository = mockk<ShadowingRepository>()
        val useCase = GetShadowingVideosUseCase(repository)

        val videos = listOf(
            ShadowingVideo(title = "Video 1", sourceType = "bilibili", filePath = "/tmp/1.mp4", durationMs = 60000L),
            ShadowingVideo(title = "Video 2", sourceType = "local", filePath = "/tmp/2.mp4", durationMs = 120000L)
        )

        coEvery { repository.observeVideos() } returns flowOf(videos)

        val result = useCase().first()
        assertEquals(2, result.size)
        assertEquals("Video 1", result[0].title)
    }

    @Test
    fun `GetShadowingVideosUseCase 无视频时返回空列表`() = runBlocking {
        val repository = mockk<ShadowingRepository>()
        val useCase = GetShadowingVideosUseCase(repository)

        coEvery { repository.observeVideos() } returns flowOf(emptyList())

        val result = useCase().first()
        assertTrue(result.isEmpty())
    }
}
