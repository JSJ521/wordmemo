package com.wordmemo.app.ui.screen.airelations

import androidx.compose.foundation.clickable
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
import com.wordmemo.app.domain.model.AiRelation
import com.wordmemo.app.ui.component.SkeletonLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRelationsScreen(
    wordId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToWord: (Long) -> Unit,
    viewModel: AiRelationsViewModel = viewModel()
) {
    LaunchedEffect(wordId) {
        viewModel.loadRelations(wordId)
    }

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关联图谱") },
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
                Text(uiState.error!!, color = Color.Red)
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = uiState.word?.english ?: "",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = uiState.word?.chinese ?: "",
                    fontSize = 18.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(16.dp))

                Text("关联词汇", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))

                uiState.relations.forEach { relation ->
                    RelationItem(
                        relation = relation,
                        onClick = { /* navigate to word detail - would need word lookup */ },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RelationItem(
    relation: AiRelation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = relation.word,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (!relation.definition.isNullOrBlank()) {
                    Text(
                        text = relation.definition,
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
            Surface(
                color = when (relation.type) {
                    "同义词" -> Color(0xFF43A047)
                    "反义词" -> Color(0xFFE53935)
                    "搭配词组" -> Color(0xFF1E88E5)
                    "形近词" -> Color(0xFFFFA726)
                    else -> Color.Gray
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = relation.type,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
