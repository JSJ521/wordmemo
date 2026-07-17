package com.wordmemo.app.ui.screen.wordlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.domain.model.Group
import com.wordmemo.app.domain.model.Word
import com.wordmemo.app.domain.repository.GroupRepository
import com.wordmemo.app.domain.repository.ReviewRepository
import com.wordmemo.app.domain.repository.WordRepository
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlinx.coroutines.launch

data class WordListUiState(
    val words: List<Word> = emptyList(),
    val groups: List<Group> = emptyList(),
    val selectedGroupId: Long? = null,
    val searchQuery: String = "",
    val dueCount: Int = 0,
    val masteredWordIds: Set<Long> = emptySet(),
    val showMasteredOnly: Boolean = false,
    val isLoading: Boolean = true,
)

@HiltViewModel
class WordListViewModel @Inject constructor(
    private val application: Application,
    private val wordRepository: WordRepository,
    private val groupRepository: GroupRepository,
    private val reviewRepository: ReviewRepository
) : AndroidViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedGroupId = MutableStateFlow<Long?>(null)

    private val _uiState = MutableStateFlow(WordListUiState())
    val uiState: StateFlow<WordListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                wordRepository.observeAll(),
                groupRepository.observeAll(),
                _selectedGroupId,
                _searchQuery,
                reviewRepository.observeDueCount()
            ) { words, groups, groupId, query, dueCount ->
                val display = when {
                    groupId != null -> wordRepository.observeByGroup(groupId).first()
                    query.isNotBlank() -> wordRepository.search(query)
                    else -> words
                }
                WordListUiState(
                    words = display, groups = groups,
                    selectedGroupId = groupId, searchQuery = query,
                    dueCount = dueCount, isLoading = false,
                    masteredWordIds = _uiState.value.masteredWordIds,
                    showMasteredOnly = _uiState.value.showMasteredOnly
                )
            }.collect { _uiState.value = it }
        }

        viewModelScope.launch {
            combine(
                reviewRepository.observeDueCount(),
                flow { emit(reviewRepository.getMasteredWordIds()) }
            ) { dueCount, masteredIds ->
                _uiState.update { it.copy(dueCount = dueCount, masteredWordIds = masteredIds.toSet()) }
            }.collect()
        }
    }

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }
    fun onGroupSelected(groupId: Long?) { _selectedGroupId.value = groupId }
    fun deleteWord(wordId: Long) { viewModelScope.launch { wordRepository.delete(wordId) } }
    fun toggleMasteredFilter() { _uiState.update { it.copy(showMasteredOnly = !it.showMasteredOnly) } }
}
