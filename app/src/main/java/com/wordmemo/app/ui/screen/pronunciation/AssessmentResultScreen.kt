package com.wordmemo.app.ui.screen.pronunciation

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordmemo.app.domain.pronunciation.model.AssessmentRecord
import com.wordmemo.app.domain.pronunciation.model.PhonemeScore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentResultScreen(
    assessmentId: Long,
    onNavigateBack: () -> Unit = {},
    onNavigateToProgress: () -> Unit = {},
    viewModel: AssessmentResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(assessmentId) {
        viewModel.loadAssessment(assessmentId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "测评结果",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.error!!,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            val assessment = uiState.assessment
            if (assessment != null) {
                AssessmentResultContent(
                    assessment = assessment,
                    accuracyScore = viewModel.uiState.value.accuracyScore,
                    fluencyScore = viewModel.uiState.value.fluencyScore,
                    completenessScore = viewModel.uiState.value.completenessScore,
                    onNavigateToProgress = onNavigateToProgress
                )
            }
        }
    }
}

@Composable
private fun AssessmentResultContent(
    assessment: AssessmentRecord,
    accuracyScore: Int,
    fluencyScore: Int,
    completenessScore: Int,
    onNavigateToProgress: () -> Unit
) {
    val scoreColor = scoreColorFor(assessment.overallScore)
    val gradeLabel = assessment.scoreLevel

    // Count-up animation for score
    val animatedScore = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(assessment.overallScore) {
        animatedScore.animateTo(
            targetValue = assessment.overallScore.toFloat(),
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)
    ) {
        // Score section
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Score circle
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(scoreColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${animatedScore.value.toInt()}",
                                style = MaterialTheme.typography.displaySmall,
                                color = scoreColor,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "/ 100",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Grade label
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = gradeLabel,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = scoreColor.copy(alpha = 0.15f),
                            labelColor = scoreColor
                        )
                    )
                }
            }
        }

        // Dimension scores
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DimensionScoreCard(
                    label = "准确率",
                    score = accuracyScore,
                    modifier = Modifier.weight(1f)
                )
                DimensionScoreCard(
                    label = "流利度",
                    score = fluencyScore,
                    modifier = Modifier.weight(1f)
                )
                DimensionScoreCard(
                    label = "完整度",
                    score = completenessScore,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Phoneme analysis
        item {
            if (assessment.phonemeScores.isNotEmpty()) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "音素分析",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Target sentence display
                        Text(
                            text = assessment.sentenceText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Colored phoneme text
                        PhonemeColoredText(
                            text = assessment.sentenceText,
                            phonemeScores = assessment.phonemeScores
                        )

                        // Legend
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LegendItem(color = Color(0xFF4CAF50), label = "准确")
                            LegendItem(color = Color(0xFFFFB74D), label = "需改进")
                            LegendItem(color = Color(0xFFEF5350), label = "不准确")
                        }
                    }
                }
            } else {
                // Simple text display when no phoneme scores
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "句子原文",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = assessment.sentenceText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (assessment.transcribedText != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            Text(
                                "你的朗读",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = assessment.transcribedText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Correction suggestions
        item {
            if (assessment.correctionSuggestions != null) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.TipsAndUpdates,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "纠正建议",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Parse suggestions into separate cards
                        val suggestions = parseSuggestions(assessment.correctionSuggestions)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            suggestions.forEach { suggestion ->
                                SuggestionCard(text = suggestion)
                            }
                        }
                    }
                }
            }
        }

        // Action buttons
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = { /* navigate to re-record */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("重新测评（重录）")
                }
                TextButton(
                    onClick = onNavigateToProgress,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("查看进步趋势")
                }
            }
        }
    }
}

@Composable
private fun DimensionScoreCard(
    label: String,
    score: Int,
    modifier: Modifier = Modifier
) {
    val color = scoreColorFor(score)
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$score",
                style = MaterialTheme.typography.headlineSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PhonemeColoredText(
    text: String,
    phonemeScores: List<PhonemeScore>
) {
    // Group phoneme scores by word index to color words/syllables
    val annotatedText = buildAnnotatedString {
        if (phonemeScores.isEmpty()) {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                append(text)
            }
            return@buildAnnotatedString
        }

        // Simple approach: color individual words based on their phoneme scores
        val words = text.split(" ")
        words.forEachIndexed { wordIndex, word ->
            val wordPhonemes = phonemeScores.filter { it.wordIndex == wordIndex }
            val avgScore = if (wordPhonemes.isNotEmpty())
                wordPhonemes.map { it.gopScore }.average()
            else 0.0

            val wordColor = when {
                avgScore >= 0.7 -> Color(0xFF4CAF50)
                avgScore >= 0.5 -> Color(0xFFFFB74D)
                avgScore > 0.0 -> Color(0xFFEF5350)
                else -> MaterialTheme.colorScheme.onSurface
            }

            withStyle(SpanStyle(color = wordColor, fontWeight = FontWeight.Medium)) {
                append(word)
            }
            if (wordIndex < words.size - 1) {
                append(" ")
            }
        }
    }

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SuggestionCard(
    text: String
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.VolumeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun parseSuggestions(suggestionsStr: String): List<String> {
    if (suggestionsStr.isBlank()) return emptyList()
    // Try to split by newlines or numbered items first
    val items = suggestionsStr.split("\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { it.replace(Regex("^\\d+[.、]\\s*"), "") }
    return if (items.size >= 2) items else listOf(suggestionsStr)
}

private fun scoreColorFor(score: Int): Color = when {
    score >= 90 -> Color(0xFF4CAF50)
    score >= 75 -> Color(0xFFD0BCFF)
    score >= 60 -> Color(0xFFFFB74D)
    else -> Color(0xFFEF5350)
}
