package com.wordmemo.app.ui.screen.shadowing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordmemo.app.data.shadowing.service.DownloadStatus
import com.wordmemo.app.domain.shadowing.model.ShadowingVideo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadowingHomeScreen(
    onNavigateToSession: (Long) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    viewModel: ShadowingHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showImportSheet by remember { mutableStateOf(false) }
    var importBilibiliMode by remember { mutableStateOf(false) }
    var bilibiliUrl by remember { mutableStateOf("") }
    var videoToDelete by remember { mutableStateOf<ShadowingVideo?>(null) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var showSubtitlePrompt by remember { mutableStateOf(false) }

    // SAF file picker for local video import
    val localFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            pendingVideoUri = it
            showSubtitlePrompt = true  // 弹出字幕选择提示
        }
    }

    // SAF file picker for subtitle file (triggered after video selection)
    val subtitleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { subtitleUri: Uri? ->
        pendingVideoUri?.let { videoUri ->
            if (subtitleUri != null) {
                viewModel.onEvent(ShadowingHomeEvent.ImportLocalFileWithSubtitle(videoUri, subtitleUri))
            } else {
                viewModel.onEvent(ShadowingHomeEvent.ImportLocalFile(videoUri))
            }
            pendingVideoUri = null
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "影子跟读",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { /* 搜索 */ }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = { /* 排序 */ }) {
                        Icon(Icons.Default.Sort, contentDescription = "排序")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showImportSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "导入视频")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading && uiState.videoList.isEmpty()) {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (uiState.videoList.isEmpty()) {
                // Empty state
                EmptyShadowingState(
                    onImportClick = { showImportSheet = true }
                )
            } else {
                // Video list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 80.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = uiState.videoList,
                        key = { it.id }
                    ) { video ->
                        VideoCard(
                            video = video,
                            onClick = { onNavigateToSession(video.id) },
                            onDelete = { videoToDelete = video }
                        )
                    }
                }
            }

            // Error snackbar
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = {
                            viewModel.onEvent(ShadowingHomeEvent.ClearError)
                        }) {
                            Text("关闭")
                        }
                    }
                ) {
                    Text(error)
                }
            }

            // Download progress
            uiState.downloadProgress?.let { progress ->
                if (progress.status == DownloadStatus.DOWNLOADING) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "正在下载...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            LinearProgressIndicator(
                                progress = { progress.percentage / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${progress.percentage}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Import method bottom sheet
    if (showImportSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showImportSheet = false
                importBilibiliMode = false
                bilibiliUrl = ""
            },
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ImportMethodSheetContent(
                bilibiliMode = importBilibiliMode,
                bilibiliUrl = bilibiliUrl,
                onBilibiliUrlChange = { bilibiliUrl = it },
                onBilibiliModeChange = { importBilibiliMode = it },
                onImportBilibili = {
                    if (bilibiliUrl.isNotBlank()) {
                        viewModel.onEvent(ShadowingHomeEvent.ImportBilibili(bilibiliUrl))
                        showImportSheet = false
                        importBilibiliMode = false
                        bilibiliUrl = ""
                    }
                },
                onImportLocal = {
                    showImportSheet = false
                    // 打开 SAF 文件选择器，支持常见视频格式
                    localFilePickerLauncher.launch(arrayOf("video/*", "application/octet-stream"))
                },
                isDownloading = uiState.downloadProgress?.status == DownloadStatus.DOWNLOADING
            )
        }
    }

    // 删除确认对话框
    videoToDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            shape = MaterialTheme.shapes.large,
            title = {
                Text("删除视频", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Text("确定要删除「${video.title}」吗？\n相关的句子和录音数据也将被删除。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onEvent(ShadowingHomeEvent.DeleteVideo(video.id))
                        videoToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 字幕选择提示对话框（选完视频后弹出）
    if (showSubtitlePrompt) {
        AlertDialog(
            onDismissRequest = {
                showSubtitlePrompt = false
                pendingVideoUri?.let { uri ->
                    viewModel.onEvent(ShadowingHomeEvent.ImportLocalFile(uri))
                }
                pendingVideoUri = null
            },
            shape = MaterialTheme.shapes.large,
            title = {
                Text("选择字幕文件", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Text(
                    "是否需要为视频选择对应的字幕文件（.srt / .vtt）？\n\n" +
                            "如果跳过，系统将尝试自动提取内嵌字幕或通过语音识别生成。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSubtitlePrompt = false
                        subtitleFilePickerLauncher.launch(
                            arrayOf("text/*", "application/x-subrip", "application/x-srt", "*/*")
                        )
                    }
                ) {
                    Text("选择字幕")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSubtitlePrompt = false
                        pendingVideoUri?.let { uri ->
                            viewModel.onEvent(ShadowingHomeEvent.ImportLocalFile(uri))
                        }
                        pendingVideoUri = null
                    }
                ) {
                    Text("跳过")
                }
            }
        )
    }
}

@Composable
private fun EmptyShadowingState(onImportClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = "导入你的第一个英语视频",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "支持 B站链接 / 本地视频文件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            FilledTonalButton(
                onClick = onImportClick,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("导入视频")
            }
        }
    }
}

@Composable
private fun VideoCard(
    video: ShadowingVideo,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail placeholder
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(72.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                // Duration overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatDuration(video.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }

            // Video info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${video.completedCount}/${video.sentenceCount} 句 / ${formatSource(video.sourceType)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Progress bar
                val progress = if (video.sentenceCount > 0) {
                    video.completedCount.toFloat() / video.sentenceCount
                } else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                )
            }

            // More button
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportMethodSheetContent(
    bilibiliMode: Boolean,
    bilibiliUrl: String,
    onBilibiliUrlChange: (String) -> Unit,
    onBilibiliModeChange: (Boolean) -> Unit,
    onImportBilibili: () -> Unit,
    onImportLocal: () -> Unit,
    isDownloading: Boolean
) {
    Column(
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Handle
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                .align(Alignment.CenterHorizontally)
        )

        Text(
            text = "导入视频",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        // B站链接 option
        Card(
            onClick = { onBilibiliModeChange(true) },
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "B站链接",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "粘贴B站视频链接自动下载",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 本地文件 option
        Card(
            onClick = onImportLocal,
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "本地文件",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "从手机存储选择视频文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Bilibili URL input panel
        AnimatedVisibility(
            visible = bilibiliMode,
            enter = slideInVertically() + fadeIn()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = bilibiliUrl,
                        onValueChange = onBilibiliUrlChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("粘贴B站视频链接...") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        trailingIcon = {
                            if (bilibiliUrl.isNotEmpty()) {
                                IconButton(onClick = { onBilibiliUrlChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "清除")
                                }
                            }
                        }
                    )
                    Button(
                        onClick = onImportBilibili,
                        enabled = bilibiliUrl.isNotBlank() && !isDownloading,
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("导入")
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSec = durationMs / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}

private fun formatSource(sourceType: String): String = when (sourceType) {
    "bilibili" -> "B站"
    "local" -> "本地"
    else -> sourceType
}
