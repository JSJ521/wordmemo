package com.wordmemo.app.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wordmemo.app.ui.theme.OfflineGray

@Composable
fun OfflineBanner(
    isOffline: Boolean,
    modifier: Modifier = Modifier
) {
    if (isOffline) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = OfflineGray.copy(alpha = 0.15f),
            shape = RoundedCornerShape(0.dp)
        ) {
            Text(
                text = "离线模式 — AI 功能不可用，基础功能正常运行",
                color = OfflineGray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun SkeletonLoader(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        repeat(3) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(vertical = 4.dp),
                color = Color.LightGray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            ) {}
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "搜索单词...",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun GroupFilterBar(
    groups: List<com.wordmemo.app.domain.model.Group>,
    selectedGroupId: Long?,
    onGroupSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (groups.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedGroupId == null,
            onClick = { onGroupSelected(null) },
            label = { Text("全部") }
        )
        groups.forEach { group ->
            FilterChip(
                selected = selectedGroupId == group.id,
                onClick = { onGroupSelected(group.id) },
                label = { Text(group.name) }
            )
        }
    }
}
