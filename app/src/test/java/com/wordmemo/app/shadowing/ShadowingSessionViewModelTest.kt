package com.wordmemo.app.shadowing

import com.wordmemo.app.data.shadowing.service.AudioWaveformExtractor
import com.wordmemo.app.data.shadowing.service.SentenceAudioRecorder
import com.wordmemo.app.data.shadowing.service.VideoImportService
import com.wordmemo.app.domain.shadowing.model.ShadowingSentence
import com.wordmemo.app.domain.shadowing.repository.ShadowingRepository
import com.wordmemo.app.domain.shadowing.usecase.GetSentencesUseCase
import com.wordmemo.app.ui.screen.shadowing.AudioSource
import com.wordmemo.app.ui.screen.shadowing.RecordingState
import com.wordmemo.app.ui.screen.shadowing.ShadowingSessionViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * ShadowingSessionViewModel 单元测试 — 验证录音状态管理、句子导航、UI State 变化。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShadowingSessionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val getSentencesUseCase = mockk<GetSentencesUseCase>()
    private val shadowingRepository = mockk<ShadowingRepository>()
    private val videoImportService = mockk<VideoImportService>()
    private val sentenceAudioRecorder = mockk<SentenceAudioRecorder>()
    private val audioWaveformExtractor = mockk<AudioWaveformExtractor>()
    private val context = RuntimeEnvironment.getApplication()
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

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)

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

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
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

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
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

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
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
        val sentences = listOf(
            ShadowingSentence(id = 1, videoId = 1, sentenceIndex = 0, text = "Hello", startTimeMs = 0L, endTimeMs = 1000L)
        )
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(sentences)
        coEvery { shadowingRepository.getVideoById(any()) } returns null

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
        viewModel.loadSentences(1L)
        advanceUntilIdle()

        every { sentenceAudioRecorder.startRecording(any(), any()) } returns "/tmp/test_recording.mp3"
        every { sentenceAudioRecorder.getMaxAmplitude() } returns 500

        viewModel.startRecording()
        advanceTimeBy(200)

        val state = viewModel.uiState.value
        assertEquals(RecordingState.RECORDING, state.recordingState)
        assertTrue(state.isRecording)
        assertTrue("录音中应有波形条", state.waveformAmplitudes.isNotEmpty())
        viewModel.stopRecording()
        advanceUntilIdle()
    }

    @Test
    fun `stopRecording 切换到PLAYBACK状态`() = runTest {
        val sentences = listOf(
            ShadowingSentence(id = 1, videoId = 1, sentenceIndex = 0, text = "Hello", startTimeMs = 0L, endTimeMs = 1000L)
        )
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(sentences)
        coEvery { shadowingRepository.getVideoById(any()) } returns null
        every { sentenceAudioRecorder.startRecording(any(), any()) } returns "/tmp/test_recording.mp3"
        every { sentenceAudioRecorder.stopRecording() } returns Pair("/tmp/test_recording.mp3", 1500L)
        every { audioWaveformExtractor.extractFromFile(any()) } returns List(60) { 0.5f }

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
        viewModel.loadSentences(1L)
        advanceUntilIdle()

        viewModel.startRecording()
        viewModel.stopRecording()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(RecordingState.PLAYBACK, state.recordingState)
        assertFalse(state.isRecording)
        assertTrue(state.hasRecording)
        assertTrue("录音波形应有数据", state.recordedWaveform.isNotEmpty())
    }

    @Test
    fun `deleteRecording 重置为IDLE`() = runTest {
        val sentences = listOf(
            ShadowingSentence(id = 1, videoId = 1, sentenceIndex = 0, text = "Hello", startTimeMs = 0L, endTimeMs = 1000L)
        )
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(sentences)
        coEvery { shadowingRepository.getVideoById(any()) } returns null
        every { sentenceAudioRecorder.startRecording(any(), any()) } returns "/tmp/test_recording.mp3"
        every { sentenceAudioRecorder.stopRecording() } returns Pair("/tmp/test_recording.mp3", 1500L)
        every { audioWaveformExtractor.extractFromFile(any()) } returns List(60) { 0.5f }
        every { sentenceAudioRecorder.deleteRecordingForSentence(any(), any()) } returns Unit

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
        viewModel.loadSentences(1L)
        advanceUntilIdle()

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
        val sentences = listOf(
            ShadowingSentence(id = 1, videoId = 1, sentenceIndex = 0, text = "Hello", startTimeMs = 0L, endTimeMs = 1000L)
        )
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(sentences)
        coEvery { shadowingRepository.getVideoById(any()) } returns null
        every { sentenceAudioRecorder.startRecording(any(), any()) } returns "/tmp/test_recording.mp3"
        every { sentenceAudioRecorder.stopRecording() } returns Pair("/tmp/test_recording.mp3", 1500L)
        every { audioWaveformExtractor.extractFromFile(any()) } returns List(60) { 0.5f }

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
        viewModel.loadSentences(1L)
        advanceUntilIdle()

        viewModel.startRecording()
        viewModel.stopRecording()
        advanceUntilIdle()

        viewModel.playRecording()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isPlayingRecording)
        assertEquals(RecordingState.PLAYBACK, state.recordingState)
    }

    @Test
    fun `switchAudioSource 切换音频源`() = runTest {
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(emptyList())

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
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

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
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

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
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

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
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

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
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

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
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

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
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
        coEvery { shadowingRepository.getVideoById(any()) } returns null
        every { sentenceAudioRecorder.startRecording(any(), any()) } returns "/tmp/test_recording.mp3"
        every { sentenceAudioRecorder.stopRecording() } returns Pair("/tmp/test_recording.mp3", 1500L)
        every { audioWaveformExtractor.extractFromFile(any()) } returns List(60) { 0.5f }

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
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

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
        viewModel.playOriginalSentence()
        assertEquals(AudioSource.ORIGINAL, viewModel.uiState.value.activeAudioSource)
    }

    @Test
    fun `playUserRecording 切换到录音源`() = runTest {
        val sentences = listOf(
            ShadowingSentence(id = 1, videoId = 1, sentenceIndex = 0, text = "Hello", startTimeMs = 0L, endTimeMs = 1000L)
        )
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(sentences)
        coEvery { shadowingRepository.getVideoById(any()) } returns null
        every { sentenceAudioRecorder.startRecording(any(), any()) } returns "/tmp/test_recording.mp3"
        every { sentenceAudioRecorder.stopRecording() } returns Pair("/tmp/test_recording.mp3", 1500L)
        every { audioWaveformExtractor.extractFromFile(any()) } returns List(60) { 0.5f }

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
        viewModel.loadSentences(1L)
        advanceUntilIdle()

        viewModel.startRecording()
        viewModel.stopRecording()
        advanceUntilIdle()

        viewModel.playUserRecording()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isPlayingRecording)
        assertEquals(AudioSource.RECORDING, viewModel.uiState.value.activeAudioSource)
    }

    @Test
    fun `togglePlayPause 切换播放暂停状态`() = runTest {
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(emptyList())

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
        assertFalse(viewModel.uiState.value.isPlayingVideo)

        viewModel.togglePlayPause()
        assertTrue(viewModel.uiState.value.isPlayingVideo)

        viewModel.togglePlayPause()
        assertFalse(viewModel.uiState.value.isPlayingVideo)
    }

    @Test
    fun `setPlaybackSpeed 设置播放速度`() = runTest {
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(emptyList())

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
        assertEquals(1.0f, viewModel.uiState.value.playbackSpeed)

        viewModel.setPlaybackSpeed(1.5f)
        assertEquals(1.5f, viewModel.uiState.value.playbackSpeed)
    }

    @Test
    fun `seekForward 和 seekBackward`() = runTest {
        coEvery { getSentencesUseCase.invoke(any()) } returns MutableStateFlow(emptyList())

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)

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

        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)
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
        viewModel = ShadowingSessionViewModel(getSentencesUseCase, shadowingRepository, videoImportService, sentenceAudioRecorder, audioWaveformExtractor, context)

        // Test with no sentences
        assertEquals(-1, viewModel.findSentenceAtPosition(0L))
    }
}
