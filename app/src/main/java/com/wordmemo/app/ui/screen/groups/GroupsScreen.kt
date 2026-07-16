package com.wordmemo.app.ui.screen.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordmemo.app.domain.model.Group

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGroup: (Long) -> Unit,
    viewModel: GroupsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分组管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showCreateDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = "新建分组")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无分组，点击 + 创建", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                items(uiState.groups, key = { it.id }) { group ->
                    GroupItem(
                        group = group,
                        onClick = { onNavigateToGroup(group.id) },
                        onDelete = { viewModel.deleteGroup(group.id) }
                    )
                }
            }
        }

        // Create group dialog
        if (uiState.showCreateDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissCreateDialog() },
                title = { Text("新建分组") },
                text = {
                    OutlinedTextField(
                        value = uiState.newGroupName,
                        onValueChange = { viewModel.onNewGroupNameChanged(it) },
                        label = { Text("分组名称") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.createGroup() }) {
                        Text("创建")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissCreateDialog() }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

@Composable
private fun GroupItem(
    group: Group,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(android.graphics.Color.parseColor(group.color)),
                modifier = Modifier.size(12.dp)
            ) {}
            Spacer(Modifier.width(12.dp))
            Text(
                text = group.name,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Red.copy(alpha = 0.6f))
            }
        }
    }
}
