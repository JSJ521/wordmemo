package com.wordmemo.app.ui.screen.shadowing

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.wordmemo.app.domain.shadowing.model.ShadowingSentence
import kotlinx.coroutines.delay

// ==================== 颜色常量 ====================
private val ColorOriginal = Color(0xFF4FC3F7)    // 浅蓝 — 原声波形
private val ColorRecording = Color(0xFF81C784)   // 浅绿 — 录音波形
private val ColorRecordingActive = Color(0xFFF44336) // 红色 — 录音中
private val ColorPlaybackActive = Color(0xFFFFA726)  // 橙色 — 回放中

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadowingSessionScreen(
    videoId: Long,
    onNavigateBack: () -> Unit = {},
    viewModel: ShadowingSessionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSentenceListSheet by remember { mutableStateOf(false) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Subtitle file picker for re-uploading subtitles
    val subtitleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.reUploadSubtitle(it) }
    }

    // 加载指定视频的句子数据
    LaunchedEffect(videoId) {
        viewModel.loadSentences(videoId)
    }

    // ExoPlayer lifecycle
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
        }
    }

    // Set up media when videoFilePath becomes available
    LaunchedEffect(uiState.videoFilePath) {
        if (uiState.videoFilePath.isNotEmpty()) {
            val mediaItem = MediaItem.fromUri(Uri.parse(uiState.videoFilePath))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }
    }

    // Sync play/pause state from ViewModel
    LaunchedEffect(uiState.isPlayingVideo) {
        if (uiState.isPlayingVideo) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // Sync seek commands from ViewModel
    LaunchedEffect(uiState.pendingSeekToSentenceMs) {
        val seekMs = uiState.pendingSeekToSentenceMs
        if (seekMs >= 0L) {
            exoPlayer.seekTo(seekMs)
            viewModel.onSeekCompleted()
        }
    }

    // Sync playback speed
    LaunchedEffect(uiState.playbackSpeed) {
        exoPlayer.setPlaybackSpeed(uiState.playbackSpeed)
    }

    // Sync seekTo position changes
    LaunchedEffect(uiState.currentPositionMs) {
        if (uiState.pendingSeekToSentenceMs < 0L) {
            val currentPlayerPos = exoPlayer.currentPosition
            val diff = kotlin.math.abs(uiState.currentPositionMs - currentPlayerPos)
            if (diff > 500) {
                exoPlayer.seekTo(uiState.currentPositionMs)
            }
        }
    }

    // Observe player position changes to sync with ViewModel
    LaunchedEffect(exoPlayer) {
        while (true) {
            val pos = exoPlayer.currentPosition
            viewModel.onPlaybackPositionChanged(pos)
            delay(250)
        }
    }

    // Observe player state changes
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> viewModel.pauseVideo()
                    else -> {}
                }
            }
            override fun onPlayerError(error: PlaybackException) {}
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying && uiState.isPlayingVideo) {
                    viewModel.pauseVideo()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Clean up ExoPlayer when leaving the screen
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    // Error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.pauseVideo()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.videoTitle.ifEmpty { "跟读训练" },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else if (uiState.sentences.isEmpty()) {
            EmptySentencesState(
                subtitlePath = uiState.videoSubtitlePath,
                onSelectSubtitleFile = {
                    subtitleFilePickerLauncher.launch(arrayOf("text/*", "application/x-subrip", "*/*"))
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // === Video Player Area (16:9) ===
                VideoPlayerSection(
                    exoPlayer = exoPlayer,
                    isPlaying = uiState.isPlayingVideo,
                    playbackSpeed = uiState.playbackSpeed,
                    currentSentenceText = uiState.sentences.getOrNull(uiState.currentSentenceIndex)?.text ?: "",
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onSeekBackward = { viewModel.seekBackward() },
                    onSeekForward = { viewModel.seekForward() },
                    onShowSpeedPicker = { showSpeedPicker = true }
                )

                // === Seek Bar ===
                SeekBarRow(
                    currentPositionMs = uiState.currentPositionMs,
                    durationMs = uiState.videoDurationMs,
                    onSeek = { viewModel.seekTo(it) }
                )

                val currentSentence = uiState.sentences.getOrNull(uiState.currentSentenceIndex)

                // === Sentence Display ===
                SentenceDisplaySection(
                    currentIndex = uiState.currentSentenceIndex,
                    totalCount = uiState.sentences.size,
                    sentenceText = currentSentence?.text ?: "",
                    modifier = Modifier.weight(0.15f)
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

// ==================== Empty Sentences State ====================

@Composable
private fun EmptySentencesState(
    subtitlePath: String?,
    onSelectSubtitleFile: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Subtitles,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = "暂无句子数据",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "请选择字幕文件或导入带内嵌字幕的视频",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = onSelectSubtitleFile,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("选择字幕文件")
            }
        }
    }
}

// ==================== Video Player Section ====================

@Composable
private fun VideoPlayerSection(
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    playbackSpeed: Float,
    currentSentenceText: String,
    onTogglePlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onShowSpeedPicker: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!isPlaying) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "播放",
                    modifier = Modifier.size(64.dp),
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.6f),
            onClick = onShowSpeedPicker
        ) {
            Text(
                text = "${playbackSpeed}x",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        if (currentSentenceText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = currentSentenceText,
                    style = MaterialTheme.typography.titleMedium.merge(
                        TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(1f, 1f), 3f))
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Touch controls overlay
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSeekBackward, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Replay10, contentDescription = "后退10秒", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
            }
            FilledIconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(onClick = onSeekForward, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Forward10, contentDescription = "前进10秒", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
            }
        }
    }
}

// ==================== Seek Bar ====================

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
            )
        )
        Text(
            text = formatTimestamp(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ==================== Sentence Display ====================

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

// ==================== Dual Waveform Comparison ====================

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
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // 原声波形占位提示（当前通过 ExoPlayer 播放视频，暂不显示实时FFT）
        // 后续可加 Visualizer API 实时波形

        // 录音波形（录制中实时 / 已完成固定）
        if (isRecording) {
            // 录制中：实时振幅条
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = ColorRecordingActive,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "录音中",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorRecordingActive,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatTimer(recordingDurationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorRecordingActive
                )
            }
            Spacer(Modifier.height(4.dp))
            LiveWaveformBars(
                amplitudes = recordingWaveform,
                color = ColorRecordingActive,
                modifier = Modifier.fillMaxWidth().height(40.dp)
            )
        } else if (hasRecording && recordedWaveform.isNotEmpty()) {
            // 已有录音：显示固定波形
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Equalizer,
                    contentDescription = null,
                    tint = if (isPlayingRecording) ColorPlaybackActive else ColorRecording,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (isPlayingRecording) "正在播放录音" else "录音波形",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPlayingRecording) ColorPlaybackActive else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                if (isPlayingRecording) {
                    Text(
                        text = formatTimer(recordingDurationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorPlaybackActive
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            RecordedWaveformView(
                amplitudes = recordedWaveform,
                color = if (isPlayingRecording) ColorPlaybackActive else ColorRecording,
                modifier = Modifier.fillMaxWidth().height(40.dp)
            )

            // 录音+原声双波形对比（简化：仅显示录音波形，原声波形后续通过 Visualizer 加）
            Text(
                text = "提示：点击下方「原文」播放原声，点击「我的录音」播放录音，对比发音差异",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            // 无录音时：显示操作提示
            Box(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "点击下方麦克风按钮开始录音",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * 实时波形条 — 录音中显示不断滚动的振幅条。
 */
@Composable
private fun LiveWaveformBars(
    amplitudes: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val barWidth = size.width / amplitudes.size.coerceAtLeast(1)
        val midY = size.height / 2
        val maxHeight = size.height * 0.9f

        amplitudes.forEachIndexed { index, amp ->
            val barHeight = maxHeight * amp.coerceIn(0.02f, 1f)
            val x = index * barWidth + 2f
            val w = (barWidth - 4f).coerceAtLeast(1f)

            // 从底部向上生长
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(w, barHeight),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }
}

/**
 * 已录制的固定波形 — 显示完整的录音振幅轮廓。
 */
@Composable
private fun RecordedWaveformView(
    amplitudes: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val barWidth = size.width / amplitudes.size.coerceAtLeast(1)
        val midY = size.height / 2
        val maxHeight = size.height * 0.85f

        amplitudes.forEachIndexed { index, amp ->
            val barHeight = maxHeight * amp.coerceIn(0.02f, 1f)
            val x = index * barWidth + 1f
            val w = (barWidth - 2f).coerceAtLeast(1f)

            // 居中对称波形
            drawRoundRect(
                color = color.copy(alpha = 0.8f),
                topLeft = Offset(x, midY - barHeight / 2),
                size = Size(w, barHeight),
                cornerRadius = CornerRadius(1f, 1f)
            )
        }
    }
}

// ==================== Recording Controls ====================

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
    val buttonColor by animateColorAsState(
        targetValue = when {
            isRecording -> ColorRecordingActive
            hasRecording -> ColorRecording
            else -> MaterialTheme.colorScheme.primary
        },
        label = "recBtnColor"
    )

    val statusText = when {
        !hasPermission -> "需要麦克风权限"
        isRecording -> "点击停止"
        hasRecording && isPlayingRecording -> "正在播放"
        hasRecording -> "已录音，点击回放"
        else -> "朗读上方句子"
    }

    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 删除按钮
            if (hasRecording && !isRecording) {
                IconButton(
                    onClick = onDeleteRecording,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除录音",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Spacer(Modifier.size(36.dp))
            }

            // 录音/回放主按钮
            Box(contentAlignment = Alignment.Center) {
                if (isRecording) {
                    // 脉冲光环
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val ringAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f, targetValue = 0f,
                        animationSpec = infiniteRepeatable(tween(1200, easing = EaseOutCubic), RepeatMode.Restart),
                        label = "ringAlpha"
                    )
                    val ringScale by infiniteTransition.animateFloat(
                        initialValue = 1f, targetValue = 1.6f,
                        animationSpec = infiniteRepeatable(tween(1200, easing = EaseOutCubic), RepeatMode.Restart),
                        label = "ringScale"
                    )
                    Canvas(modifier = Modifier.size(100.dp)) {
                        drawCircle(
                            color = ColorRecordingActive.copy(alpha = ringAlpha),
                            radius = 28.dp.toPx() * ringScale,
                            center = Offset(size.width / 2, size.height / 2),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }

                IconButton(
                    onClick = {
                        when {
                            isRecording -> onStopRecording()
                            hasRecording && isPlayingRecording -> onPlayRecording() // 切换
                            hasRecording -> onPlayRecording()
                            else -> onStartRecording()
                        }
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(buttonColor),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(
                        imageVector = when {
                            isRecording -> Icons.Default.Stop
                            isPlayingRecording -> Icons.Default.Pause
                            hasRecording -> Icons.Default.PlayArrow
                            else -> Icons.Default.Mic
                        },
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // 录音时长 / 占位
            Text(
                text = if (isRecording || hasRecording) formatTimer(recordingDurationMs) else "",
                style = MaterialTheme.typography.labelMedium,
                color = if (isRecording) ColorRecordingActive else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(48.dp)
            )
        }

        // 状态文本
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = -16.dp)
        )
    }
}

// ==================== Playback Comparison ====================

@OptIn(ExperimentalMaterial3Api::class)
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
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                        selected = activeAudioSource == AudioSource.ORIGINAL && !isPlayingRecording,
                        onClick = { onSwitchAudioSource(AudioSource.ORIGINAL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("原文", style = MaterialTheme.typography.labelSmall)
                    }
                    SegmentedButton(
                        selected = activeAudioSource == AudioSource.RECORDING || isPlayingRecording,
                        onClick = { onSwitchAudioSource(AudioSource.RECORDING) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("我的录音", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                // 播放原文 - 控制 ExoPlayer
                AssistChip(
                    onClick = onPlayOriginal,
                    label = { Text("播放原文", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (activeAudioSource == AudioSource.ORIGINAL && !isPlayingRecording)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                // 播放录音
                AssistChip(
                    onClick = onPlayUserRecording,
                    label = {
                        Text(
                            if (isPlayingRecording) "暂停" else "播放录音",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (isPlayingRecording) Icons.Default.Pause else Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isPlayingRecording) ColorPlaybackActive
                            else if (activeAudioSource == AudioSource.RECORDING) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
    }
}

// ==================== Sentence Navigation ====================

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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious, enabled = currentIndex > 0) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "上一句")
        }

        IconButton(onClick = onShowList) {
            Icon(Icons.Default.List, contentDescription = "句子列表")
        }

        Text(
            text = "${currentIndex + 1} / $totalCount",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        IconButton(onClick = onNext, enabled = currentIndex < totalCount - 1) {
            Icon(Icons.Default.SkipNext, contentDescription = "下一句")
        }
    }
}

// ==================== Sentence List Sheet ====================

@Composable
private fun SentenceListSheet(
    sentences: List<ShadowingSentence>,
    currentIndex: Int,
    onJumpToSentence: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        Text(
            text = "句子列表",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
        LazyColumn {
            itemsIndexed(sentences) { index, sentence ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = "${index + 1}. ${sentence.text}",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(
                            text = formatTimestamp(sentence.startTimeMs),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (index == currentIndex)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.clickable { onJumpToSentence(index) }
                )
            }
        }
    }
}

// ==================== Speed Picker Dialog ====================

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
