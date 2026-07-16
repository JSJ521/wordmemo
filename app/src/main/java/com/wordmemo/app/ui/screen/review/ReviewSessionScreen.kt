package com.wordmemo.app.ui.screen.review

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🎉 复习完成！",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        if (uiState.reviewedCount > 0) {
                            Text(
                                text = "本次复习: ${uiState.reviewedCount} 个单词",
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
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
                    color = Color.Gray,
                    fontSize = 14.sp
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
                        fontSize = 14.sp,
                        color = Color.Gray
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
