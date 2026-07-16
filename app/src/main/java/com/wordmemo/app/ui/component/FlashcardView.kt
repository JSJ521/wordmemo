package com.wordmemo.app.ui.component

import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs

@Composable
fun FlashcardView(
    frontText: String,
    frontPhonetic: String,
    backText: String,
    isFlipped: Boolean,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation = remember { Animatable(0f) }
    val target = if (isFlipped) 180f else 0f

    // TextToSpeech：全局初始化一次，设英语为朗读语言
    val context = LocalContext.current
    val tts = remember {
        lateinit var t: TextToSpeech
        t = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                t.setLanguage(java.util.Locale.US)
            } else {
                android.util.Log.w("FlashcardView", "TTS init failed: $status")
            }
        }
        t
    }
    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    LaunchedEffect(isFlipped) {
        rotation.animateTo(
            targetValue = target,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    val showFront = rotation.value < 90f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .clickable { onFlip() }
            .graphicsLayer {
                rotationY = rotation.value
                cameraDistance = 12f * density
            },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = if (showFront) Color(0xFFE3F2FD) else Color(0xFFF3E5F5))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (showFront) Color(0xFFE3F2FD) else Color(0xFFF3E5F5),
                    RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // ── 正面 ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(24.dp)
                    .alpha(if (showFront) 1f else 0f)
            ) {
                // 英语单词
                Text(
                    text = frontText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 音标（浅灰斜体）
                if (frontPhonetic.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = frontPhonetic,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF78909C)
                    )
                }

                // 读音按钮
                Spacer(Modifier.height(16.dp))
                FilledTonalIconButton(
                    onClick = {
                        tts.speak(frontText, TextToSpeech.QUEUE_FLUSH, null, null)
                    },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFFBBDEFB)
                    )
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = "朗读",
                        tint = Color(0xFF1565C0)
                    )
                }
            }

            // ── 反面（翻转超过90°才显示） ──
            if (!showFront) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(24.dp)
                        .alpha((abs(rotation.value) - 90f) / 90f)
                        .graphicsLayer { rotationY = 180f }
                ) {
                    Text(
                        text = backText,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // 反面也有读音按钮
                    Spacer(Modifier.height(16.dp))
                    FilledTonalIconButton(
                        onClick = {
                            tts.speak(frontText, TextToSpeech.QUEUE_FLUSH, null, null)
                        },
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFFE1BEE7)
                        )
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "朗读",
                            tint = Color(0xFF6A1B9A)
                        )
                    }
                }
            }
        }
    }
}
