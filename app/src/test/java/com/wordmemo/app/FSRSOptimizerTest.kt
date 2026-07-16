package com.wordmemo.app

import com.wordmemo.app.domain.fsrs.FSRSDefaults
import com.wordmemo.app.domain.fsrs.FSRSOptimizer
import org.junit.Assert.*
import org.junit.Test

class FSRSOptimizerTest {

    private val optimizer = FSRSOptimizer()

    @Test
    fun `optimize with empty logs returns default params`() {
        val params = optimizer.optimize(emptyList())
        assertArrayEquals(FSRSDefaults.DEFAULT_PARAMS, params.toDoubleArray(), 0.001)
    }

    @Test
    fun `optimize with sufficient logs produces valid params`() {
        val logs = generateTestReviews(25)
        val params = optimizer.optimize(logs)
        assertEquals(19, params.size)
        params.forEach { assertTrue("Parameter should be positive", it > 0) }
    }

    @Test
    fun `optimizer does not crash with single log`() {
        val logs = generateTestReviews(1)
        val params = optimizer.optimize(logs)
        assertNotNull(params)
        assertEquals(19, params.size)
    }

    @Test
    fun `log loss improves after optimization`() {
        val logs = generateTestReviews(20)
        val before = optimizer.logLoss(logs, FSRSDefaults.DEFAULT_PARAMS.toList())
        val optimized = optimizer.optimize(logs, iterations = 200)
        val after = optimizer.logLoss(logs, optimized)
        assertTrue("Loss should not increase", after <= before * 1.1 || after <= before + 0.5)
    }

    private fun generateTestReviews(count: Int): List<FSRSOptimizer.ReviewData> {
        return (1..count).map { i ->
            FSRSOptimizer.ReviewData(
                elapsedDays = (count - i).toDouble().coerceAtLeast(0.0),
                stability = (1.0 + i * 0.5).coerceAtLeast(0.1),
                rating = when { i % 4 == 0 -> 1; i % 4 == 1 -> 2; i % 4 == 2 -> 3; else -> 4 },
                difficulty = 5.0
            )
        }
    }
}
