package com.wordmemo.app.ui.screen.worddetail

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordmemo.app.ui.component.SkeletonLoader
import com.wordmemo.app.ui.component.TranslationPanel
import com.wordmemo.app.ui.theme.OfflineGray
import com.wordmemo.app.domain.model.Word

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDetailScreen(
    wordId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToAiLearning: () -> Unit,
    onNavigateToGraph: () -> Unit,
    viewModel: WordDetailViewModel = viewModel()
) {
    LaunchedEffect(wordId) {
        viewModel.loadWord(wordId)
    }

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("单词详情", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            SkeletonLoader(modifier = Modifier.padding(padding))
        } else if (uiState.word == null) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("单词未找到",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val word = uiState.word!!
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Word header
                Text(
                    text = word.english,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                if (uiState.isTranslating) {
                    Text(
                        text = "AI 翻译中...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (uiState.translationError != null && word.chinese.contains("待补充")) {
                    Text(
                        text = uiState.translationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = { viewModel.retryTranslate() }) {
                        Text("重试翻译", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Text(
                        text = if (word.chinese.contains("待补充")) "正在获取释义"
                              else word.chinese,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (!word.note.isNullOrBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text("备注", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(word.note, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(16.dp))

                // 分组选择
                if (uiState.allGroups.isNotEmpty()) {
                    Text("分组", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.allGroups.forEach { group ->
                            val isSelected = group.id in uiState.wordGroups
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) viewModel.removeFromGroup(group.id)
                                    else viewModel.assignToGroup(group.id)
                                },
                                label = { Text(group.name) }
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                if (uiState.isOnline) {
                    Button(
                        onClick = onNavigateToAiLearning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("AI 学习 — 助记 & 批量生成")
                    }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = onNavigateToGraph,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("单词图谱 — 网状关系")
                    }
                } else {
                    Text("AI 功能需要网络连接", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
