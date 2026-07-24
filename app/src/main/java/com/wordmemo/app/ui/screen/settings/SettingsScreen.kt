package com.wordmemo.app.ui.screen.settings

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.wordmemo.app.ui.theme.AiBadge
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSection(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showApiKey by remember { mutableStateOf(false) }
    var showAsrKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── AI 配置 ──
            SettingsSection(icon = Icons.Default.Key, title = "AI 配置") {
                Text("配置大模型 API 以使用 AI 翻译、助记、关联图谱",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(12.dp))

                if (!uiState.isOnline) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("当前离线 — AI 功能不可用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = uiState.apiKey,
                    onValueChange = { viewModel.onApiKeyChanged(it) },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showApiKey = !showApiKey }) {
                            Text(if (showApiKey) "隐藏" else "显示",
                                style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    shape = MaterialTheme.shapes.small
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.apiBaseUrl,
                    onValueChange = { viewModel.onBaseUrlChanged(it) },
                    label = { Text("API Endpoint URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.apiModel,
                    onValueChange = { viewModel.onModelChanged(it) },
                    label = { Text("模型名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small
                )

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { viewModel.saveApiConfig() },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small
                    ) { Text("保存配置") }
                    OutlinedButton(
                        onClick = { viewModel.testConnection() },
                        enabled = !uiState.isTestingConnection,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        if (uiState.isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("测试连接")
                        }
                    }
                }

                if (uiState.connectionTestResult != null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = if (uiState.connectionTestResult!!.contains("成功"))
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (uiState.connectionTestResult!!.contains("成功"))
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(uiState.connectionTestResult!!,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                }

                // ── Cookie 配置 ──
            SettingsSection(icon = Icons.Default.Info, title = "视频下载 Cookie") {
                Text("配置 B站 / YouTube 的登录 Cookie，解锁登录视频和年龄限制视频的下载。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.bilibiliCookie,
                    onValueChange = { viewModel.onBilibiliCookieChanged(it) },
                    label = { Text("B站 Cookie") },
                    placeholder = { Text("粘贴B站 cookies.txt 内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    shape = MaterialTheme.shapes.small
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.youtubeCookie,
                    onValueChange = { viewModel.onYoutubeCookieChanged(it) },
                    label = { Text("YouTube Cookie") },
                    placeholder = { Text("粘贴YouTube cookies.txt 内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    shape = MaterialTheme.shapes.small
                )

                Spacer(Modifier.height(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "如何获取cookie：\n" +
                            "1. 电脑浏览器登录B站/YouTube\n" +
                            "2. 安装 Get cookies.txt 扩展\n" +
                            "3. 导出cookie粘贴到上方",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.saveCookieConfig() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) { Text("保存 Cookie") }

                if (uiState.connectionTestResult != null && uiState.connectionTestResult!!.contains("Cookie")) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(uiState.connectionTestResult!!,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

                // ── 数据管理 ──
            SettingsSection(icon = Icons.Default.Storage, title = "数据管理") {
                val ctx = LocalContext.current
                val exportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri -> if (uri != null) viewModel.exportDataToUri(uri, ctx.contentResolver) }

                Button(
                    onClick = { exportLauncher.launch("单词本_备份.json") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) { Text("导出备份") }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.importCet6(ctx) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) { Text("导入六级词库 (327词)") }

                Spacer(Modifier.height(12.dp))

                // 分隔：ASR（语音识别）配置
                Text(
                    text = "语音识别（Whisper）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )

                OutlinedTextField(
                    value = uiState.asrApiKey,
                    onValueChange = { viewModel.onAsrApiKeyChanged(it) },
                    label = { Text("ASR API Key (OpenRouter)") },
                    placeholder = { Text("sk-or-v1-...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showAsrKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showAsrKey = !showAsrKey }) {
                            Text(if (showAsrKey) "隐藏" else "显示",
                                style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    shape = MaterialTheme.shapes.small
                )
                Text(
                    text = "填写 OpenRouter API Key 即可使用在线语音转字幕",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                Spacer(Modifier.height(12.dp))

                FilledTonalButton(
                    onClick = { viewModel.saveApiConfig() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                ) { Text("保存配置") }

                Spacer(Modifier.height(12.dp))

                var importUri by remember { mutableStateOf<android.net.Uri?>(null) }
                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri -> importUri = uri }
                LaunchedEffect(importUri) {
                    val u = importUri ?: return@LaunchedEffect
                    viewModel.importFromJson(ctx.contentResolver.openInputStream(u)
                        ?.bufferedReader()?.readText() ?: "")
                    importUri = null
                }

                OutlinedButton(
                    onClick = { importLauncher.launch("application/json") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) { Text("导入数据 (JSON)") }

                if (uiState.exportResult != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(uiState.exportResult!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ── 版本更新 ──
            SettingsSection(icon = Icons.Default.SystemUpdate, title = "版本更新") {
                val ctx = LocalContext.current
                val displayVer = remember {
                    try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
                    } catch (_: Exception) { "?" }
                }
                Text("当前版本: v$displayVer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.checkForUpdate() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    enabled = !uiState.isCheckingUpdate
                ) {
                    if (uiState.isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (uiState.isCheckingUpdate) "检查中..." else "检查更新")
                }

                if (uiState.updateCheckResult != null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(uiState.updateCheckResult!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // 更新确认对话框
    val updateInfo = uiState.pendingUpdate
    if (updateInfo != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdate() },
            title = { Text("发现新版本 ${updateInfo.latestVersion}",
                style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(if (updateInfo.releaseNotes.isNotBlank())
                    updateInfo.releaseNotes.take(300) else "新版本已发布，是否下载更新？",
                    style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                Button(onClick = { viewModel.downloadUpdate(updateInfo) },
                    shape = MaterialTheme.shapes.small) { Text("下载更新") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdate() }) { Text("稍后再说") }
            },
            shape = MaterialTheme.shapes.medium
        )
    }
}
