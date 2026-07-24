package com.wordmemo.app.ui.screen.reading

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordmemo.app.domain.shadowing.model.ShadowingSentence
import com.wordmemo.app.ui.navigation.EpubShadowingDataHolder
import kotlinx.coroutines.launch

/**
 * EPUB 精听阅读页。
 *
 * 功能：
 * - 原文逐句显示 + TTS 高亮同步
 * - TTS 播放控制（播放/暂停/停止/上一句/下一句/速度）
 * - 长按句子翻译（BottomSheet 显示中文翻译）
 * - 跟读入口（跳转到 EpubShadowingScreen）
 * - 章节切换
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReadingScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToShadowing: (List<ShadowingSentence>) -> Unit = {},
    viewModel: ReadingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showChapterPicker by remember { mutableStateOf(false) }
    var showTranslationSheet by remember { mutableStateOf(false) }
    var translationTargetIndex by remember { mutableIntStateOf(-1) }

    // 自动滚动到当前高亮句子
    LaunchedEffect(uiState.currentSentenceIndex) {
        if (uiState.currentSentenceIndex >= 0) {
            listState.animateScrollToItem(uiState.currentSentenceIndex)
        }
    }

    // 翻译 BottomSheet 显示
    LaunchedEffect(uiState.translation) {
        if (uiState.translation != null && showTranslationSheet) {
            // 翻译已就绪
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            uiState.currentBook?.title ?: "加载中…",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val chapter = uiState.currentBook?.chapters?.getOrNull(uiState.currentChapterIndex)
                        if (chapter != null) {
                            Text(
                                chapter.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 章节选择
                    IconButton(onClick = { showChapterPicker = true }) {
                        Icon(Icons.Default.List, contentDescription = "章节列表")
                    }
                    // 播放速度
                    IconButton(onClick = { showSpeedPicker = true }) {
                        Icon(Icons.Default.Speed, contentDescription = "速度")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.sentences.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "暂无内容",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // === 句子列表（主要阅读区域） ===
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        itemsIndexed(uiState.sentences) { index, sentence ->
                            SentenceItem(
                                index = index,
                                text = sentence,
                                isHighlighted = index == uiState.currentSentenceIndex,
                                isTtsPlaying = uiState.isTtsSpeaking,
                                ttsHighlightedText = uiState.ttsHighlightedSentence,
                                onClick = {
                                    viewModel.jumpToAndPlay(index)
                                },
                                onLongClick = {
                                    // 长按翻译
                                    translationTargetIndex = index
                                    showTranslationSheet = true
                                    viewModel.translateSentenceAtIndex(index)
                                }
                            )
                        }
                    }

                    // === 控制栏 ===
                    ReadingControlBar(
                        isTtsSpeaking = uiState.isTtsSpeaking,
                        canGoPrevious = uiState.currentSentenceIndex > 0,
                        canGoNext = uiState.currentSentenceIndex < uiState.sentences.size - 1,
                        hasSentences = uiState.sentences.isNotEmpty(),
                        isTranslating = uiState.isTranslating,
                        playbackSpeed = uiState.playbackSpeed,
                        onPlayPause = {
                            if (uiState.isTtsSpeaking) viewModel.pauseTts()
                            else viewModel.playTts()
                        },
                        onPrevious = { viewModel.jumpToAndPlay((uiState.currentSentenceIndex - 1).coerceAtLeast(0)) },
                        onNext = { viewModel.jumpToAndPlay((uiState.currentSentenceIndex + 1).coerceAtMost(uiState.sentences.size - 1)) },
                        onTranslate = { viewModel.translateCurrentSentence() },
                        onShowSpeedPicker = { showSpeedPicker = true },
                        onShadowing = {
                            // 将当前句子转换为 ShadowingSentence 传给跟读页
                            val currentIdx = uiState.currentSentenceIndex
                            if (currentIdx >= 0 && currentIdx < uiState.sentences.size) {
                                val sentences = uiState.sentences.mapIndexed { i, text ->
                                    ShadowingSentence(
                                        id = i.toLong(),
                                        videoId = -1L, // 标记为 EPUB 来源
                                        sentenceIndex = i,
                                        text = text,
                                        startTimeMs = (i * 3000L).toLong(),
                                        endTimeMs = ((i + 1) * 3000L).toLong()
                                    )
                                }
                                // 设置书名供跟读页面显示
                                EpubShadowingDataHolder.bookTitle =
                                    uiState.currentBook?.title ?: "未知书籍"
                                onNavigateToShadowing(sentences)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // === 章节切换按钮 ===
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val currentChapter = uiState.currentChapterIndex
                        val totalChapters = uiState.currentBook?.chapters?.size ?: 0

                        OutlinedButton(
                            onClick = { viewModel.previousChapter() },
                            enabled = currentChapter > 0
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("上一章")
                        }

                        Text(
                            "第 ${currentChapter + 1} / $totalChapters 章",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )

                        OutlinedButton(
                            onClick = { viewModel.nextChapter() },
                            enabled = currentChapter < totalChapters - 1
                        ) {
                            Text("下一章")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // 错误提示
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("关闭")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }

    // 速度选择弹窗
    if (showSpeedPicker) {
        SpeedPickerDialog(
            currentSpeed = uiState.playbackSpeed,
            onSelect = { viewModel.setPlaybackSpeed(it) },
            onDismiss = { showSpeedPicker = false }
        )
    }

    // 章节选择弹窗
    if (showChapterPicker) {
        val book = uiState.currentBook
        if (book != null) {
            ChapterPickerDialog(
                chapters = book.chapters.map { it.title },
                currentIndex = uiState.currentChapterIndex,
                onSelect = { index ->
                    viewModel.jumpToChapter(index)
                    showChapterPicker = false
                },
                onDismiss = { showChapterPicker = false }
            )
        }
    }

    // 翻译 BottomSheet
    if (showTranslationSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showTranslationSheet = false
                viewModel.clearTranslation()
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            TranslationSheetContent(
                sentenceText = uiState.sentences.getOrNull(translationTargetIndex) ?: "",
                translation = uiState.translation,
                isLoading = uiState.isTranslating,
                onDismiss = {
                    showTranslationSheet = false
                    viewModel.clearTranslation()
                }
            )
        }
    }
}

// ==================== 句子条目 ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SentenceItem(
    index: Int,
    text: String,
    isHighlighted: Boolean,
    isTtsPlaying: Boolean,
    ttsHighlightedText: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val backgroundColor = when {
        isHighlighted && isTtsPlaying -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        isHighlighted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    val fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                append("${index + 1}. ")
                pushStyle(SpanStyle(fontWeight = fontWeight))
                append(text)
                pop()
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (isHighlighted && isTtsPlaying)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurface
        )
    }
}

// ==================== 翻译 BottomSheet ====================

@Composable
private fun TranslationSheetContent(
    sentenceText: String,
    translation: String?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "翻译",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(20.dp))
            }
        }

        // 原文
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = sentenceText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 分隔
        HorizontalDivider()

        // 译文中
        when {
            isLoading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        "翻译中…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            translation != null -> {
                Text(
                    text = translation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            else -> {
                Text(
                    "翻译失败",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ==================== 控制栏 ====================

@Composable
private fun ReadingControlBar(
    isTtsSpeaking: Boolean,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    hasSentences: Boolean,
    isTranslating: Boolean,
    playbackSpeed: Float,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTranslate: () -> Unit,
    onShowSpeedPicker: () -> Unit,
    onShadowing: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp
    ) {
        Column {
            // 速度指示
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "${playbackSpeed}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onShowSpeedPicker)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            // 主控制按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 上一句
                IconButton(
                    onClick = onPrevious,
                    enabled = canGoPrevious
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "上一句")
                }

                // 播放/暂停
                FilledIconButton(
                    onClick = onPlayPause,
                    enabled = hasSentences,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isTtsSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isTtsSpeaking) "暂停" else "播放",
                        modifier = Modifier.size(32.dp)
                    )
                }

                // 下一句
                IconButton(
                    onClick = onNext,
                    enabled = canGoNext
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "下一句")
                }
            }

            // 辅助控制按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 翻译
                AssistChip(
                    onClick = onTranslate,
                    label = { Text(if (isTranslating) "翻译中…" else "翻译", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Translate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )

                // 跟读
                AssistChip(
                    onClick = onShadowing,
                    label = { Text("跟读", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

// ==================== 速度选择 ====================

@Composable
private fun SpeedPickerDialog(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
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

// ==================== 章节选择 ====================

@Composable
private fun ChapterPickerDialog(
    chapters: List<String>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择章节") },
        text = {
            LazyColumn {
                itemsIndexed(chapters) { index, title ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) },
                        color = if (index == currentIndex)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = "${index + 1}. $title",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
