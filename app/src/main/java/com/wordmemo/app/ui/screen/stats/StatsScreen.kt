package com.wordmemo.app.ui.screen.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.wordmemo.app.domain.model.Stats
import com.wordmemo.app.ui.component.SkeletonLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("学习统计") },
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
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("学习进度", fontWeight = FontWeight.Bold, fontSize = 20.sp)

                // Stats cards grid
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        title = "总单词",
                        value = "${uiState.stats.totalWords}",
                        icon = Icons.Default.MenuBook,
                        color = Color(0xFF1976D2),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "待复习",
                        value = "${uiState.stats.dueCards}",
                        icon = Icons.Default.Loop,
                        color = Color(0xFFFFA726),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        title = "已掌握",
                        value = "${uiState.stats.masteredWords}",
                        icon = Icons.Default.CheckCircle,
                        color = Color(0xFF43A047),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "总复习",
                        value = "${uiState.stats.totalReviews}",
                        icon = Icons.Default.Replay,
                        color = Color(0xFF7C4DFF),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text("今日数据", fontWeight = FontWeight.Bold, fontSize = 20.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        title = "今日复习",
                        value = "${uiState.stats.todayReviews}",
                        icon = Icons.Default.Today,
                        color = Color(0xFF26A69A),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "今日新增",
                        value = "${uiState.stats.todayNewCards} / ${uiState.stats.dailyReviewLimit}",
                        icon = Icons.Default.AddCircle,
                        color = Color(0xFFE91E63),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text("系统状态", fontWeight = FontWeight.Bold, fontSize = 20.sp)

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("FSRS 参数优化", color = Color.Gray)
                            Text(
                                if (uiState.stats.fsrsOptimized) "已优化" else "默认参数",
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = color
            )
            Text(
                text = title,
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}
