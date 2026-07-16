package com.wordmemo.app.ui.screen.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.data.local.WordMemoDatabase
import com.wordmemo.app.domain.model.Stats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StatsUiState(
    val stats: Stats = Stats(),
    val isLoading: Boolean = true
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WordMemoDatabase.getInstance(application)
    private val flashcardDao = db.flashcardDao()
    private val wordDao = db.wordDao()
    private val reviewLogDao = db.reviewLogDao()

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val words = withContext(Dispatchers.IO) { wordDao.observeAll().first() }
                val due = withContext(Dispatchers.IO) {
                    flashcardDao.countDue(System.currentTimeMillis())
                }
                val mastered = withContext(Dispatchers.IO) { flashcardDao.countMastered() }
                val totalReviews = withContext(Dispatchers.IO) { reviewLogDao.countTotal() }
                val todayStart = withContext(Dispatchers.IO) {
                    val cal = java.util.Calendar.getInstance()
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    cal.set(java.util.Calendar.SECOND, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                    cal.timeInMillis
                }
                val todayEnd = todayStart + 86400000L
                val todayReviews = withContext(Dispatchers.IO) {
                    reviewLogDao.countTodayReviews(todayStart, todayEnd)
                }
                val todayNewCards = withContext(Dispatchers.IO) {
                    reviewLogDao.countTodayNewCards(todayStart, todayEnd)
                }
                _uiState.value = StatsUiState(
                    stats = Stats(
                        totalWords = words.size,
                        dueCards = due,
                        masteredWords = mastered,
                        totalReviews = totalReviews,
                        todayReviews = todayReviews,
                        todayNewCards = todayNewCards
                    ),
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = StatsUiState(isLoading = false)
            }
        }
    }
}
