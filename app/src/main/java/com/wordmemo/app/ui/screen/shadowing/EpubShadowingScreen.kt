package com.wordmemo.app.ui.screen.shadowing

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordmemo.app.data.tts.SentenceTTS
import com.wordmemo.app.domain.shadowing.model.ShadowingSentence
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

/**
 * EPUB / TXT 精听跟读页面。
 *
 * 与 ShadowingSessionScreen 不同：
 * - 无视频播放（使用 TTS 替代 ExoPlayer）
 * - 接收 ShadowingSentence 列表直接传入
 * - 保留录音/回放/波形对比
 *
 * 约束：不修改已有的 ShadowingSessionScreen 核心逻辑。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubShadowingScreen(
    sentences: List<ShadowingSentence>,
    bookTitle: String,
    onNavigateBack: () -> Unit = {},
    viewModel: EpubShadowingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSentenceListSheet by remember { mutableStateOf(false) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 初始化 — 传入句子
    LaunchedEffect(sentences) {
        viewModel.initialize(sentences, bookTitle)
    }

    // 监听 TTS 事件 → 自动推进到下一句
    LaunchedEffect(Unit) {
        viewModel.ttsEvents.consumeAsFlow().collect { event ->
            when (event) {
                is SentenceTTS.TtsEvent.SentenceCompleted -> {
                    viewModel.onTtsSentenceCompleted(event.index)
                }
                is SentenceTTS.TtsEvent.AllSentencesCompleted -> {
                    viewModel.onTtsAllCompleted()
                }
                else -> {}
            }
        }
    }

    // Error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "跟读 - ${uiState.bookTitle}",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopTts()
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showSpeedPicker = true }) {
                        Icon(Icons.Default.Speed, contentDescription = "播放速度")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // === TTS Playback Controls ===
            TtsPlaybackSection(
                isPlaying = uiState.isTtsPlaying,
                playbackSpeed = uiState.playbackSpeed,
                onTogglePlayPause = { viewModel.toggleTtsPlayPause() },
                onShowSpeedPicker = { showSpeedPicker = true },
                currentSentenceText = uiState.sentences.getOrNull(uiState.currentSentenceIndex)?.text ?: ""
            )

            // === Seek Bar ===
            SeekBarRow(
                currentPositionMs = uiState.currentSentenceProgressMs,
                durationMs = uiState.currentSentenceDurationMs,
                onSeek = { } // TTS 不支持 seek
            )

            val currentSentence = uiState.sentences.getOrNull(uiState.currentSentenceIndex)

            // === Sentence Display ===
            SentenceDisplaySection(
                currentIndex = uiState.currentSentenceIndex,
                totalCount = uiState.sentences.size,
                sentenceText = currentSentence?.text ?: "",
                modifier = Modifier.weight(0.12f)
            )

            // === Dual Waveform Comparison ===
            WaveformComparisonSection(
                hasRecording = uiState.hasRecording,
                recordedWaveform = uiState.recordedWaveform,
                isRecording = uiState.isRecording,
                recordingWaveform = uiState.waveformAmplitudes,
                isPlayingRecording = uiState.isPlayingRecording,
                recordingDurationMs = uiState.recordingDurationMs,
                modifier = Modifier.weight(0.25f)
            )

            // === Recording Controls ===
            RecordingControlBar(
                hasPermission = uiState.hasMicrophonePermission,
                recordingState = uiState.recordingState,
                isRecording = uiState.isRecording,
                hasRecording = uiState.hasRecording,
                isPlayingRecording = uiState.isPlayingRecording,
                recordingDurationMs = uiState.recordingDurationMs,
                onStartRecording = { viewModel.startRecording() },
                onStopRecording = { viewModel.stopRecording() },
                onPlayRecording = { viewModel.playRecording() },
                onDeleteRecording = { viewModel.deleteRecording() }
            )

            // === Playback Comparison ===
            PlaybackComparisonSection(
                hasRecording = uiState.hasRecording,
                activeAudioSource = uiState.activeAudioSource,
                isPlayingRecording = uiState.isPlayingRecording,
                onPlayOriginal = { viewModel.playOriginalSentence() },
                onPlayUserRecording = { viewModel.playUserRecording() },
                onSwitchAudioSource = { viewModel.switchAudioSource(it) }
            )

            // === Sentence Navigation ===
            SentenceNavigationRow(
                currentIndex = uiState.currentSentenceIndex,
                totalCount = uiState.sentences.size,
                onPrevious = { viewModel.previousSentence() },
                onNext = { viewModel.nextSentence() },
                onShowList = { showSentenceListSheet = true }
            )
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

    // Speed picker dialog
    if (showSpeedPicker) {
        SpeedPickerDialog(
            currentSpeed = uiState.playbackSpeed,
            onSelect = { viewModel.setPlaybackSpeed(it) },
            onDismiss = { showSpeedPicker = false }
        )
    }
}

// ==================== TTS Playback Section ====================

@Composable
private fun TtsPlaybackSection(
    isPlaying: Boolean,
    playbackSpeed: Float,
    onTogglePlayPause: () -> Unit,
    onShowSpeedPicker: () -> Unit,
    currentSentenceText: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Speed badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = onShowSpeedPicker
                ) {
                    Text(
                        text = "${playbackSpeed}x",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            // Current sentence
            if (currentSentenceText.isNotEmpty()) {
                Text(
                    text = currentSentenceText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // Play/Pause button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = {
                    // Seek backward (prev sentence)
                }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "上一句", modifier = Modifier.size(28.dp))
                }

                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = {
                    // Seek forward (next sentence)
                }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "下一句", modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

// ==================== Reused Shadowing UI ====================
// These components are copied from ShadowingSessionScreen to avoid
// modifying its core logic. They share identical behavior.

@Composable
private fun SeekBarRow(
    currentPositionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = formatTimestamp(currentPositionMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs) else 0f,
            onValueChange = { fraction -> onSeek((fraction * durationMs).toLong()) },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            enabled = false
        )
        Text(
            text = formatTimestamp(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "句子 ${currentIndex + 1} / $totalCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = sentenceText,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// === Waveform ===

@Composable
private fun WaveformComparisonSection(
    hasRecording: Boolean,
    recordedWaveform: List<Float>,
    isRecording: Boolean,
    recordingWaveform: List<Float>,
    isPlayingRecording: Boolean,
    recordingDurationMs: Long,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "录音中…",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFF44336)
                )
                Spacer(Modifier.height(4.dp))
                // Simple recording indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (i in recordingWaveform.indices) {
                        val height = (recordingWaveform[i] * 40).dp.coerceAtLeast(2.dp)
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(height)
                                .background(Color(0xFFF44336), RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        } else if (hasRecording) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isPlayingRecording) "录音回放中…" else "录音就绪",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF81C784)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatTimer(recordingDurationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = "点击录音按钮开始跟读",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// === Recording Controls ===

@Composable
private fun RecordingControlBar(
    hasPermission: Boolean,
    recordingState: RecordingState,
    isRecording: Boolean,
    hasRecording: Boolean,
    isPlayingRecording: Boolean,
    recordingDurationMs: Long,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPlayRecording: () -> Unit,
    onDeleteRecording: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isRecording) {
            // Stop recording
            FilledIconButton(
                onClick = onStopRecording,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFFF44336)
                )
            ) {
                Icon(Icons.Default.Stop, contentDescription = "停止录音", modifier = Modifier.size(28.dp))
            }
            Text(
                text = formatTimer(recordingDurationMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else if (hasRecording) {
            // Play / delete recording
            FilledIconButton(
                onClick = onPlayRecording,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isPlayingRecording)
                        Color(0xFFFFA726) else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    if (isPlayingRecording) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlayingRecording) "暂停回放" else "播放录音",
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(onClick = onDeleteRecording) {
                Icon(Icons.Default.Delete, contentDescription = "删除录音", tint = MaterialTheme.colorScheme.error)
            }
        } else {
            // Start recording
            FilledIconButton(
                onClick = onStartRecording,
                enabled = hasPermission,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Mic, contentDescription = "开始录音", modifier = Modifier.size(28.dp))
            }
        }
    }

    if (!hasPermission) {
        Text(
            text = "需要麦克风权限才能录音",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 24.dp)
        )
    }
}

// === Playback Comparison ===

@Composable
private fun PlaybackComparisonSection(
    hasRecording: Boolean,
    activeAudioSource: AudioSource,
    isPlayingRecording: Boolean,
    onPlayOriginal: () -> Unit,
    onPlayUserRecording: () -> Unit,
    onSwitchAudioSource: (AudioSource) -> Unit
) {
    AnimatedVisibility(visible = hasRecording) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Original audio (TTS)
            FilterChip(
                selected = activeAudioSource == AudioSource.ORIGINAL,
                onClick = onPlayOriginal,
                label = { Text("原声", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )

            // User recording
            FilterChip(
                selected = activeAudioSource == AudioSource.RECORDING,
                onClick = { onSwitchAudioSource(AudioSource.RECORDING) },
                label = { Text("我的录音", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
        }
    }
}

// === Sentence Navigation ===

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
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onPrevious,
            enabled = currentIndex > 0
        ) {
            Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("上一句", style = MaterialTheme.typography.labelMedium)
        }

        TextButton(onClick = onShowList) {
            Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("句子列表", style = MaterialTheme.typography.labelMedium)
        }

        TextButton(
            onClick = onNext,
            enabled = currentIndex < totalCount - 1
        ) {
            Text("下一句", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

// === Sentence List Bottom Sheet ===

@Composable
private fun SentenceListSheet(
    sentences: List<ShadowingSentence>,
    currentIndex: Int,
    onJumpToSentence: (Int) -> Unit
) {
    Column(modifier = Modifier.heightIn(max = 400.dp)) {
        Text(
            "句子列表",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(sentences) { index, sentence ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onJumpToSentence(index) },
                    color = if (index == currentIndex)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "${index + 1}. ${sentence.text}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedPickerDialog(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("播放速度", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                speeds.forEach { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(speed) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${speed}x",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (currentSpeed == speed) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (currentSpeed == speed) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ==================== Utility Functions ====================

private fun formatTimestamp(ms: Long): String {
    if (ms < 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}

private fun formatTimer(ms: Long): String {
    if (ms < 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}
