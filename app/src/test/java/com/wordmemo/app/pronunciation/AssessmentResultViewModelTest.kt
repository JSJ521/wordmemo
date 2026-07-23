package com.wordmemo.app.pronunciation

import com.wordmemo.app.domain.pronunciation.model.AssessmentRecord
import com.wordmemo.app.domain.pronunciation.repository.PronunciationRepository
import com.wordmemo.app.ui.screen.pronunciation.AssessmentResultViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * AssessmentResultViewModel 单元测试 — 验证测评结果加载、维度分数计算。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssessmentResultViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val pronunciationRepository = mockk<PronunciationRepository>()
    private lateinit var viewModel: AssessmentResultViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `初始状态默认值正确`() {
        viewModel = AssessmentResultViewModel(pronunciationRepository)

        val state = viewModel.uiState.value
        assertNull(state.assessment)
        assertEquals(0, state.accuracyScore)
        assertEquals(0, state.fluencyScore)
        assertEquals(0, state.completenessScore)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `loadAssessment 加载成功时更新各维度分数`() = runTest {
        val assessment = AssessmentRecord(
            id = 1,
            sentenceText = "Hello world",
            overallScore = 90,
            scoreLevel = "优秀",
            assessmentType = "shadowing"
        )

        coEvery { pronunciationRepository.getById(1L) } returns assessment

        viewModel = AssessmentResultViewModel(pronunciationRepository)
        viewModel.loadAssessment(1L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.assessment)
        val a = state.assessment!!
        assertEquals(90, a.overallScore)
        // 根据 computeAccuracy(90) = 90 - 2 = 88
        assertEquals(88, state.accuracyScore)
        // 根据 computeFluency(90) = 90 - 8 = 82
        assertEquals(82, state.fluencyScore)
        // 根据 computeCompleteness(90) = 90 - 3 = 87
        assertEquals(87, state.completenessScore)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `loadAssessment moderate score 75`() = runTest {
        val assessment = AssessmentRecord(
            id = 2,
            sentenceText = "Some sentence",
            overallScore = 75,
            scoreLevel = "良好",
            assessmentType = "shadowing"
        )

        coEvery { pronunciationRepository.getById(2L) } returns assessment

        viewModel = AssessmentResultViewModel(pronunciationRepository)
        viewModel.loadAssessment(2L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // computeAccuracy(75) = 75 - 5 = 70
        assertEquals(70, state.accuracyScore)
        // computeFluency(75) = 75 - 10 = 65
        assertEquals(65, state.fluencyScore)
        // computeCompleteness(75) = 75 - 6 = 69
        assertEquals(69, state.completenessScore)
    }

    @Test
    fun `loadAssessment low score 50`() = runTest {
        val assessment = AssessmentRecord(
            id = 3,
            sentenceText = "Poor match",
            overallScore = 50,
            scoreLevel = "需改进",
            assessmentType = "shadowing"
        )

        coEvery { pronunciationRepository.getById(3L) } returns assessment

        viewModel = AssessmentResultViewModel(pronunciationRepository)
        viewModel.loadAssessment(3L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // computeAccuracy(50) = 50 (coerceIn 0..100)
        assertEquals(50, state.accuracyScore)
        // computeFluency(50) = 50 - 5 = 45
        assertEquals(45, state.fluencyScore)
        // computeCompleteness(50) = 50 - 2 = 48
        assertEquals(48, state.completenessScore)
    }

    @Test
    fun `loadAssessment 分数不越界`() = runTest {
        val assessment = AssessmentRecord(
            id = 4,
            sentenceText = "Perfect",
            overallScore = 100,
            scoreLevel = "优秀",
            assessmentType = "shadowing"
        )

        coEvery { pronunciationRepository.getById(4L) } returns assessment

        viewModel = AssessmentResultViewModel(pronunciationRepository)
        viewModel.loadAssessment(4L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("accuracyScore应在0-100范围内", state.accuracyScore in 0..100)
        assertTrue("fluencyScore应在0-100范围内", state.fluencyScore in 0..100)
        assertTrue("completenessScore应在0-100范围内", state.completenessScore in 0..100)
    }

    @Test
    fun `loadAssessment 记录不存在时设置错误`() = runTest {
        coEvery { pronunciationRepository.getById(999L) } returns null

        viewModel = AssessmentResultViewModel(pronunciationRepository)
        viewModel.loadAssessment(999L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.assessment)
        assertEquals("未找到测评记录", state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `loadAssessment 异常时设置错误`() = runTest {
        coEvery { pronunciationRepository.getById(any()) } throws RuntimeException("数据库错误")

        viewModel = AssessmentResultViewModel(pronunciationRepository)
        viewModel.loadAssessment(1L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.assessment)
        assertEquals("数据库错误", state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `loadAssessment 高分90时dimension scores较高`() = runTest {
        val assessment = AssessmentRecord(
            id = 5,
            sentenceText = "Good job",
            overallScore = 95,
            scoreLevel = "优秀",
            assessmentType = "shadowing"
        )

        coEvery { pronunciationRepository.getById(5L) } returns assessment

        viewModel = AssessmentResultViewModel(pronunciationRepository)
        viewModel.loadAssessment(5L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // 实际计算: accuracy(93) > completeness(92) > fluency(87)
        assertTrue("高分时accuracy应高于completeness", state.accuracyScore > state.completenessScore)
        assertTrue("高分时completeness应高于fluency", state.completenessScore > state.fluencyScore)
    }

    @Test
    fun `loadAssessment 设置loadState为true然后false`() = runTest {
        coEvery { pronunciationRepository.getById(1L) } returns AssessmentRecord(
            id = 1, sentenceText = "Test", overallScore = 80, scoreLevel = "良好", assessmentType = "shadowing"
        )

        viewModel = AssessmentResultViewModel(pronunciationRepository)
        viewModel.loadAssessment(1L)

        // 执行初始协程让isLoading=true生效
        runCurrent()

        // 刚启动时 isLoading 应为 true
        assertTrue(viewModel.uiState.value.isLoading)

        advanceUntilIdle()

        // 完成后 isLoading 应为 false
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
