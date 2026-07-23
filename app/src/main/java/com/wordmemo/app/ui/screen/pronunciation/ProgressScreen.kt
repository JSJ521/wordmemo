package com.wordmemo.app.ui.screen.pronunciation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordmemo.app.domain.pronunciation.model.AssessmentRecord
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ProgressViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "进步追踪",
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
        if (uiState.isLoading && uiState.historyRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.historyRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.TrendingUp,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        "还没有测评记录",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "完成发音测评后，这里会展示你的进步趋势",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            ProgressContent(
                records = uiState.historyRecords,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressContent(
    records: List<AssessmentRecord>,
    modifier: Modifier = Modifier
) {
    val totalAssessments = records.size
    val avgScore = if (records.isNotEmpty()) records.map { it.overallScore }.average().toInt() else 0
    val bestScore = if (records.isNotEmpty()) records.maxOf { it.overallScore } else 0
    val avgScoreColor = scoreColorFor(avgScore)
    var selectedPeriod by remember { mutableStateOf(7) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)
    ) {
        // Summary header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SummaryCard(
                    value = "$totalAssessments",
                    label = "测评次数",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    value = "$avgScore",
                    label = "平均分",
                    color = avgScoreColor,
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    value = "$bestScore",
                    label = "最高分",
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Line chart card
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
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "评分趋势",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // Period selector
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = selectedPeriod == 7,
                                onClick = { selectedPeriod = 7 },
                                shape = SegmentedButtonDefaults.itemShape(0, 3)
                            ) { Text("7天", style = MaterialTheme.typography.labelSmall) }
                            SegmentedButton(
                                selected = selectedPeriod == 30,
                                onClick = { selectedPeriod = 30 },
                                shape = SegmentedButtonDefaults.itemShape(1, 3)
                            ) { Text("30天", style = MaterialTheme.typography.labelSmall) }
                            SegmentedButton(
                                selected = selectedPeriod == -1,
                                onClick = { selectedPeriod = -1 },
                                shape = SegmentedButtonDefaults.itemShape(2, 3)
                            ) { Text("全部", style = MaterialTheme.typography.labelSmall) }
                        }
                    }

                    // Filter records by period
                    val filteredRecords = filterRecordsByPeriod(records, selectedPeriod)

                    // Line chart
                    ScoreTrendLineChart(
                        records = filteredRecords,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            }
        }

        // Pie chart card
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
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "错误音素分布",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Compute phoneme stats from records
                    val phonemeStats = computePhonemeStats(records)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pie chart
                        PhonemePieChart(
                            stats = phonemeStats,
                            modifier = Modifier.size(160.dp)
                        )

                        // Legend
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            phonemeStats.take(4).forEach { stat ->
                                PieLegendItem(
                                    color = stat.color,
                                    label = stat.label,
                                    percentage = stat.percentage
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent assessments
        item {
            Row(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(
                    "最近测评",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        val recentRecords = records.sortedByDescending { it.createdAt }.take(10)
        items(
            items = recentRecords,
            key = { it.id }
        ) { record ->
            RecentAssessmentItem(record = record)
        }
    }
}

@Composable
private fun SummaryCard(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScoreTrendLineChart(
    records: List<AssessmentRecord>,
    modifier: Modifier = Modifier
) {
    if (records.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                "暂无数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Sort records by date
    val sortedRecords = records.sortedBy { it.createdAt }
    val scores = sortedRecords.map { it.overallScore }
    val maxScore = 100f
    val minScore = 0f

    val lineColor = Color(0xFF4CAF50)
    val fillColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    Canvas(modifier = modifier) {
        val paddingLeft = 40.dp.toPx()
        val paddingRight = 16.dp.toPx()
        val paddingTop = 16.dp.toPx()
        val paddingBottom = 40.dp.toPx()
        val chartWidth = size.width - paddingLeft - paddingRight
        val chartHeight = size.height - paddingTop - paddingBottom

        // Draw horizontal grid lines (4 lines: 0, 25, 50, 75, 100)
        val gridSteps = 4
        for (i in 0..gridSteps) {
            val y = paddingTop + chartHeight * (1f - i.toFloat() / gridSteps)
            drawLine(
                color = gridColor,
                start = Offset(paddingLeft, y),
                end = Offset(size.width - paddingRight, y),
                strokeWidth = 1.dp.toPx()
            )
            // Y-axis label
            drawContext.canvas.nativeCanvas.drawText(
                "${i * 25}",
                paddingLeft - 8.dp.toPx(),
                y + 4.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#808080")
                    textSize = 10.dp.toPx()
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            )
        }

        if (scores.size < 2) {
            // Single point: draw a dot
            val x = paddingLeft + chartWidth / 2
            val y = paddingTop + chartHeight * (1f - (scores.first() - minScore) / (maxScore - minScore))
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )
            return@Canvas
        }

        // Build path for the line and fill
        val stepX = chartWidth / (scores.size - 1).coerceAtLeast(1)
        val path = Path()
        val fillPath = Path()

        scores.forEachIndexed { index, score ->
            val x = paddingLeft + index * stepX
            val y = paddingTop + chartHeight * (1f - (score - minScore) / (maxScore - minScore))

            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, paddingTop + chartHeight)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        // Close fill path
        fillPath.lineTo(
            paddingLeft + (scores.size - 1) * stepX,
            paddingTop + chartHeight
        )
        fillPath.close()

        // Draw fill
        drawPath(fillPath, fillColor)

        // Draw line
        drawPath(
            path,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Draw dots
        scores.forEachIndexed { index, score ->
            val x = paddingLeft + index * stepX
            val y = paddingTop + chartHeight * (1f - (score - minScore) / (maxScore - minScore))
            drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(x, y))
        }

        // Draw X-axis labels (date)
        val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
        scores.forEachIndexed { index, _ ->
            if (index % max(1, scores.size / 5) == 0 || index == scores.size - 1) {
                val x = paddingLeft + index * stepX
                val record = sortedRecords[index]
                val dateStr = dateFormat.format(Date(record.createdAt))
                drawContext.canvas.nativeCanvas.drawText(
                    dateStr,
                    x,
                    size.height - 8.dp.toPx(),
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#808080")
                        textSize = 9.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }
    }
}

@Composable
private fun PhonemePieChart(
    stats: List<PhonemeStat>,
    modifier: Modifier = Modifier
) {
    if (stats.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                "暂无数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val total = stats.sumOf { it.count }.toFloat()
    val colors = stats.map { it.color }

    Canvas(modifier = modifier) {
        val canvasSize = minOf(size.width, size.height)
        val radius = canvasSize / 2f
        val center = Offset(size.width / 2, size.height / 2)
        val strokeWidth = radius * 0.35f

        // Draw doughnut chart
        var startAngle = -90f
        stats.forEach { stat ->
            val sweepAngle = (stat.count / total) * 360f
            drawArc(
                color = stat.color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            startAngle += sweepAngle
        }

        // Center text showing total errors
        val totalErrors = stats.sumOf { it.count }
        drawContext.canvas.nativeCanvas.drawText(
            "$totalErrors",
            center.x,
            center.y + 6.dp.toPx(),
            android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#E0E0E0")
                textSize = 20.dp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }
        )
    }
}

@Composable
private fun PieLegendItem(
    color: Color,
    label: String,
    percentage: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentAssessmentItem(
    record: AssessmentRecord
) {
    val scoreColor = scoreColorFor(record.overallScore)
    val dateStr = remember(record.createdAt) {
        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(record.createdAt))
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${record.overallScore}",
                style = MaterialTheme.typography.headlineSmall,
                color = scoreColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(48.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = record.sentenceText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// Data class for phoneme statistics
private data class PhonemeStat(
    val phoneme: String,
    val count: Int,
    val percentage: Int,
    val color: Color,
    val label: String
)

private fun computePhonemeStats(records: List<AssessmentRecord>): List<PhonemeStat> {
    // Collect phoneme scores from all records
    val phonemeCounts = mutableMapOf<String, Int>()
    records.forEach { record ->
        record.phonemeScores.forEach { ps ->
            if (ps.gopScore < 0.5) { // Only count errors (GOP < 0.5)
                phonemeCounts[ps.phoneme] = phonemeCounts.getOrDefault(ps.phoneme, 0) + 1
            }
        }
    }

    if (phonemeCounts.isEmpty()) {
        // No phoneme data yet - show placeholder stats
        return listOf(
            PhonemeStat("th", 0, 0, Color(0xFFFF5252), "th /dh/"),
            PhonemeStat("r", 0, 0, Color(0xFFFFB74D), "r /r/"),
            PhonemeStat("l", 0, 0, Color(0xFF4CAF50), "l /l/")
        )
    }

    val total = phonemeCounts.values.sum().toFloat()
    val sorted = phonemeCounts.entries.sortedByDescending { it.value }

    val colors = listOf(
        Color(0xFFFF5252),
        Color(0xFFFFB74D),
        Color(0xFF4CAF50),
        Color(0xFF64B5F6),
        Color(0xFFAB47BC),
        Color(0xFFFF7043)
    )

    return sorted.mapIndexed { index, (phoneme, count) ->
        PhonemeStat(
            phoneme = phoneme,
            count = count,
            percentage = ((count / total) * 100).toInt(),
            color = colors.getOrElse(index) { Color(0xFF9E9E9E) },
            label = phoneme
        )
    }
}

private fun filterRecordsByPeriod(
    records: List<AssessmentRecord>,
    periodDays: Int
): List<AssessmentRecord> {
    if (periodDays <= 0) return records
    val cutoff = System.currentTimeMillis() - periodDays * 24L * 60 * 60 * 1000
    return records.filter { it.createdAt >= cutoff }
}

private fun scoreColorFor(score: Int): Color = when {
    score >= 90 -> Color(0xFF4CAF50)
    score >= 75 -> Color(0xFFD0BCFF)
    score >= 60 -> Color(0xFFFFB74D)
    else -> Color(0xFFEF5350)
}
