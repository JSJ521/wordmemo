package com.wordmemo.app.ui.screen.review

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordmemo.app.ui.component.FlashcardView
import com.wordmemo.app.ui.component.RatingBar
import com.wordmemo.app.ui.component.SkeletonLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSessionScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReviewSessionViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.startSession()
    }

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("复习") },
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
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.isLoading) {
                SkeletonLoader(modifier = Modifier.weight(1f))
            } else if (uiState.isSessionComplete) {
                ReviewCelebration(
                    reviewedCount = uiState.reviewedCount,
                    totalCards = uiState.totalCards,
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Progress indicator
                LinearProgressIndicator(
                    progress = {
                        if (uiState.totalCards > 0)
                            uiState.reviewedCount.toFloat() / uiState.totalCards
                        else 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Text(
                    text = "${uiState.reviewedCount + 1} / ${uiState.totalCards}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(16.dp))

                // Flashcard
                FlashcardView(
                    frontText = uiState.currentWord?.english ?: "",
                    frontPhonetic = uiState.currentWord?.phonetic ?: "",
                    backText = uiState.currentWord?.chinese ?: "",
                    isFlipped = uiState.isFlipped,
                    onFlip = { viewModel.flipCard() },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                )

                Spacer(Modifier.height(16.dp))

                // Rating buttons (only visible after flip)
                if (uiState.isFlipped) {
                    Text(
                            text = "还记得吗？",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    RatingBar(
                        onRate = { rating -> viewModel.rateCard(rating) },
                        cardState = uiState.currentCard?.state ?: "NEW",
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
private fun ReviewCelebration(
    reviewedCount: Int, totalCards: Int,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "celebration")
    val pulse = infinite.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 大星星 + 脉冲
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                modifier = Modifier
                    .size((48 * pulse.value).dp)
                    .padding(4.dp),
                tint = Color(0xFFFFD700)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "✨ 复习完成 ✨",
                style = TextStyle(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFFFD700), Color(0xFF4A6CF7), Color(0xFFFF8F00))
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            if (reviewedCount > 0) {
                Text(
                    text = "本次复习 $reviewedCount 个单词",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            if (totalCards > 0) {
                Text(
                    text = "完成率 ${reviewedCount * 100 / totalCards}%",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "日积月累，水滴石穿 🌊",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
