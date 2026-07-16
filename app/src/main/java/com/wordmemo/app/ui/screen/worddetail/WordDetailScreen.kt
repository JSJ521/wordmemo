package com.wordmemo.app.ui.screen.worddetail

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
    onNavigateToMnemonics: () -> Unit,
    onNavigateToRelations: () -> Unit,
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
                title = { Text("单词详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
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
                Text("单词未找到", color = Color.Gray)
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
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                if (uiState.isTranslating) {
                    Text(
                        text = "AI 翻译中...",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = if (word.chinese.contains("待补充")) "正在获取释义"
                              else word.chinese,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (!word.note.isNullOrBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text("备注", fontWeight = FontWeight.Medium, color = Color.Gray, fontSize = 12.sp)
                    Text(word.note, fontSize = 16.sp)
                }

                Spacer(Modifier.height(16.dp))

                // 分组选择
                if (uiState.allGroups.isNotEmpty()) {
                    Text("分组", fontWeight = FontWeight.Medium, color = Color.Gray, fontSize = 12.sp)
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

                Spacer(Modifier.height(24.dp))
                Text("添加时间", fontWeight = FontWeight.Medium, color = Color.Gray, fontSize = 12.sp)
                Text(
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(word.createdAt)),
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(24.dp))

                // AI features
                Text("AI 增强", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))

                if (uiState.isOnline) {
                    Button(
                        onClick = onNavigateToMnemonics,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("AI 助记 — 谐音/词根/故事联想")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onNavigateToRelations,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("关联图谱 — 同义词/近义词/搭配")
                    }

                    Button(
                        onClick = onNavigateToGraph,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
                    ) {
                        Text("单词图谱 — 网状关系")
                    }
                } else {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = OfflineGray.copy(alpha = 0.3f)
                        )
                    ) {
                        Text("AI 功能需要网络连接", color = OfflineGray)
                    }
                }
            }
        }
    }
}
