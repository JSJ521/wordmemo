package com.wordmemo.app.ui.component

import android.media.MediaPlayer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URLEncoder
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

    // 在线 TTS：播放 Google 美式发音
    fun speakWord(word: String) {
        try {
            val encoded = URLEncoder.encode(word, "UTF-8")
            val url = "https://translate.google.com/translate_tts?ie=UTF-8&q=$encoded&tl=en&client=tw-ob"
            MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener { start() }
                setOnCompletionListener { release() }
                setOnErrorListener { _, _, _ -> release(); true }
                prepareAsync()
            }
        } catch (e: Exception) {
            android.util.Log.e("FlashcardView", "在线发音失败: $word", e)
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
            .defaultMinSize(minHeight = 240.dp)
            .clickable { onFlip() }
            .graphicsLayer {
                rotationY = rotation.value
                cameraDistance = 12f * density
            },
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (showFront) MaterialTheme.colorScheme.primaryContainer 
                           else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // ── 正面 ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(24.dp)
                    .alpha(if (showFront) 1f else 0f)
            ) {
                Text(
                    text = frontText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (frontPhonetic.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = frontPhonetic,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))
                FilledTonalIconButton(
                    onClick = { speakWord(frontText) },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = "朗读",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // ── 反面 ──
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

                    Spacer(Modifier.height(16.dp))
                    FilledTonalIconButton(
                        onClick = { speakWord(frontText) },
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "朗读",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}
