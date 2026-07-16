package com.wordmemo.app

import com.wordmemo.app.domain.fsrs.FSRSFlashcard
import com.wordmemo.app.domain.fsrs.FSRSState
import com.wordmemo.app.domain.fsrs.Rating
import com.wordmemo.app.domain.usecase.review.RateCardUseCase
import com.wordmemo.app.domain.fsrs.FSRSAlgorithm
import com.wordmemo.app.domain.fsrs.FSRSOptimizer
import com.wordmemo.app.domain.model.Flashcard
import com.wordmemo.app.domain.model.ReviewLog
import com.wordmemo.app.domain.repository.ReviewRepository
import com.wordmemo.app.domain.usecase.review.CheckDailyLimitUseCase
import com.wordmemo.app.domain.usecase.review.DailyLimitResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * RateCardUseCase 集成测试。
 * 验证复习评分流程的端到端逻辑。
 */
class RateCardUseCaseTest {

    private val reviewRepository = mockk<ReviewRepository>()
    private val fsrsAlgorithm = FSRSAlgorithm()
    private val fsrsOptimizer = FSRSOptimizer()
    private val checkDailyLimit = mockk<CheckDailyLimitUseCase>()

    private val useCase = RateCardUseCase(
        reviewRepository = reviewRepository,
        fsrsAlgorithm = fsrsAlgorithm,
        fsrsOptimizer = fsrsOptimizer,
        checkDailyLimit = checkDailyLimit
    )

    @Test
    fun `rate card with GOOD rating updates card state and saves log`() = runBlocking {
        val card = Flashcard(
            id = 1,
            wordId = 1,
            state = FSRSState.NEW.value,
            due = System.currentTimeMillis()
        )

        coEvery { reviewRepository.saveCard(any()) } returns Unit
        coEvery { reviewRepository.saveReviewLog(any()) } returns Unit
        coEvery { reviewRepository.getRecentLogs(any()) } returns emptyList()

        val result = useCase(card, Rating.GOOD.value)

        assertNotNull("Result should not be null", result)
        assertEquals(
            "Card state should transition from New",
            FSRSState.LEARNING.value,
            result.updatedCard.state
        )
        assertTrue("Stability should improve", result.updatedCard.stability > 0)
        assertEquals(
            "Review log should reflect GOOD rating",
            Rating.GOOD.value,
            result.reviewLog.rating
        )

        coVerify { reviewRepository.saveCard(any()) }
        coVerify { reviewRepository.saveReviewLog(any()) }
    }

    @Test
    fun `rate card with AGAIN rating increments lapses`() = runBlocking {
        val card = Flashcard(
            id = 2,
            wordId = 2,
            state = FSRSState.REVIEW.value,
            stability = 10.0,
            difficulty = 5.0,
            lapses = 0,
            lastReview = System.currentTimeMillis() - 86400000
        )

        coEvery { reviewRepository.saveCard(any()) } returns Unit
        coEvery { reviewRepository.saveReviewLog(any()) } returns Unit
        coEvery { reviewRepository.getRecentLogs(any()) } returns emptyList()

        val result = useCase(card, Rating.AGAIN.value)

        assertEquals("Lapses should increment by 1", card.lapses + 1, result.updatedCard.lapses)
        assertEquals(
            "State should become RELEARNING",
            FSRSState.RELEARNING.value,
            result.updatedCard.state
        )
    }

    @Test
    fun `rate card stores stability and difficulty deltas in review log`() = runBlocking {
        val card = Flashcard(
            id = 3,
            wordId = 3,
            state = FSRSState.REVIEW.value,
            stability = 8.0,
            difficulty = 5.0,
            lastReview = System.currentTimeMillis() - 86400000
        )

        var savedLog: ReviewLog? = null
        coEvery { reviewRepository.saveCard(any()) } returns Unit
        coEvery { reviewRepository.saveReviewLog(any()) } answers {
            savedLog = firstArg()
        }
        coEvery { reviewRepository.getRecentLogs(any()) } returns emptyList()

        val result = useCase(card, Rating.GOOD.value)

        assertEquals(card.stability, result.reviewLog.stabilityBefore, 0.001)
        assertEquals(card.difficulty, result.reviewLog.difficultyBefore, 0.001)
        assertTrue("stabilityAfter should be greater than stabilityBefore",
            result.reviewLog.stabilityAfter > result.reviewLog.stabilityBefore)
    }
}
