package com.wordmemo.app.ui.screen.groups

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wordmemo.app.data.local.WordMemoDatabase
import com.wordmemo.app.domain.model.Group
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GroupsUiState(
    val groups: List<Group> = emptyList(),
    val showCreateDialog: Boolean = false,
    val newGroupName: String = ""
)

class GroupsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WordMemoDatabase.getInstance(application)
    private val groupDao = db.groupDao()

    private val _uiState = MutableStateFlow(GroupsUiState())
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            groupDao.observeAll().collect { entities ->
                val groups = entities.map {
                    Group(id = it.id, name = it.name, color = it.color)
                }
                _uiState.value = _uiState.value.copy(groups = groups)
            }
        }
    }

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true, newGroupName = "")
    }

    fun dismissCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false)
    }

    fun onNewGroupNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(newGroupName = name)
    }

    fun createGroup() {
        val name = _uiState.value.newGroupName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                groupDao.create(
                    com.wordmemo.app.data.local.entity.GroupEntity(
                        name = name, color = "#2196F3"
                    )
                )
            }
            _uiState.value = _uiState.value.copy(showCreateDialog = false, newGroupName = "")
        }
    }

    fun deleteGroup(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { groupDao.delete(id) }
        }
    }
}
