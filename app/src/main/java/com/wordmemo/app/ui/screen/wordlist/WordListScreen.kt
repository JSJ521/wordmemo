package com.wordmemo.app.ui.screen.wordlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordmemo.app.domain.model.Word
import com.wordmemo.app.ui.component.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordListScreen(
    onNavigateToAdd: () -> Unit,
    onNavigateToWordDetail: (Long) -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToGroups: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStats: () -> Unit,
    initialGroupId: Long? = null,
    viewModel: WordListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 从分组页返回时应用选中的分组筛选
    LaunchedEffect(initialGroupId) {
        if (initialGroupId != null) {
            viewModel.onGroupSelected(initialGroupId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("词鼠书记") },
                actions = {
                    BadgedBox(badge = {
                        if (uiState.dueCount > 0) {
                            Badge { Text("${uiState.dueCount}") }
                        }
                    }) {
                        IconButton(onClick = onNavigateToReview) {
                            Icon(Icons.Default.Loop, contentDescription = "复习")
                        }
                    }
                    IconButton(onClick = onNavigateToStats) {
                        Icon(Icons.Default.BarChart, contentDescription = "统计")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallFloatingActionButton(onClick = onNavigateToGroups) {
                    Icon(Icons.Default.Folder, contentDescription = "分组")
                }
                FloatingActionButton(onClick = onNavigateToAdd) {
                    Icon(Icons.Default.Add, contentDescription = "添加单词")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search bar
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = { viewModel.onSearchQueryChanged(it) }
            )

            // AI 行业词汇生成
            AiVocabSection(
                isGenerating = uiState.isGeneratingAi,
                generatedWords = uiState.aiGeneratedWords,
                resultMessage = uiState.aiGenerationResult,
                onGenerate = { viewModel.generateAiVocab() },
                onDismissResult = { viewModel.dismissAiResult() }
            )

            // 筛选栏：全部 / 已掌握
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !uiState.showMasteredOnly,
                    onClick = { if (uiState.showMasteredOnly) viewModel.toggleMasteredFilter() },
                    label = { Text("全部 (${uiState.words.size})") }
                )
                FilterChip(
                    selected = uiState.showMasteredOnly,
                    onClick = { if (!uiState.showMasteredOnly) viewModel.toggleMasteredFilter() },
                    label = { Text("已掌握 (${uiState.masteredWordIds.size})") }
                )
            }

            val displayWords = if (uiState.showMasteredOnly)
                uiState.words.filter { it.id in uiState.masteredWordIds }
            else uiState.words

            if (uiState.isLoading) {
                SkeletonLoader()
            } else if (displayWords.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Spacer(Modifier.height(16.dp))
                        if (uiState.selectedGroupId != null && uiState.words.none { it.id in uiState.masteredWordIds }) {
                            Text("该分组还没有单词", color = Color.Gray, fontSize = 16.sp)
                            Text("在单词详情页可分配分组", color = Color.Gray, fontSize = 14.sp)
                        } else {
                            Text("还没有收藏单词", color = Color.Gray, fontSize = 16.sp)
                            Text("点击 + 添加第一个单词", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(displayWords, key = { it.id }) { word ->
                        WordItem(
                            word = word,
                            onClick = { onNavigateToWordDetail(word.id) },
                            onDelete = { viewModel.deleteWord(word.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordItem(
    word: Word,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFE53935), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = Color.White
                )
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = word.english,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                if (word.phonetic.isNotBlank()) {
                    Text(
                        text = word.phonetic,
                        color = Color(0xFF78909C),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Light
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = word.chinese,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                }
            }
        }
    }
}

/** AI 行业词汇生成卡片 */
@Composable
private fun AiVocabSection(
    isGenerating: Boolean,
    generatedWords: List<com.wordmemo.app.data.network.AiWordGenerator.GeneratedWord>,
    resultMessage: String?,
    onGenerate: () -> Unit,
    onDismissResult: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F8FF)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null,
                    tint = Color(0xFF1565C0), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("海外 EPC 项目词汇", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onGenerate,
                    enabled = !isGenerating,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("生成中...", fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("随机生成", fontSize = 13.sp)
                    }
                }
            }

            if (resultMessage != null) {
                Spacer(Modifier.height(6.dp))
                Text(resultMessage, fontSize = 13.sp,
                    color = if (resultMessage.startsWith("✅")) Color(0xFF2E7D32)
                    else Color(0xFFC62828))
            }

            if (generatedWords.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("本次生成:", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                generatedWords.forEach { w ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("▸ ", fontSize = 13.sp, color = Color(0xFF1565C0))
                        Text(w.english, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        if (w.chinese.isNotBlank()) {
                            Text(" — ${w.chinese}", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
