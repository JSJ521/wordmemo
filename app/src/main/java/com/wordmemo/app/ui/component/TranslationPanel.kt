package com.wordmemo.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordmemo.app.ui.theme.AiBadge
import com.wordmemo.app.ui.theme.OfflineGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationPanel(
    isVisible: Boolean,
    chinese: String,
    aiTranslation: String?,
    isOnline: Boolean,
    isLoading: Boolean = false,
    onDismiss: () -> Unit,
    onOpenAiMnemonics: (() -> Unit)? = null,
    onOpenAiRelations: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("翻译", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("本地翻译", fontWeight = FontWeight.Medium, color = Color.Gray, fontSize = 12.sp)
                Text(chinese, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)

                if (aiTranslation != null && isOnline) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = AiBadge.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("AI 翻译", fontWeight = FontWeight.Bold, color = AiBadge, fontSize = 12.sp)
                                Spacer(Modifier.width(8.dp))
                                Surface(color = AiBadge, shape = RoundedCornerShape(4.dp)) {
                                    Text("AI", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(aiTranslation, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                } else if (!isOnline) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = OfflineGray.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "离线模式·本地翻译",
                            color = OfflineGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (isOnline && onOpenAiMnemonics != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenAiMnemonics) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("AI 助记", fontSize = 12.sp)
                        }
                        if (onOpenAiRelations != null) {
                            OutlinedButton(onClick = onOpenAiRelations) {
                                Icon(Icons.Filled.AccountTree, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("关联图谱", fontSize = 12.sp)
                            }
                        }
                    }
                } else if (onOpenAiMnemonics != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            colors = ButtonDefaults.outlinedButtonColors(disabledContentColor = OfflineGray)
                        ) {
                            Text("AI 助记(需网络)", fontSize = 12.sp)
                        }
                        if (onOpenAiRelations != null) {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                colors = ButtonDefaults.outlinedButtonColors(disabledContentColor = OfflineGray)
                            ) {
                                Text("关联图谱(需网络)", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
