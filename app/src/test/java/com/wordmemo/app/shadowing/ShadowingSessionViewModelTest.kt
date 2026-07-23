package com.wordmemo.app.shadowing

import com.wordmemo.app.domain.shadowing.model.ShadowingSentence
import com.wordmemo.app.domain.shadowing.repository.ShadowingRepository
import com.wordmemo.app.domain.shadowing.usecase.GetSentencesUseCase
import com.wordmemo.app.ui.screen.shadowing.AudioSource
import com.wordmemo.app.ui.screen.shadowing.RecordingState
import com.wordmemo.app.ui.screen.shadowing.ShadowingSessionViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * ShadowingSessionViewModel 单元测试 — 验证录音状态管理、句子导航、UI State 变化。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShadowingSessionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val getSentencesUseCase = mockk<GetSentencesUseCase>()
    private val shadowingRepository = mockk<ShadowingRepository>()
    private lateinit var viewModel: ShadowingSessionViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // Default: no video found
        coEvery { shadowingRepository.getVideoById(any()) } returns null
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `初始状态默认为IDLE`() = runTest {
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(emptyList())

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)

        val state = viewModel.uiState.value
        assertEquals(RecordingState.IDLE, state.recordingState)
        assertFalse(state.isRecording)
        assertFalse(state.hasRecording)
        assertEquals(0, state.recordingDurationMs)
        assertTrue("初始波形应为空", state.waveformAmplitudes.isEmpty())
        assertEquals(AudioSource.ORIGINAL, state.activeAudioSource)
    }

    @Test
    fun `loadSentences 加载句子并更新状态`() = runTest {
        val sentences = listOf(
            ShadowingSentence(id = 1, videoId = 1, sentenceIndex = 0, text = "Hello", startTimeMs = 0L, endTimeMs = 1000L),
            ShadowingSentence(id = 2, videoId = 1, sentenceIndex = 1, text = "World", startTimeMs = 1000L, endTimeMs = 2000L)
        )
        coEvery { getSentencesUseCase.invoke(1L) } returns MutableStateFlow(sentences)

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        viewModel.loadSentences(1L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.sentences.size)
        assertEquals(0, state.currentSentenceIndex)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `loadSentences 正常加载后不应有error`() = runTest {
        coEvery { getSentencesUseCase.invoke(1L) } returns MutableStateFlow(emptyList())

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        viewModel.loadSentences(1L)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `loadSentences 时获取视频信息`() = runTest {
        val sentences = listOf(
            ShadowingSentence(id = 1, videoId = 1, sentenceIndex = 0, text = "Hello", startTimeMs = 0L, endTimeMs = 1000L)
        )
        coEvery { getSentencesUseCase.invoke(1L) } returns MutableStateFlow(sentences)
        coEvery { shadowingRepository.getVideoById(1L) } returns com.wordmemo.app.domain.shadowing.model.ShadowingVideo(
            id = 1,
            title = "Test Video",
            sourceType = "local",
            filePath = "/path/to/video.mp4",
            durationMs = 60000L
        )

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        viewModel.loadSentences(1L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Test Video", state.videoTitle)
        assertEquals("/path/to/video.mp4", state.videoFilePath)
        assertEquals(60000L, state.videoDurationMs)
        coVerify { shadowingRepository.getVideoById(1L) }
    }

    @Test
    fun `startRecording 切换到RECORDING状态`() = runTest {
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(emptyList())

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        viewModel.startRecording()
        advanceTimeBy(200)

        val state = viewModel.uiState.value
        assertEquals(RecordingState.RECORDING, state.recordingState)
        assertTrue(state.isRecording)
        viewModel.stopRecording()
        advanceUntilIdle()
    }

    @Test
    fun `stopRecording 切换到PLAYBACK状态`() = runTest {
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(emptyList())

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        viewModel.startRecording()
        viewModel.stopRecording()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(RecordingState.PLAYBACK, state.recordingState)
        assertFalse(state.isRecording)
        assertTrue(state.hasRecording)
        assertTrue("停止后波形归零", state.waveformAmplitudes.isEmpty())
    }

    @Test
    fun `deleteRecording 重置为IDLE`() = runTest {
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(emptyList())

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        viewModel.startRecording()
        viewModel.stopRecording()
        viewModel.deleteRecording()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(RecordingState.IDLE, state.recordingState)
        assertFalse(state.hasRecording)
        assertEquals(0L, state.recordingDurationMs)
        assertEquals(AudioSource.ORIGINAL, state.activeAudioSource)
    }

    @Test
    fun `playRecording 切换到录音播放源`() = runTest {
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(emptyList())

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        viewModel.playRecording()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AudioSource.RECORDING, state.activeAudioSource)
        assertEquals(RecordingState.PLAYBACK, state.recordingState)
    }

    @Test
    fun `switchAudioSource 切换音频源`() = runTest {
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(emptyList())

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        viewModel.switchAudioSource(AudioSource.RECORDING)
        assertEquals(AudioSource.RECORDING, viewModel.uiState.value.activeAudioSource)

        viewModel.switchAudioSource(AudioSource.ORIGINAL)
        assertEquals(AudioSource.ORIGINAL, viewModel.uiState.value.activeAudioSource)
    }

    @Test
    fun `previousSentence 向前导航到前一句`() = runTest {
        val sentences = listOf(
            ShadowingSentence(id = 1, videoId = 1, sentenceIndex = 0, text = "First", startTimeMs = 0L, endTimeMs = 1000L),
            ShadowingSentence(id = 2, videoId = 1, sentenceIndex = 1, text = "Second", startTimeMs = 1000L, endTimeMs = 2000L)
        )
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(sentences)

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        viewModel.loadSentences(1L)
        advanceUntilIdle()

        // Navigate forward then back
        viewModel.nextSentence()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.currentSentenceIndex)

        viewModel.previousSentence()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentSentenceIndex)
    }

    @Test
    fun `previousSentence 在开头时不做任何操作`() = runTest {
        val sentences = listOf(
            ShadowingSentence(id = 1, videoId = 1, sentenceIndex = 0, text = "Only", startTimeMs = 0L, endTimeMs = 1000L)
        )
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(sentences)

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        viewModel.loadSentences(1L)
        advanceUntilIdle()

        viewModel.previousSentence()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentSentenceIndex)
    }

    @Test
    fun `nextSentence 当有下一句时导航`() = runTest {
        val sentences = listOf(
            ShadowingSentence(id = 1, videoId = 1, sentenceIndex = 0, text = "First", startTimeMs = 0L, endTimeMs = 1000L),
            ShadowingSentence(id = 2, videoId = 1, sentenceIndex = 1, text = "Second", startTimeMs = 1000L, endTimeMs = 2000L)
        )
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(sentences)

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        viewModel.loadSentences(1L)
        advanceUntilIdle()

        viewModel.nextSentence()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.currentSentenceIndex)
    }

    @Test
    fun `nextSentence 在末尾时不做任何操作`() = runTest {
        val sentences = listOf(
            ShadowingSentence(id = 1, videoId = 1, sentenceIndex = 0, text = "Only", startTimeMs = 0L, endTimeMs = 1000L)
        )
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(sentences)

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        viewModel.loadSentences(1L)
        advanceUntilIdle()

        viewModel.nextSentence()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentSentenceIndex)
    }

    @Test
    fun `jumpToSentence 跳转到有效索引`() = runTest {
        val sentences = listOf(
            ShadowingSentence(id = 1, videoId = 1, sentenceIndex = 0, text = "A", startTimeMs = 0L, endTimeMs = 1000L),
            ShadowingSentence(id = 2, videoId = 1, sentenceIndex = 1, text = "B", startTimeMs = 1000L, endTimeMs = 2000L),
            ShadowingSentence(id = 3, videoId = 1, sentenceIndex = 2, text = "C", startTimeMs = 2000L, endTimeMs = 3000L)
        )
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(sentences)

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        viewModel.loadSentences(1L)
        advanceUntilIdle()

        viewModel.jumpToSentence(2)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.currentSentenceIndex)

        viewModel.jumpToSentence(0)
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentSentenceIndex)
    }

    @Test
    fun `jumpToSentence 越界时不改变索引`() = runTest {
        val sentences = listOf(
            ShadowingSentence(id = 1, videoId = 1, sentenceIndex = 0, text = "A", startTimeMs = 0L, endTimeMs = 1000L)
        )
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(sentences)

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        viewModel.loadSentences(1L)
        advanceUntilIdle()

        viewModel.jumpToSentence(5)
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentSentenceIndex)

        viewModel.jumpToSentence(-1)
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentSentenceIndex)
    }

    @Test
    fun `切换句子时重置录音状态`() = runTest {
        val sentences = listOf(
            ShadowingSentence(id = 1, videoId = 1, sentenceIndex = 0, text = "First", startTimeMs = 0L, endTimeMs = 1000L),
            ShadowingSentence(id = 2, videoId = 1, sentenceIndex = 1, text = "Second", startTimeMs = 1000L, endTimeMs = 2000L)
        )
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(sentences)

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        viewModel.loadSentences(1L)
        advanceUntilIdle()

        viewModel.startRecording()
        advanceTimeBy(200)
        viewModel.stopRecording()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasRecording)

        viewModel.nextSentence()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hasRecording)
        assertEquals(RecordingState.IDLE, viewModel.uiState.value.recordingState)
    }

    @Test
    fun `playOriginalSentence 切换到原声源`() = runTest {
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(emptyList())

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        viewModel.playOriginalSentence()
        assertEquals(AudioSource.ORIGINAL, viewModel.uiState.value.activeAudioSource)
    }

    @Test
    fun `playUserRecording 切换到录音源`() = runTest {
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(emptyList())

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        viewModel.playUserRecording()
        assertEquals(AudioSource.RECORDING, viewModel.uiState.value.activeAudioSource)
    }

    @Test
    fun `录制状态转换完整流程`() = runTest {
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(emptyList())

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)

        // IDLE -> RECORDING
        viewModel.startRecording()
        advanceTimeBy(200)
        assertEquals(RecordingState.RECORDING, viewModel.uiState.value.recordingState)
        assertTrue(viewModel.uiState.value.isRecording)

        // RECORDING -> PLAYBACK
        viewModel.stopRecording()
        advanceUntilIdle()
        assertEquals(RecordingState.PLAYBACK, viewModel.uiState.value.recordingState)
        assertTrue(viewModel.uiState.value.hasRecording)

        // PLAYBACK -> IDLE (delete)
        viewModel.deleteRecording()
        advanceUntilIdle()
        assertEquals(RecordingState.IDLE, viewModel.uiState.value.recordingState)
        assertFalse(viewModel.uiState.value.hasRecording)
    }

    @Test
    fun `togglePlayPause 切换播放暂停状态`() = runTest {
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(emptyList())

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        assertFalse(viewModel.uiState.value.isPlayingVideo)

        viewModel.togglePlayPause()
        assertTrue(viewModel.uiState.value.isPlayingVideo)

        viewModel.togglePlayPause()
        assertFalse(viewModel.uiState.value.isPlayingVideo)
    }

    @Test
    fun `setPlaybackSpeed 设置播放速度`() = runTest {
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(emptyList())

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        assertEquals(1.0f, viewModel.uiState.value.playbackSpeed)

        viewModel.setPlaybackSpeed(1.5f)
        assertEquals(1.5f, viewModel.uiState.value.playbackSpeed)
    }

    @Test
    fun `seekForward 和 seekBackward`() = runTest {
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(emptyList())

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)

        // Start at 0
        assertEquals(0L, viewModel.uiState.value.currentPositionMs)

        // Seek forward 10s
        viewModel.seekForward()
        assertEquals(10000L, viewModel.uiState.value.currentPositionMs)

        // Seek forward another 10s
        viewModel.seekForward()
        assertEquals(20000L, viewModel.uiState.value.currentPositionMs)

        // Seek backward 10s
        viewModel.seekBackward()
        assertEquals(10000L, viewModel.uiState.value.currentPositionMs)

        // Seek backward past 0 should clamp
        viewModel.seekBackward()
        viewModel.seekBackward()
        assertEquals(0L, viewModel.uiState.value.currentPositionMs)
    }

    @Test
    fun `onPlaybackPositionChanged 自动更新当前句子`() = runTest {
        val sentences = listOf(
            ShadowingSentence(id = 1, videoId = 1, sentenceIndex = 0, text = "First", startTimeMs = 0L, endTimeMs = 5000L),
            ShadowingSentence(id = 2, videoId = 1, sentenceIndex = 1, text = "Second", startTimeMs = 5000L, endTimeMs = 10000L),
            ShadowingSentence(id = 3, videoId = 1, sentenceIndex = 2, text = "Third", startTimeMs = 10000L, endTimeMs = 15000L)
        )
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(sentences)

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)
        viewModel.loadSentences(1L)
        advanceUntilIdle()

        // Initial: sentence 0
        assertEquals(0, viewModel.uiState.value.currentSentenceIndex)

        // Position 6000ms → should be sentence 1
        viewModel.onPlaybackPositionChanged(6000L)
        assertEquals(1, viewModel.uiState.value.currentSentenceIndex)

        // Position 12000ms → should be sentence 2
        viewModel.onPlaybackPositionChanged(12000L)
        assertEquals(2, viewModel.uiState.value.currentSentenceIndex)

        // Position 2000ms → back to sentence 0
        viewModel.onPlaybackPositionChanged(2000L)
        assertEquals(0, viewModel.uiState.value.currentSentenceIndex)
    }

    @Test
    fun `findSentenceAtPosition 返回正确句子索引`() = runTest {
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(emptyList())
        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository)

        // Test with no sentences
        assertEquals(-1, viewModel.findSentenceAtPosition(0L))
    }
}
