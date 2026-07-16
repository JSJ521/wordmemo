package com.wordmemo.app.ui.screen.wordlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.domain.model.Group
import com.wordmemo.app.domain.model.Word
import com.wordmemo.app.domain.repository.GroupRepository
import com.wordmemo.app.domain.repository.ReviewRepository
import com.wordmemo.app.domain.repository.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WordListUiState(
    val words: List<Word> = emptyList(),
    val groups: List<Group> = emptyList(),
    val selectedGroupId: Long? = null,
    val searchQuery: String = "",
    val dueCount: Int = 0,
    val masteredWordIds: Set<Long> = emptySet(),
    val showMasteredOnly: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class WordListViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    private val groupRepository: GroupRepository,
    private val reviewRepository: ReviewRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedGroupId = MutableStateFlow<Long?>(null)

    private val _uiState = MutableStateFlow(WordListUiState())
    val uiState: StateFlow<WordListUiState> = _uiState.asStateFlow()

    init {
        // Observe words
        viewModelScope.launch {
            combine(
                wordRepository.observeAll(),
                groupRepository.observeAll(),
                _selectedGroupId,
                _searchQuery,
                reviewRepository.observeDueCount()
            ) { words, groups, groupId, query, dueCount ->
                val filtered = when {
                    groupId != null -> words.filter { word ->
                        // words are already filtered by group via observeByGroup
                        words.any { it.id == word.id }
                    }
                    query.isNotBlank() -> words.filter {
                        it.english.contains(query, ignoreCase = true) ||
                                it.chinese.contains(query, ignoreCase = true)
                    }
                    else -> words
                }
                WordListUiState(
                    words = if (groupId != null) {
                        wordRepository.observeByGroup(groupId).first()
                    } else if (query.isNotBlank()) {
                        wordRepository.search(query)
                    } else {
                        words
                    },
                    groups = groups,
                    selectedGroupId = groupId,
                    searchQuery = query,
                    dueCount = dueCount,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }

        // Load due count and mastered words
        viewModelScope.launch {
            combine(
                reviewRepository.observeDueCount(),
                kotlinx.coroutines.flow.flow { emit(reviewRepository.getMasteredWordIds()) }
            ) { dueCount, masteredIds ->
                _uiState.update { it.copy(
                    dueCount = dueCount,
                    masteredWordIds = masteredIds.toSet()
                )}
            }.collect()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onGroupSelected(groupId: Long?) {
        _selectedGroupId.value = groupId
    }

    fun deleteWord(wordId: Long) {
        viewModelScope.launch {
            wordRepository.delete(wordId)
        }
    }

    fun toggleMasteredFilter() {
        _uiState.update { it.copy(showMasteredOnly = !it.showMasteredOnly) }
    }
}
