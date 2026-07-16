package com.wordmemo.app

import com.wordmemo.app.domain.fsrs.FSRSAlgorithm
import com.wordmemo.app.domain.fsrs.FSRSDefaults
import com.wordmemo.app.domain.fsrs.FSRSFlashcard
import com.wordmemo.app.domain.fsrs.FSRSReviewLog
import com.wordmemo.app.domain.fsrs.FSRSOptimizer
import com.wordmemo.app.domain.fsrs.FSRSState
import com.wordmemo.app.domain.fsrs.Rating
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * FSRS 算法核心逻辑单元测试。
 * 验证 FSRS v4.5 调度算法的正确性。
 */
class FSRSAlgorithmTest {

    private val algorithm = FSRSAlgorithm()

    @Test
    fun `schedule with NEW card and GOOD rating returns LEARNING state`() {
        val card = FSRSFlashcard(state = FSRSState.NEW)
        val result = algorithm.schedule(card, Rating.GOOD)

        assertEquals(FSRSState.LEARNING, result.card.state)
        assertTrue("Stability should be positive after first review", result.card.stability > 0)
        assertNotNull("Review log should be created", result.reviewLog)
        assertEquals(Rating.GOOD, result.reviewLog.rating)
    }

    @Test
    fun `schedule with NEW card and EASY rating returns REVIEW state`() {
        val card = FSRSFlashcard(state = FSRSState.NEW)
        val result = algorithm.schedule(card, Rating.EASY)

        assertEquals(FSRSState.REVIEW, result.card.state)
    }

    @Test
    fun `schedule with LEARNING card and GOOD rating promotes to REVIEW`() {
        val card = FSRSFlashcard(
            state = FSRSState.LEARNING,
            stability = 1.0,
            difficulty = 5.0,
            lastReview = System.currentTimeMillis() - 3600000 // 1 hour ago
        )
        val result = algorithm.schedule(card, Rating.GOOD)

        assertEquals(FSRSState.REVIEW, result.card.state)
        assertTrue("Review stability should be higher", result.card.stability >= card.stability)
    }

    @Test
    fun `schedule with REVIEW card and AGAIN rating returns RELEARNING`() {
        val card = FSRSFlashcard(
            state = FSRSState.REVIEW,
            stability = 10.0,
            difficulty = 5.0,
            lastReview = System.currentTimeMillis() - 86400000 // 1 day ago
        )
        val result = algorithm.schedule(card, Rating.AGAIN)

        assertEquals(FSRSState.RELEARNING, result.card.state)
        assertEquals("Lapses should increment", card.lapses + 1, result.card.lapses)
    }

    @Test
    fun `schedule increments reps counter`() {
        val card = FSRSFlashcard(state = FSRSState.NEW, reps = 0)
        val result = algorithm.schedule(card, Rating.GOOD)

        assertEquals(1, result.card.reps)
    }

    @Test
    fun `AGAIN rating produces higher difficulty than EASY`() {
        val card = FSRSFlashcard(
            state = FSRSState.REVIEW,
            stability = 5.0,
            difficulty = 5.0,
            lastReview = System.currentTimeMillis() - 86400000
        )

        val againResult = algorithm.schedule(card, Rating.AGAIN)
        val easyResult = algorithm.schedule(card, Rating.EASY)

        assertTrue(
            "AGAIN should result in higher difficulty than EASY",
            againResult.card.difficulty > easyResult.card.difficulty
        )
    }

    @Test
    fun `retrievability decreases with elapsed time`() {
        val r1 = algorithm.computeRetrievability(10.0, 0.0) // Just reviewed
        val r2 = algorithm.computeRetrievability(10.0, 30.0) // 30 days later

        assertTrue("Retrievability at time 0 should be 1.0", r1 >= 0.99)
        assertTrue("Retrievability should decrease over time", r1 > r2)
    }

    @Test
    fun `FSRS defaults should be 19 parameters`() {
        assertEquals(19, FSRSDefaults.DEFAULT_PARAMS.size)
    }

    @Test
    fun `due time should be in the future after scheduling`() {
        val now = System.currentTimeMillis()
        val card = FSRSFlashcard(
            state = FSRSState.NEW,
            due = now
        )

        val result = algorithm.schedule(card, Rating.GOOD, now)

        assertTrue(
            "Next review due should be in the future",
            result.card.due >= now
        )
    }

    @Test
    fun `schedule preserves review log history`() {
        val card = FSRSFlashcard(
            state = FSRSState.REVIEW,
            stability = 8.0,
            difficulty = 4.5,
            lastReview = System.currentTimeMillis() - 86400000
        )

        val result = algorithm.schedule(card, Rating.GOOD)

        val log = result.reviewLog
        assertEquals(card.id, log.cardId)
        assertEquals(Rating.GOOD, log.rating)
        assertEquals(card.stability, log.stabilityBefore, 0.001)
        assertEquals(card.difficulty, log.difficultyBefore, 0.001)
        assertTrue("Post-review stability should be tracked", log.stabilityAfter > 0)
    }

    @Test
    fun `short term stability calculation handles delta days`() {
        val card = FSRSFlashcard(
            state = FSRSState.LEARNING,
            stability = 0.5,
            difficulty = 5.0,
            lastReview = System.currentTimeMillis() - 1800000 // 30 min ago
        )

        val result = algorithm.schedule(card, Rating.GOOD)
        assertTrue("Short term stability should be positive", result.card.stability > 0)
    }

    @Test
    fun `multiple schedules maintain stability growth trajectory`() {
        var card = FSRSFlashcard(state = FSRSState.NEW)
        val now = System.currentTimeMillis()

        // Simulate 5 successive GOOD reviews
        for (i in 1..5) {
            val result = algorithm.schedule(card, Rating.GOOD, now + (i * 86400000L))
            card = result.card
        }

        assertTrue(
            "Stability should grow with successive GOOD ratings",
            card.stability > 1.0
        )
        assertEquals(FSRSState.REVIEW, card.state)
        assertEquals(5, card.reps)
    }
}
