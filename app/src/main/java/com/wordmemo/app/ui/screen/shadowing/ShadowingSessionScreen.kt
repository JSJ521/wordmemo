package com.wordmemo.app.ui.screen.shadowing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordmemo.app.domain.shadowing.model.ShadowingSentence

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadowingSessionScreen(
    videoId: Long,
    onNavigateBack: () -> Unit = {},
    viewModel: ShadowingSessionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSentenceListSheet by remember { mutableStateOf(false) }

    LaunchedEffect(videoId) {
        viewModel.loadSentences(videoId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "跟读训练",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { /* 设置 */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
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
        } else if (uiState.sentences.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无句子数据",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val currentSentence = uiState.sentences.getOrNull(uiState.currentSentenceIndex)

                // Sentence display area (25%)
                SentenceDisplaySection(
                    currentIndex = uiState.currentSentenceIndex,
                    totalCount = uiState.sentences.size,
                    sentenceText = currentSentence?.text ?: "",
                    modifier = Modifier.weight(0.25f)
                )

                // Recording area with waveform (40%)
                RecordingArea(
                    recordingState = uiState.recordingState,
                    isRecording = uiState.isRecording,
                    hasRecording = uiState.hasRecording,
                    waveformAmplitudes = uiState.waveformAmplitudes,
                    recordingDurationMs = uiState.recordingDurationMs,
                    onStartRecording = { viewModel.startRecording() },
                    onStopRecording = { viewModel.stopRecording() },
                    onPlayRecording = { viewModel.playRecording() },
                    onDeleteRecording = { viewModel.deleteRecording() },
                    modifier = Modifier.weight(0.40f)
                )

                // Playback comparison
                PlaybackComparisonSection(
                    hasRecording = uiState.hasRecording,
                    recordingState = uiState.recordingState,
                    activeAudioSource = uiState.activeAudioSource,
                    recordingDurationMs = uiState.recordingDurationMs,
                    onPlayOriginal = { viewModel.playOriginalSentence() },
                    onPlayUserRecording = { viewModel.playUserRecording() },
                    onSwitchAudioSource = { viewModel.switchAudioSource(it) }
                )

                Spacer(modifier = Modifier.weight(1f))

                // Sentence navigation
                SentenceNavigationRow(
                    currentIndex = uiState.currentSentenceIndex,
                    totalCount = uiState.sentences.size,
                    onPrevious = { viewModel.previousSentence() },
                    onNext = { viewModel.nextSentence() },
                    onShowList = { showSentenceListSheet = true }
                )
            }
        }
    }

    // Sentence list bottom sheet
    if (showSentenceListSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSentenceListSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SentenceListSheet(
                sentences = uiState.sentences,
                currentIndex = uiState.currentSentenceIndex,
                onJumpToSentence = { index ->
                    viewModel.jumpToSentence(index)
                    showSentenceListSheet = false
                }
            )
        }
    }
}

@Composable
private fun SentenceDisplaySection(
    currentIndex: Int,
    totalCount: Int,
    sentenceText: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "第 ${currentIndex + 1} / $totalCount 句",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = sentenceText,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun RecordingArea(
    recordingState: RecordingState,
    isRecording: Boolean,
    hasRecording: Boolean,
    waveformAmplitudes: List<Float>,
    recordingDurationMs: Long,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPlayRecording: () -> Unit,
    onDeleteRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val buttonColor by animateColorAsState(
        targetValue = when {
            isRecording -> Color(0xFFF44336)
            hasRecording -> Color(0xFF4CAF50)
            else -> MaterialTheme.colorScheme.primary
        },
        label = "buttonColor"
    )

    val statusText = when {
        isRecording -> "录音中..."
        hasRecording -> "回放中"
        else -> "点击录音，朗读上方句子"
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Pulse ring animation when recording
            Box(
                contentAlignment = Alignment.Center
            ) {
                if (isRecording) {
                    val ringAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = EaseOutCubic),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "ringAlpha"
                    )
                    val ringScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = EaseOutCubic),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "ringScale"
                    )
                    Canvas(modifier = Modifier.size(120.dp)) {
                        val cx = size.width / 2
                        val cy = size.height / 2
                        val radius = 36.dp.toPx() * ringScale
                        drawCircle(
                            color = Color(0xFFF44336).copy(alpha = ringAlpha),
                            radius = radius,
                            center = Offset(cx, cy),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }

                // Recording button
                IconButton(
                    onClick = {
                        when {
                            isRecording -> onStopRecording()
                            hasRecording -> onPlayRecording()
                            else -> onStartRecording()
                        }
                    },
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(buttonColor),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = when {
                            isRecording -> Icons.Default.Stop
                            hasRecording -> Icons.Default.PlayArrow
                            else -> Icons.Default.Mic
                        },
                        contentDescription = if (isRecording) "停止录音" else "开始录音",
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Status text & recording timer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isRecording) {
                    Text(
                        text = formatTimer(recordingDurationMs),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFF44336),
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Waveform visualization
            if (isRecording && waveformAmplitudes.isNotEmpty()) {
                WaveformView(
                    amplitudes = waveformAmplitudes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 32.dp)
                )
            }

            // Delete recording button
            if (hasRecording && !isRecording) {
                TextButton(
                    onClick = onDeleteRecording,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除录音", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun WaveformView(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val barWidth = size.width / amplitudes.size
        val maxHeight = size.height * 0.9f
        val midY = size.height / 2

        amplitudes.forEachIndexed { index, amplitude ->
            val barHeight = maxHeight * amplitude.coerceIn(0.05f, 1f)
            val x = index * barWidth + barWidth * 0.15f
            val barW = barWidth * 0.7f

            drawRect(
                color = primaryColor,
                topLeft = Offset(x, midY - barHeight / 2),
                size = androidx.compose.ui.geometry.Size(barW, barHeight)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackComparisonSection(
    hasRecording: Boolean,
    recordingState: RecordingState,
    activeAudioSource: AudioSource,
    recordingDurationMs: Long,
    onPlayOriginal: () -> Unit,
    onPlayUserRecording: () -> Unit,
    onSwitchAudioSource: (AudioSource) -> Unit
) {
    val showSection = hasRecording || recordingState == RecordingState.PLAYBACK

    AnimatedVisibility(visible = showSection) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "回放对比",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))

                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = activeAudioSource == AudioSource.ORIGINAL,
                        onClick = { onSwitchAudioSource(AudioSource.ORIGINAL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("原文", style = MaterialTheme.typography.labelSmall)
                    }
                    SegmentedButton(
                        selected = activeAudioSource == AudioSource.RECORDING,
                        onClick = { onSwitchAudioSource(AudioSource.RECORDING) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("我的录音", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPlayOriginal) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "播放原声",
                            tint = if (activeAudioSource == AudioSource.ORIGINAL)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onPlayUserRecording) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "播放录音",
                            tint = if (activeAudioSource == AudioSource.RECORDING)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (hasRecording) {
                    Text(
                        text = "最近录音: ${formatTimer(recordingDurationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SentenceNavigationRow(
    currentIndex: Int,
    totalCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShowList: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onPrevious,
            enabled = currentIndex > 0,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                Icons.Default.ChevronLeft,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("上一句")
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onShowList) {
            Icon(
                Icons.Default.List,
                contentDescription = "句子列表",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.weight(1f))

        OutlinedButton(
            onClick = onNext,
            enabled = currentIndex < totalCount - 1,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("下一句")
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SentenceListSheet(
    sentences: List<ShadowingSentence>,
    currentIndex: Int,
    onJumpToSentence: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.padding(bottom = 32.dp)
    ) {
        itemsIndexed(sentences) { index, sentence ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onJumpToSentence(index) }
                    .background(
                        if (index == currentIndex)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        else Color.Transparent
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (index == currentIndex) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                } else {
                    Spacer(Modifier.width(3.dp))
                }

                Text(
                    text = formatTimestamp(sentence.startTimeMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp)
                )

                Text(
                    text = sentence.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (index == currentIndex)
                        MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (index <= currentIndex) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                }
            }
            if (index < sentences.size - 1) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }
        }
    }
}

private fun formatTimer(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}

private fun formatTimestamp(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
