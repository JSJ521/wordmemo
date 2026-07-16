package com.wordmemo.app

import com.wordmemo.app.domain.usecase.review.CheckDailyLimitUseCase
import com.wordmemo.app.domain.usecase.review.DailyLimitResult
import com.wordmemo.app.domain.repository.ReviewRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * CheckDailyLimitUseCase 单元测试。
 * 验证每日新增上限控制逻辑。
 */
class CheckDailyLimitUseCaseTest {

    private val reviewRepository = mockk<ReviewRepository>()
    private lateinit var useCase: CheckDailyLimitUseCase

    @Before
    fun setup() {
        useCase = CheckDailyLimitUseCase(reviewRepository)
        useCase.setDailyLimit(13) // 默认
    }

    @Test
    fun `when today count is below limit returns Allowed`() = runBlocking {
        coEvery { reviewRepository.countTodayNewCards() } returns 5

        val result = useCase()

        assertTrue("Should be allowed when under limit", result is DailyLimitResult.Allowed)
        assertEquals("Remaining should be 13 - 5 = 8", 8, (result as DailyLimitResult.Allowed).remaining)
    }

    @Test
    fun `when today count equals limit returns Rejected`() = runBlocking {
        coEvery { reviewRepository.countTodayNewCards() } returns 13

        val result = useCase()

        assertTrue("Should be rejected when at limit", result is DailyLimitResult.Rejected)
    }

    @Test
    fun `when today count exceeds limit returns Rejected`() = runBlocking {
        coEvery { reviewRepository.countTodayNewCards() } returns 15

        val result = useCase()

        assertTrue("Should be rejected when over limit", result is DailyLimitResult.Rejected)
    }

    @Test
    fun `when limit is 0 all attempts are rejected`() = runBlocking {
        useCase.setDailyLimit(0)
        coEvery { reviewRepository.countTodayNewCards() } returns 0

        val result = useCase()

        assertTrue("Should be rejected when limit is 0", result is DailyLimitResult.Rejected)
    }

    @Test
    fun `when limit is increased new cards become allowed`() = runBlocking {
        useCase.setDailyLimit(20)
        coEvery { reviewRepository.countTodayNewCards() } returns 13

        val result = useCase()

        assertTrue("Should be allowed after increasing limit", result is DailyLimitResult.Allowed)
        assertEquals("Remaining should be 20 - 13 = 7", 7, (result as DailyLimitResult.Allowed).remaining)
    }

    @Test
    fun `getDailyLimit returns current limit`() {
        assertEquals(13, useCase.getDailyLimit())
        useCase.setDailyLimit(25)
        assertEquals(25, useCase.getDailyLimit())
    }

    @Test
    fun `setDailyLimit clamps to minimum of 0`() {
        useCase.setDailyLimit(-1)
        assertEquals(0, useCase.getDailyLimit())
    }

    @Test
    fun `setOptimizeThreshold clamps to minimum of 5`() {
        useCase.setOptimizeThreshold(1)
        assertEquals(5, useCase.getOptimizeThreshold())
    }

    @Test
    fun `optimize threshold defaults to 20`() {
        assertEquals(20, useCase.getOptimizeThreshold())
    }
}
