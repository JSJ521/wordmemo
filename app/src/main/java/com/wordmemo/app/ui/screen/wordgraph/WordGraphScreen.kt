package com.wordmemo.app.ui.screen.wordgraph

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.*

// ─── 配色方案（Composable 级，跟随主题）───
private class GraphColors(
    val bg: () -> Color, val topBar: () -> Color, val center: () -> Color,
    val guide: () -> Color, val edge: () -> Color, val bookmark: Color,
    val typeColors: Map<String, Color>, val error: () -> Color, val subtitle: () -> Color
)

@Composable
private fun rememberGraphColors(): GraphColors {
    val c = MaterialTheme.colorScheme
    return remember(c) { GraphColors(
        bg = { c.background }, topBar = { c.surface }, center = { c.onBackground },
        guide = { c.outlineVariant }, edge = { c.outline }, bookmark = Color(0xFFFFCA28),
        typeColors = mapOf("synonym" to c.primary, "antonym" to c.error,
            "collocation" to c.tertiary, "similar" to c.secondary, "concept" to Color(0xFFE65100)),
        error = { c.error }, subtitle = { c.onSurfaceVariant }
    )}}


// 关系类型中文标签
private val typeLabels = mapOf(
    "synonym" to "近义词",
    "antonym" to "反义词",
    "collocation" to "常用搭配",
    "similar" to "形近词",
    "concept" to "相关概念"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordGraphScreen(
    wordId: Long,
    onNavigateBack: () -> Unit,
    viewModel: WordGraphViewModel = viewModel()
) {
    LaunchedEffect(wordId) { viewModel.loadWord(wordId) }
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val gc = rememberGraphColors()

    LaunchedEffect(uiState.lastBookmarked) {
        uiState.lastBookmarked?.let { label ->
            snackbarHostState.showSnackbar(
                message = "✓ 已收藏: $label",
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.centerWord?.english?.let { "关系网: $it" } ?: "关系图谱",
                        color = gc.center()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = gc.subtitle())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = gc.topBar())
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(gc.bg())
        ) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = gc.center())
                }
                uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠️", fontSize = 28.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(uiState.error ?: "", color = gc.error(), fontSize = 12.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                    }
                }
                uiState.graph.nodes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无关联词汇", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                else -> RadialTextGraph(
                    graph = uiState.graph,
                    bookmarkedLabels = uiState.bookmarkedLabels,
                    onBookmark = { label, chinese -> viewModel.addWordToBook(label, chinese) },
                    gc = gc
                )
            }
        }
    }
}

// ═══════════════════════════════════════════
//  纯文字径向关系网
// ═══════════════════════════════════════════
@Composable
private fun RadialTextGraph(
    graph: MultiLevelGraph,
    bookmarkedLabels: Set<String>,
    onBookmark: (String, String) -> Unit,
    gc: GraphColors
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var dragNodeId by remember { mutableStateOf<String?>(null) }

    val nodePositions = remember(graph.nodes.size) {
        calculateRadialPositions(graph.nodes, graph.edges)
    }

    // 预计算每段文字尺寸（用于命中检测）
    val nodeSizes = remember(nodePositions, graph.nodes) {
        graph.nodes.map { node ->
            val engLen = node.label.length.coerceAtMost(10)
            val cnLen = node.chinese.length.coerceAtMost(14)
            val fontSize = when (node.level) {
                0 -> 16f; 1 -> 14f; else -> 12f
            }
            val cnFontSize = 9.5f
            // 估算文字宽度：英文字母≈0.6倍字号，中文字≈1倍字号
            val engWidth = engLen * fontSize * 0.6f
            val cnWidth = cnLen * cnFontSize * 1.0f
            val maxWidth = maxOf(engWidth, cnWidth) + 16f
            val totalHeight = fontSize + cnFontSize + 6f
            maxWidth to totalHeight
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.25f, 4f)
                    panOffset = Offset(panOffset.x + pan.x, panOffset.y + pan.y)
                }
            }
            .pointerInput(graph.nodes, nodePositions, bookmarkedLabels) {
                val cs = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())
                detectTapGestures(
                    onTap = { pos ->
                        hitText(pos, graph.nodes, nodePositions, scale, panOffset, cs, nodeSizes)?.let { node ->
                            if (node.label !in bookmarkedLabels) onBookmark(node.label, node.chinese)
                        }
                    },
                    onLongPress = { pos ->
                        hitText(pos, graph.nodes, nodePositions, scale, panOffset, cs, nodeSizes)?.let { node ->
                            onBookmark(node.label, node.chinese)
                        }
                    }
                )
            }
            .pointerInput(graph.nodes, nodePositions) {
                val cs = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())
                detectDragGestures(
                    onDragStart = { offset ->
                        dragNodeId = hitTestId(offset, graph.nodes, nodePositions, scale, panOffset, cs, nodeSizes)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val idx = dragNodeId?.let { id -> graph.nodes.indexOfFirst { it.id == id } }
                        if (idx != null && idx >= 0 && idx < nodePositions.size) {
                            nodePositions[idx] = Offset(
                                nodePositions[idx].x + dragAmount.x / scale,
                                nodePositions[idx].y + dragAmount.y / scale
                            )
                        }
                    },
                    onDragEnd = { dragNodeId = null }
                )
            }
    ) {
        val cx = size.width / 2f + panOffset.x
        val cy = size.height / 2f + panOffset.y
        val nodes = graph.nodes
        if (nodePositions.size != nodes.size) return@Canvas
        val density = this.density

        // 1. 引导环（极淡灰）
        drawGuideCircles(cx, cy, scale, density, gc)
        // 2. 连接线（浅灰贝塞尔曲线）
        drawEdges(graph, nodes, nodePositions, cx, cy, scale, density, gc)
        // 3. 文字节点（纯文字，无圆圈）
        drawTextNodes(nodes, nodePositions, cx, cy, scale, density, bookmarkedLabels, dragNodeId, gc)
    }
}

// ═══════════════════════════════════════════
//  位置计算
// ═══════════════════════════════════════════
private fun calculateRadialPositions(
    nodes: List<GraphNode>,
    edges: List<GraphEdge>
): MutableList<Offset> {
    val positions = MutableList(nodes.size) { Offset.Zero }
    val levelGroups = nodes.groupBy { it.level }
    val random = java.util.Random(42)
    for (i in nodes.indices) { if (nodes[i].level == 0) positions[i] = Offset.Zero }
    for (node in levelGroups[1] ?: emptyList()) {
        val idx = nodes.indexOfFirst { it.id == node.id }
        if (idx < 0) continue
        val angle = node.branchIndex * 2 * PI / 7 - PI / 2
        positions[idx] = Offset((140f * cos(angle)).toFloat(), (140f * sin(angle)).toFloat())
    }
    for ((branchIdx, branchNodes) in (levelGroups[2] ?: emptyList()).groupBy { it.branchIndex }) {
        val parentAngle = branchIdx * 2 * PI / 7 - PI / 2
        val span = 20f * PI / 180f
        val count = branchNodes.size
        for ((ci, child) in branchNodes.withIndex()) {
            val idx = nodes.indexOfFirst { it.id == child.id }
            if (idx < 0) continue
            val offsetAngle = if (count == 1) 0f else (-span + ci * 2f * span / (count - 1)).toFloat()
            val childAngle = parentAngle + offsetAngle
            val rOffset = (random.nextFloat() - 0.5f) * 20f
            positions[idx] = Offset(
                ((260f + rOffset) * cos(childAngle)).toFloat(),
                ((260f + rOffset) * sin(childAngle)).toFloat()
            )
        }
    }
    return positions
}

// ═══════════════════════════════════════════
//  绘制
// ═══════════════════════════════════════════

private fun DrawScope.drawGuideCircles(cx: Float, cy: Float, scale: Float, density: Float, gc: GraphColors) {
    val paint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 0.8f * density
        isAntiAlias = true
        this.color = gc.guide().toArgb()
    }
    for (r in listOf(140f, 240f)) {
        drawContext.canvas.nativeCanvas.drawCircle(cx, cy, r * scale, paint)
    }
}

private fun DrawScope.drawEdges(
    graph: MultiLevelGraph, nodes: List<GraphNode>, positions: List<Offset>,
    cx: Float, cy: Float, scale: Float, density: Float, gc: GraphColors
) {
    for (edge in graph.edges) {
        val fi = nodes.indexOfFirst { it.id == edge.from }
        val ti = nodes.indexOfFirst { it.id == edge.to }
        if (fi < 0 || ti < 0 || fi >= positions.size || ti >= positions.size) continue
        val from = Offset(cx + positions[fi].x * scale, cy + positions[fi].y * scale)
        val to   = Offset(cx + positions[ti].x * scale, cy + positions[ti].y * scale)
        val alpha = if (nodes[ti].level == 1) 0.5f else 0.3f
        val mid = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f)
        val dx = to.x - from.x; val dy = to.y - from.y
        val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val cp = Offset(mid.x - dy / dist * dist * 0.12f, mid.y + dx / dist * dist * 0.12f)
        drawPath(
            Path().apply {
                moveTo(from.x, from.y)
                quadraticBezierTo(cp.x, cp.y, to.x, to.y)
            },
            gc.edge().copy(alpha = alpha),
            style = Stroke(width = 1.2f * density, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

private fun DrawScope.drawTextNodes(
    nodes: List<GraphNode>, positions: List<Offset>,
    cx: Float, cy: Float, scale: Float, density: Float,
    bookmarkedLabels: Set<String>, dragNodeId: String?, gc: GraphColors
) {
    for (i in nodes.indices) {
        if (i >= positions.size) continue
        val node = nodes[i]
        val px = cx + positions[i].x * scale
        val py = cy + positions[i].y * scale

        val engSize = when (node.level) {
            0 -> 16f * density
            1 -> 14f * density
            else -> 12f * density
        }
        val cnSize = 9.5f * density
        val isDrag = dragNodeId == node.id

        // 英文（根据类型着色）
        val engColor = if (node.level == 0) gc.center()
            else gc.typeColors[node.type] ?: gc.subtitle()
        val label = if (node.label.length > 12) node.label.take(11) + "…" else node.label

        drawContext.canvas.nativeCanvas.apply {
            // 英文
            val ePaint = android.graphics.Paint().apply {
                textSize = engSize
                this.color = engColor.toArgb()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                typeface = if (node.level == 0) android.graphics.Typeface.DEFAULT_BOLD
                    else android.graphics.Typeface.DEFAULT
                if (isDrag) { alpha = 180 } // 拖拽中半透明
            }
            drawText(label, px, py, ePaint)

            // 中文（灰色小字，在英文下方）
            if (node.chinese.isNotBlank()) {
                val cPaint = android.graphics.Paint().apply {
                    textSize = cnSize
                    this.color = gc.subtitle().toArgb()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawText(node.chinese, px, py + cnSize + 2f * density, cPaint)
            }

            // 关系类型标签（极淡小字，在中文字下方）
            if (node.level > 0 && node.type.isNotBlank()) {
                val tLabel = typeLabels[node.type] ?: node.type
                val tPaint = android.graphics.Paint().apply {
                    textSize = 7.5f * density
                    this.color = engColor.copy(alpha = 0.55f).toArgb()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                val chineseHeight = if (node.chinese.isNotBlank()) cnSize + 2f * density else 0f
                drawText(tLabel, px, py + chineseHeight + cnSize - 4f * density, tPaint)
            }

            // 收藏标记：金色★在英文右上角
            if (node.label in bookmarkedLabels && node.level > 0) {
                val sPaint = android.graphics.Paint().apply {
                    textSize = engSize * 0.8f
                    this.color = gc.bookmark.toArgb()
                    textAlign = android.graphics.Paint.Align.LEFT
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                // ★放在英文右侧，与英文对齐
                val engWidth = android.graphics.Paint().apply {
                    textSize = engSize
                    isAntiAlias = true
                }.measureText(label)
                drawText(" ★", px + engWidth / 2f + 2f * density, py, sPaint)
            }
        }
    }
}

// ═══════════════════════════════════════════
//  交互辅助（文字级命中检测）
// ═══════════════════════════════════════════

private fun hitText(
    tapPos: Offset, nodes: List<GraphNode>, positions: List<Offset>,
    scale: Float, panOffset: Offset,
    canvasSize: androidx.compose.ui.geometry.Size,
    sizes: List<Pair<Float, Float>>
): GraphNode? {
    val cx = canvasSize.width / 2f + panOffset.x
    val cy = canvasSize.height / 2f + panOffset.y
    val hitW = 30f / scale.coerceAtLeast(0.25f)
    val hitH = 26f / scale.coerceAtLeast(0.25f)
    for (i in nodes.indices) {
        if (i >= positions.size || nodes[i].id == "center") continue
        val px = cx + positions[i].x * scale
        val py = cy + positions[i].y * scale
        val (w, h) = if (i < sizes.size) sizes[i] else (80f to 30f)
        val halfW = (w * scale).coerceAtLeast(hitW) / 2f
        val halfH = (h * scale).coerceAtLeast(hitH) / 2f
        if (tapPos.x in (px - halfW)..(px + halfW) &&
            tapPos.y in (py - halfH - 6f * scale)..(py + halfH + 4f * scale)) {
            return nodes[i]
        }
    }
    return null
}

private fun hitTestId(
    tapPos: Offset, nodes: List<GraphNode>, positions: List<Offset>,
    scale: Float, panOffset: Offset,
    canvasSize: androidx.compose.ui.geometry.Size,
    sizes: List<Pair<Float, Float>>
): String? {
    val cx = canvasSize.width / 2f + panOffset.x
    val cy = canvasSize.height / 2f + panOffset.y
    val hitW = 34f / scale.coerceAtLeast(0.25f)
    val hitH = 30f / scale.coerceAtLeast(0.25f)
    for (i in nodes.indices) {
        if (i >= positions.size || nodes[i].level == 0) continue
        val px = cx + positions[i].x * scale; val py = cy + positions[i].y * scale
        val (w, h) = if (i < sizes.size) sizes[i] else (80f to 30f)
        val halfW = (w * scale).coerceAtLeast(hitW) / 2f
        val halfH = (h * scale).coerceAtLeast(hitH) / 2f
        if (tapPos.x in (px - halfW)..(px + halfW) &&
            tapPos.y in (py - halfH - 6f * scale)..(py + halfH + 4f * scale)) {
            return nodes[i].id
        }
    }
    return null
}
