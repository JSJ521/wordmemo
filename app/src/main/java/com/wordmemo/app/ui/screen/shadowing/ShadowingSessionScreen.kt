package com.wordmemo.app.ui.screen.shadowing

import android.net.Uri
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.wordmemo.app.domain.shadowing.model.ShadowingSentence
import kotlinx.coroutines.delay

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

    // Sync seekTo position changes (from seekForward/seekBackward)
    LaunchedEffect(uiState.currentPositionMs) {
        // Only seek if not triggered by a sentence change (pendingSeekToSentenceMs handles that)
        if (uiState.pendingSeekToSentenceMs < 0L) {
            val currentPlayerPos = exoPlayer.currentPosition
            val diff = kotlin.math.abs(uiState.currentPositionMs - currentPlayerPos)
            if (diff > 500) { // If the diff is >500ms, it's a user-initiated seek, not a position update
                exoPlayer.seekTo(uiState.currentPositionMs)
            }
        }
    }

    // Observe player position changes to sync with ViewModel
    LaunchedEffect(exoPlayer) {
        while (true) {
            val pos = exoPlayer.currentPosition
            viewModel.onPlaybackPositionChanged(pos)
            delay(250) // Update every 250ms
        }
    }

    // Observe player state changes
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> { /* Player ready */ }
                    Player.STATE_ENDED -> {
                        viewModel.pauseVideo()
                    }
                    Player.STATE_BUFFERING -> { /* Buffering */ }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Player error — handled silently
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying && uiState.isPlayingVideo) {
                    viewModel.pauseVideo()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Clean up ExoPlayer when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Scaffold(
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

                // === Sentence Display (25% of remaining space) ===
                SentenceDisplaySection(
                    currentIndex = uiState.currentSentenceIndex,
                    totalCount = uiState.sentences.size,
                    sentenceText = currentSentence?.text ?: "",
                    modifier = Modifier.weight(0.20f)
                )

                // === Recording Area (40% of remaining) ===
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
                    modifier = Modifier.weight(0.35f)
                )

                // === Playback Comparison ===
                PlaybackComparisonSection(
                    hasRecording = uiState.hasRecording,
                    recordingState = uiState.recordingState,
                    activeAudioSource = uiState.activeAudioSource,
                    recordingDurationMs = uiState.recordingDurationMs,
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
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedPicker = false },
            shape = MaterialTheme.shapes.large,
            title = {
                Text("播放速度", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column {
                    speeds.forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setPlaybackSpeed(speed)
                                    showSpeedPicker = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${speed}x",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (uiState.playbackSpeed == speed)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            if (uiState.playbackSpeed == speed) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedPicker = false }) {
                    Text("取消")
                }
            }
        )
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
        // ExoPlayer surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // We use custom controls
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Play overlay (shown when paused)
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

        // Playback speed chip (top-right)
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
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

        // Current sentence text overlay (bottom-center)
        if (currentSentenceText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = currentSentenceText,
                    style = MaterialTheme.typography.titleMedium.merge(
                        TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                offset = Offset(1f, 1f),
                                blurRadius = 3f
                            )
                        )
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Touch controls overlay
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Backward 10s
            IconButton(
                onClick = onSeekBackward,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Replay10,
                    contentDescription = "后退10秒",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp)
                )
            }

            // Play/Pause
            FilledIconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(32.dp)
                )
            }

            // Forward 10s
            IconButton(
                onClick = onSeekForward,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Forward10,
                    contentDescription = "前进10秒",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp)
                )
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
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
            onValueChange = { fraction ->
                onSeek((fraction * durationMs).toLong())
            },
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
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
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

// ==================== Recording Area ====================

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
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

// ==================== Playback Comparison ====================

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
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
            .padding(horizontal = 20.dp, vertical = 12.dp)
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

// ==================== Sentence List Sheet ====================

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

// ==================== Utility Functions ====================

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
