package com.wordmemo.app.ui.screen.aimnemonics

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
import com.wordmemo.app.domain.model.AiMnemonic
import com.wordmemo.app.ui.component.SkeletonLoader
import com.wordmemo.app.ui.theme.AiBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiMnemonicsScreen(
    wordId: Long,
    onNavigateBack: () -> Unit,
    viewModel: AiMnemonicsViewModel = viewModel()
) {
    LaunchedEffect(wordId) {
        viewModel.loadMnemonics(wordId)
    }

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 助记") },
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
                // Word header
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

                Text("AI 助记方法", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))

                uiState.mnemonics.forEach { mnemonic ->
                    MnemonicCard(mnemonic)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun MnemonicCard(mnemonic: AiMnemonic, modifier: Modifier = Modifier) {
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
                    text = mnemonic.method,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = mnemonic.content,
                fontSize = 16.sp
            )
        }
    }
}
