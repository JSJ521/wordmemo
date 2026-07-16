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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.wordmemo.app.ui.theme.OfflineGray
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showApiKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
            Text("AI 配置", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("配置大模型 API 以使用 AI 翻译、助记、关联图谱功能", color = Color.Gray, fontSize = 14.sp)

            if (!uiState.isOnline) {
                Surface(
                    color = OfflineGray.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "当前离线 — AI 功能不可用",
                        color = OfflineGray,
                        modifier = Modifier.padding(8.dp),
                        fontSize = 12.sp
                    )
                }
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
                        Text(if (showApiKey) "隐藏" else "显示")
                    }
                }
            )

            OutlinedTextField(
                value = uiState.apiBaseUrl,
                onValueChange = { viewModel.onBaseUrlChanged(it) },
                label = { Text("API Endpoint URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.apiModel,
                onValueChange = { viewModel.onModelChanged(it) },
                label = { Text("模型名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.saveApiConfig() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存配置")
                }
                OutlinedButton(
                    onClick = { viewModel.testConnection() },
                    enabled = !uiState.isTestingConnection,
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isTestingConnection) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("测试连接")
                    }
                }
            }

            if (uiState.connectionTestResult != null) {
                Text(
                    text = uiState.connectionTestResult!!,
                    color = if (uiState.connectionTestResult!!.contains("成功")) Color(0xFF43A047) else Color(0xFFE53935),
                    fontSize = 14.sp
                )
            }

            Divider(Modifier.padding(vertical = 8.dp))

            Text("每日学习设置", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("每日新增上限", modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (uiState.dailyReviewLimit > 1) {
                            viewModel.onDailyLimitChanged(uiState.dailyReviewLimit - 1)
                        }
                    }) {
                        Text("-", fontSize = 20.sp)
                    }
                    Text(
                        text = "${uiState.dailyReviewLimit}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    IconButton(onClick = {
                        if (uiState.dailyReviewLimit < 99) {
                            viewModel.onDailyLimitChanged(uiState.dailyReviewLimit + 1)
                        }
                    }) {
                        Text("+", fontSize = 20.sp)
                    }
                }
            }

            Divider(Modifier.padding(vertical = 8.dp))
            // Export section
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val exportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                if (uri != null) viewModel.exportDataToUri(uri, ctx.contentResolver)
            }

            Text("数据管理", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Button(
                onClick = { exportLauncher.launch("书鼠词记_备份.json") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("导出备份")
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.importCet6(ctx) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("导入六级词库 (327词)")
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.optimizeFsrs() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
            ) {
                Text("⚡ 优化 FSRS 参数")
            }

            var importUri by remember { mutableStateOf<android.net.Uri?>(null) }
            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri -> importUri = uri }
            LaunchedEffect(importUri) {
                val uri = importUri ?: return@LaunchedEffect
                viewModel.importFromJson(ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: "")
                importUri = null
            }
            OutlinedButton(
                onClick = { importLauncher.launch("application/json") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("导入数据 (JSON)")
            }

            if (uiState.exportResult != null) {
                Text(uiState.exportResult!!, fontSize = 14.sp)
            }

            Divider(Modifier.padding(vertical = 8.dp))
            Text("版本更新", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            val displayVer = remember {
                try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
                } catch (_: Exception) { "?" }
            }
            Text(
                text = "当前版本: v$displayVer",
                color = Color.Gray,
                fontSize = 14.sp
            )
            Button(
                onClick = { viewModel.checkForUpdate() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isCheckingUpdate
            ) {
                if (uiState.isCheckingUpdate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (uiState.isCheckingUpdate) "检查中..." else "检查更新")
            }

            if (uiState.updateCheckResult != null) {
                Text(
                    text = uiState.updateCheckResult!!,
                    color = Color(0xFF43A047),
                    fontSize = 14.sp
                )
            }

            // 更新确认对话框
            val updateInfo = uiState.pendingUpdate
            if (updateInfo != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissUpdate() },
                    title = { Text("发现新版本 ${updateInfo.latestVersion}") },
                    text = {
                        Column {
                            Text(if (updateInfo.releaseNotes.isNotBlank())
                                updateInfo.releaseNotes.take(300)
                                else "新版本已发布，是否下载更新？")
                        }
                    },
                    confirmButton = {
                        Button(onClick = { viewModel.downloadUpdate(updateInfo) }) {
                            Text("下载更新")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissUpdate() }) {
                            Text("稍后再说")
                        }
                    }
                )
            }
        }
    }
}
