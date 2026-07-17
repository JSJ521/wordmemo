package com.wordmemo.app.ui.screen.ocr

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    onNavigateBack: () -> Unit,
    viewModel: OcrViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.init(context) }

    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var fileMime by remember { mutableStateOf("") }

    // 选择图片
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            fileUri = it; fileMime = "image/*"
            viewModel.processFile(it, context, "image/png", "图片")
        }
    }

    // 选择文档
    val pickDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            fileUri = it
            val name = context.contentResolver.getType(it) ?: it.lastPathSegment ?: "文件"
            fileMime = name
            viewModel.processFile(it, context, name, it.lastPathSegment ?: "文档")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (fileUri == null && uiState.recognizedText.isEmpty()) {
                Spacer(Modifier.weight(0.3f))
                Icon(Icons.Default.ImageSearch, contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text("上传图片或文档", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("自动提取英文单词，勾选后加入单词本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { pickImage.launch("image/*") },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("图片")
                    }
                    Button(
                        onClick = { pickDocument.launch("*/*") },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("文档")
                    }
                }
                Text("支持 TXT / Markdown / Word 文档",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(0.5f))
            } else {
                if (uiState.isProcessing) {
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("处理中...", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                }
                else if (uiState.error != null) {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null,
                                tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Text(uiState.error!!, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { pickImage.launch("image/*") }) { Text("选图片") }
                        Button(onClick = { pickDocument.launch("*/*") }) { Text("选文档") }
                    }
                }
                else if (uiState.words.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TextSnippet, contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("识别到 ${uiState.words.size} 个单词",
                                style = MaterialTheme.typography.titleMedium)
                            if (uiState.fileName.isNotEmpty())
                                Text(uiState.fileName, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (uiState.addedCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text("已添加 ${uiState.addedCount} 个",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // 图片预览（仅对图片）
                    if (fileMime.startsWith("image/")) {
                        fileUri?.let { uri ->
                            val bitmap = remember(uri) {
                                try { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }
                                catch (_: Exception) { null }
                            }
                            if (bitmap != null) {
                                Card(shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.medium)) {
                                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "预览",
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { pickDocument.launch("*/*") },
                            modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small
                        ) { Text("换文件") }
                        Button(
                            onClick = { viewModel.addSelectedWords() },
                            enabled = uiState.selectedWords.isNotEmpty(),
                            modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small
                        ) { Text("添加选中 (${uiState.selectedWords.size})") }
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(uiState.words) { word ->
                            val isSelected = word.text in uiState.selectedWords
                            Card(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { viewModel.toggleWord(word.text) }
                                    .then(if (isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier),
                                shape = MaterialTheme.shapes.small,
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = isSelected, onCheckedChange = { viewModel.toggleWord(word.text) })
                                    Spacer(Modifier.width(8.dp))
                                    Text(word.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
                else if (fileUri != null) {
                    Spacer(Modifier.height(16.dp))
                    Text("未识别到英文单词", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { pickImage.launch("image/*") }) { Text("选图片") }
                        Button(onClick = { pickDocument.launch("*/*") }) { Text("选文档") }
                    }
                }
            }
        }
    }
}
