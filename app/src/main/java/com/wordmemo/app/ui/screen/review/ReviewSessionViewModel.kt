package com.wordmemo.app.ui.screen.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.data.local.WordMemoDatabase
import com.wordmemo.app.data.local.mapper.toDomain
import com.wordmemo.app.data.local.entity.FlashcardEntity
import com.wordmemo.app.data.repository.ReviewRepositoryImpl
import com.wordmemo.app.domain.fsrs.FSRSAlgorithm
import com.wordmemo.app.domain.fsrs.FSRSFlashcard
import com.wordmemo.app.domain.fsrs.FSRSState
import com.wordmemo.app.domain.fsrs.Rating
import com.wordmemo.app.domain.model.Flashcard
import com.wordmemo.app.domain.model.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReviewUiState(
    val currentCard: Flashcard? = null,
    val currentWord: Word? = null,
    val isFlipped: Boolean = false,
    val totalCards: Int = 0,
    val reviewedCount: Int = 0,
    val isSessionComplete: Boolean = false,
    val isLoading: Boolean = true,
    val message: String? = null
)

class ReviewSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WordMemoDatabase.getInstance(application)
    private val flashcardDao = db.flashcardDao()
    private val reviewLogDao = db.reviewLogDao()
    private val wordDao = db.wordDao()
    private val reviewRepo = ReviewRepositoryImpl(flashcardDao, reviewLogDao)
    private val fsrs = FSRSAlgorithm()

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val cardQueue = mutableListOf<Flashcard>()

    fun startSession() {
        viewModelScope.launch {
            _uiState.value = ReviewUiState(isLoading = true)
            val cards = withContext(Dispatchers.IO) {
                flashcardDao.getDueCards(System.currentTimeMillis(), 30).map { it.toDomain() }
            }
            cardQueue.clear()
            cardQueue.addAll(cards)

            if (cards.isEmpty()) {
                _uiState.value = ReviewUiState(
                    isLoading = false,
                    isSessionComplete = true,
                    message = "🎉 没有待复习的单词！"
                )
                return@launch
            }
            loadCard(0, cards.size)
        }
    }

    private suspend fun loadCard(index: Int, total: Int) {
        if (index >= cardQueue.size) {
            _uiState.value = ReviewUiState(
                isSessionComplete = true,
                reviewedCount = index,
                totalCards = total,
                message = "🎉 复习完成！共复习了 $index 个单词"
            )
            return
        }
        val card = cardQueue[index]
        val word = withContext(Dispatchers.IO) {
            wordDao.getById(card.wordId)?.toDomain()
        }
        _uiState.value = ReviewUiState(
            currentCard = card,
            currentWord = word,
            isFlipped = false,
            totalCards = total,
            reviewedCount = index,
            isLoading = false
        )
    }

    fun flipCard() {
        _uiState.value = _uiState.value.copy(isFlipped = !_uiState.value.isFlipped)
    }

    fun rateCard(rating: Int) {
        val card = _uiState.value.currentCard ?: return
        val reviewedCount = _uiState.value.reviewedCount
        val total = _uiState.value.totalCards
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val r = when (rating) { 1 -> com.wordmemo.app.domain.fsrs.v6.Rating.Again
                    2 -> com.wordmemo.app.domain.fsrs.v6.Rating.Hard
                    3 -> com.wordmemo.app.domain.fsrs.v6.Rating.Good
                    else -> com.wordmemo.app.domain.fsrs.v6.Rating.Easy }
                val phase = when (card.state) {
                    "NEW" -> 0; "LEARNING" -> 1; "RELEARNING" -> 1; else -> 2
                }
                val interval = (card.scheduledDays.toInt()).coerceAtLeast(0)
                val fsrsCard = com.wordmemo.app.domain.fsrs.v6.FSRSFlashCard(
                    stability = card.stability.coerceAtLeast(0.1),
                    difficulty = card.difficulty.coerceIn(1.0, 10.0),
                    interval = interval,
                    reviewCount = card.reps,
                    phase = phase
                )
                val grades = com.wordmemo.app.domain.fsrs.v6.FSRS().calculate(fsrsCard)
                val g = grades.first { it.choice == r }
                val newStability = g.stability.coerceAtLeast(0.1)
                val newDifficulty = g.difficulty.coerceIn(1.0, 10.0)
                val newDue = now + g.durationMillis
                val newState = when (g.choice) {
                    com.wordmemo.app.domain.fsrs.v6.Rating.Easy -> if (phase <= 1) "REVIEW" else "REVIEW"
                    com.wordmemo.app.domain.fsrs.v6.Rating.Good -> if (phase <= 1 && g.interval <= 1) "LEARNING" else "REVIEW"
                    else -> if (phase <= 1) "LEARNING" else "RELEARNING"
                }
                val entity = FlashcardEntity(
                    id = card.id, wordId = card.wordId,
                    state = newState, stability = newStability,
                    difficulty = newDifficulty, due = newDue,
                    elapsedDays = 0.0, scheduledDays = g.interval.toDouble(),
                    reps = card.reps + 1, lapses = if (r.value <= 2) card.lapses + 1 else card.lapses,
                    lastReview = now
                )
                withContext(Dispatchers.IO) { flashcardDao.update(entity) }
                val logEntity = com.wordmemo.app.data.local.entity.ReviewLogEntity(
                    cardId = card.id, rating = r.value,
                    reviewedAt = now, stabilityBefore = card.stability,
                    difficultyBefore = card.difficulty, stabilityAfter = newStability,
                    difficultyAfter = newDifficulty
                )
                withContext(Dispatchers.IO) { reviewLogDao.insert(logEntity) }
                loadCard(reviewedCount + 1, total)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = "评分失败: ${e.message}")
            }
        }
    }
}
