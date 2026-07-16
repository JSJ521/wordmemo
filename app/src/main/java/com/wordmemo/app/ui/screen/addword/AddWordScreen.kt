package com.wordmemo.app.ui.screen.addword

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordmemo.app.ui.component.SearchBar
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWordScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddWordViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加单词") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // English input
            OutlinedTextField(
                value = uiState.english,
                onValueChange = { viewModel.onEnglishChanged(it) },
                label = { Text("英文单词/短语") },
                placeholder = { Text("例如：abandon") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Chinese input
            OutlinedTextField(
                value = uiState.chinese,
                onValueChange = { viewModel.onChineseChanged(it) },
                label = { Text("中文释义") },
                placeholder = { Text("例如：放弃、抛弃") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Note input
            OutlinedTextField(
                value = uiState.note,
                onValueChange = { viewModel.onNoteChanged(it) },
                label = { Text("备注（可选）") },
                placeholder = { Text("例句、记忆技巧等") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Spacer(Modifier.height(8.dp))

            // Save button
            Button(
                onClick = { viewModel.save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !uiState.isSaving
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.MenuBook, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("收藏单词")
                }
            }

            // Result message
            if (uiState.saveResult != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.saveResult!!.startsWith("✅"))
                            Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                    )
                ) {
                    Text(
                        text = uiState.saveResult!!,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
