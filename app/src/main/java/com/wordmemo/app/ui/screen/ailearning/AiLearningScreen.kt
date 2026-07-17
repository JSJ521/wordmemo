package com.wordmemo.app.ui.screen.ailearning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.wordmemo.app.ui.theme.AiBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiLearningScreen(
    wordId: Long,
    onNavigateBack: () -> Unit,
    viewModel: AiLearningViewModel = viewModel()
) {
    LaunchedEffect(wordId) {
        viewModel.loadWord(wordId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val tabs = listOf("助记方法", "AI生成")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 学习") },
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
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                // Word header
                Text(
                    text = uiState.word?.english ?: "",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Text(
                    text = uiState.word?.chinese ?: "",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // TabRow
                TabRow(selectedTabIndex = uiState.selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = uiState.selectedTab == index,
                            onClick = { viewModel.selectTab(index) },
                            text = { Text(title) }
                        )
                    }
                }

                // Tab content
                when (uiState.selectedTab) {
                    0 -> MnemonicsTab(uiState)
                    1 -> AiGenerationTab(uiState, viewModel)
                }
            }
        }
    }
}

@Composable
private fun MnemonicsTab(uiState: AiLearningUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("AI 助记方法", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))

        if (uiState.mnemonics.isEmpty()) {
            Text("暂无助记", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            uiState.mnemonics.forEach { mnemonic ->
                MnemonicCard(mnemonic.method, mnemonic.content)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun AiGenerationTab(uiState: AiLearningUiState, viewModel: AiLearningViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Generation card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF1565C0))
                    Spacer(Modifier.width(8.dp))
                    Text("海外 EPC 项目词汇", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.generateAiVocab() },
                    enabled = !uiState.isGenerating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("生成中...")
                    } else {
                        Text("AI 生成")
                    }
                }
            }
        }

        // Result message
        uiState.generationResult?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(msg, color = Color(0xFF2E7D32), fontSize = 14.sp)
        }

        // Generated words list
        if (uiState.generatedWords.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("已生成词汇", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            uiState.generatedWords.forEach { word ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(word.english, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(word.chinese, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MnemonicCard(method: String, content: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                color = AiBadge,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = method,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(text = content, fontSize = 16.sp)
        }
    }
}
