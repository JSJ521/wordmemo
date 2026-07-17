package com.wordmemo.app.ui.screen.addword

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

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
                title = { Text(if (uiState.isBatchMode) "批量添加" else "添加单词") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.toggleBatchMode() }) {
                        Text(if (uiState.isBatchMode) "单个添加" else "批量添加", fontSize = 13.sp)
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
            if (uiState.isBatchMode) {
                // ── 批量模式 ──
                Text(
                    "粘贴或输入多个单词，用逗号、空格或换行分隔",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = uiState.batchInput,
                    onValueChange = { viewModel.onBatchInputChanged(it) },
                    label = { Text("英文单词（批量）") },
                    placeholder = { Text("abandon, ability, abroad\naccept, access, accident") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    maxLines = 10
                )

                if (uiState.batchInput.isNotBlank()) {
                    val words = uiState.batchInput.split(Regex("[\\n,，、\\s]+"))
                        .map { it.trim() }
                        .filter { it.matches(Regex("^[a-zA-Z][a-zA-Z\\-']{1,44}$")) }
                    if (words.isNotEmpty()) {
                        Text(
                            "识别到 ${words.size} 个有效单词",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

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
                        Icon(Icons.Default.PostAdd, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("批量添加")
                    }
                }
            } else {
                // ── 单个添加模式 ──
                OutlinedTextField(
                    value = uiState.english,
                    onValueChange = { viewModel.onEnglishChanged(it) },
                    label = { Text("英文单词/短语") },
                    placeholder = { Text("例如：abandon") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.chinese,
                    onValueChange = { viewModel.onChineseChanged(it) },
                    label = { Text("中文释义") },
                    placeholder = { Text("例如：放弃、抛弃") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.note,
                    onValueChange = { viewModel.onNoteChanged(it) },
                    label = { Text("备注（可选）") },
                    placeholder = { Text("例句、记忆技巧等") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Spacer(Modifier.height(8.dp))

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
