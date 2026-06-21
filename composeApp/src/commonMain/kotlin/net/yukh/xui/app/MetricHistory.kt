package net.yukh.xui.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import net.yukh.xui.shared.dto.MetricPoint

enum class MetricKind { PERCENT, RATIO, BYTES, COUNT }

data class MetricSeriesDef(val label: String, val key: String)

enum class MetricBlock(val title: String, val kind: MetricKind, val series: List<MetricSeriesDef>) {
    CPU("CPU", MetricKind.PERCENT, listOf(MetricSeriesDef("CPU", "cpu"))),
    MEMORY("Memory", MetricKind.PERCENT, listOf(MetricSeriesDef("Memory", "mem"))),
    DISK("Disk", MetricKind.PERCENT, listOf(MetricSeriesDef("Disk", "diskUsage"))),
    LOAD("Load", MetricKind.RATIO, listOf(MetricSeriesDef("1m", "load1"), MetricSeriesDef("5m", "load5"), MetricSeriesDef("15m", "load15"))),
    NET("Network", MetricKind.BYTES, listOf(MetricSeriesDef("↑", "netUp"), MetricSeriesDef("↓", "netDown"))),
    CONN("Connections", MetricKind.COUNT, listOf(MetricSeriesDef("TCP", "tcpCount"), MetricSeriesDef("UDP", "udpCount"))),
}

data class ChartSeries(val label: String, val points: List<MetricPoint>)

data class MetricChartState(
    val block: MetricBlock,
    val bucket: Int = 2,
    val series: List<ChartSeries> = emptyList(),
    val loading: Boolean = false,
)

/** Interval options → API bucket seconds (~60 points each). Default: real-time. */
val BUCKET_OPTIONS: List<Pair<Int, String>> = listOf(
    2 to "Real-time", 30 to "30 min", 60 to "1 hour", 120 to "2 hours", 180 to "3 hours", 300 to "5 hours",
)

private fun MetricKind.format(v: Double): String = when (this) {
    MetricKind.PERCENT -> v.formatPercent()
    MetricKind.RATIO -> ((v * 100).roundToLong() / 100.0).toString()
    MetricKind.BYTES -> v.roundToLong().formatBytes() + "/s"
    MetricKind.COUNT -> v.roundToLong().toString()
}

@Composable
fun MetricHistoryDialog(state: MetricChartState, onBucket: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.error)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${tr(state.block.title)} — ${tr("history")}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
                    BUCKET_OPTIONS.forEach { (bucket, label) ->
                        val on = bucket == state.bucket
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(16.dp))
                                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { if (bucket != state.bucket) onBucket(bucket) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(tr(label), style = MaterialTheme.typography.labelMedium,
                                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val hasData = state.series.any { it.points.isNotEmpty() }
                when {
                    state.loading && !hasData -> Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    !hasData -> Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) {
                        Text(tr("No history yet"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    else -> {
                        MetricLineChart(state.series, state.block.kind, colors, Modifier.fillMaxWidth().height(200.dp))
                        state.series.forEachIndexed { i, s ->
                            val last = s.points.lastOrNull()?.v
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i % colors.size]))
                                Text("${s.label}: ${last?.let { state.block.kind.format(it) } ?: "—"}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } },
    )
}

@Composable
private fun MetricLineChart(series: List<ChartSeries>, kind: MetricKind, colors: List<Color>, modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val rawMax = series.flatMap { it.points.map { p -> p.v } }.maxOrNull() ?: 1.0
    val yMax = if (kind == MetricKind.PERCENT) 100.0 else (rawMax * 1.15).coerceAtLeast(0.001)
    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            val w = size.width; val h = size.height
            val padL = 4f; val padR = 4f; val padT = 8f; val padB = 8f
            val plotW = w - padL - padR; val plotH = h - padT - padB
            for (g in 0..4) {
                val y = padT + plotH * g / 4f
                drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), strokeWidth = 1f)
            }
            fun yOf(v: Double): Float {
                val frac = ((v - 0.0) / (yMax - 0.0)).coerceIn(0.0, 1.0)
                return padT + (1f - frac.toFloat()) * plotH
            }
            series.forEachIndexed { si, s ->
                val pts = s.points
                if (pts.size < 2) return@forEachIndexed
                val path = Path()
                pts.forEachIndexed { i, p ->
                    val x = padL + plotW * (i.toFloat() / (pts.size - 1))
                    val y = yOf(p.v)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, colors[si % colors.size], style = Stroke(width = 3f))
            }
        }
        Text(kind.format(yMax), Modifier.align(Alignment.TopEnd).padding(end = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(kind.format(0.0), Modifier.align(Alignment.BottomEnd).padding(end = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
