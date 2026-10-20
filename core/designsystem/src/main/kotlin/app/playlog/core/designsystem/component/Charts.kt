package app.playlog.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.playlog.core.designsystem.theme.LocalPlatformPalette
import app.playlog.core.designsystem.theme.PlatformPalette
import app.playlog.core.model.Platform

private val BarIdle = Color(0xFF4B5163)
private val BarZero = Color(0xFF23262F)

/**
 * Day-by-day bars (game detail, 14 days). [highlightIndex] (today) is drawn in the accent color;
 * zero days are a 4 dp stub so the rhythm stays visible.
 */
@Composable
fun DailyBarChart(
    values: List<Float>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    highlightIndex: Int = values.lastIndex,
    labels: List<String> = emptyList(),
    height: Dp = 96.dp,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier.semantics { this.contentDescription = contentDescription }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
            val slot = size.width / values.size.coerceAtLeast(1)
            val barWidth = slot * 0.62f
            val stub = 4.dp.toPx()
            values.forEachIndexed { i, v ->
                val h = if (v <= 0f) stub else (size.height * v / max).coerceAtLeast(stub)
                val color = when {
                    v <= 0f -> BarZero
                    i == highlightIndex -> accent
                    else -> BarIdle
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 4),
                )
            }
        }
        if (labels.isNotEmpty()) ChartLabels(labels)
    }
}

/** Stacked bars, one segment per platform (Activity screen). */
@Composable
fun StackedPlatformChart(
    buckets: List<Map<Platform, Float>>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    height: Dp = 160.dp,
) {
    val palette = LocalPlatformPalette.current
    Column(modifier.semantics { this.contentDescription = contentDescription }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val max = buckets.maxOfOrNull { it.values.sum() }?.takeIf { it > 0f } ?: 1f
            val slot = size.width / buckets.size.coerceAtLeast(1)
            val barWidth = (slot * 0.6f).coerceAtMost(28.dp.toPx())
            val stub = 4.dp.toPx()
            buckets.forEachIndexed { i, bucket ->
                val left = i * slot + (slot - barWidth) / 2
                val total = bucket.values.sum()
                if (total <= 0f) {
                    drawRoundRect(BarZero, Offset(left, size.height - stub), Size(barWidth, stub), CornerRadius(stub / 2))
                    return@forEachIndexed
                }
                var bottom = size.height
                val segments = Platform.entries.mapNotNull { p -> bucket[p]?.takeIf { it > 0f }?.let { p to it } }
                segments.forEachIndexed { s, (platform, value) ->
                    val h = size.height * value / max
                    drawSegment(palette.accentOf(platform), left, bottom - h, barWidth, h, roundTop = s == segments.lastIndex)
                    bottom -= h
                }
            }
        }
        if (labels.isNotEmpty()) ChartLabels(labels)
    }
}

/** Horizontal 10 dp bar split by platform share (game detail, only for multi-platform titles). */
@Composable
fun PlatformSplitBar(segments: List<Pair<Platform, Float>>, contentDescription: String, modifier: Modifier = Modifier) {
    val palette = LocalPlatformPalette.current
    Canvas(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val total = segments.sumOf { it.second.toDouble() }.toFloat().takeIf { it > 0f } ?: return@Canvas
        val gap = 2.dp.toPx()
        var x = 0f
        val radius = CornerRadius(size.height / 2)
        segments.forEach { (platform, value) ->
            val w = size.width * value / total
            drawRoundRect(palette.accentOf(platform), Offset(x, 0f), Size((w - gap).coerceAtLeast(0f), size.height), radius)
            x += w
        }
    }
}

@Composable
private fun ChartLabels(labels: List<String>) {
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach {
            Text(
                it,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

private fun DrawScope.drawSegment(color: Color, left: Float, top: Float, width: Float, height: Float, roundTop: Boolean) {
    if (!roundTop) {
        drawRect(color, Offset(left, top), Size(width, height))
        return
    }
    val r = (width / 4).coerceAtMost(height)
    val path = Path().apply {
        moveTo(left, top + height)
        lineTo(left, top + r)
        quadraticTo(left, top, left + r, top)
        lineTo(left + width - r, top)
        quadraticTo(left + width, top, left + width, top + r)
        lineTo(left + width, top + height)
        close()
    }
    drawPath(path, color)
}

fun PlatformPalette.accentOf(platform: Platform): Color = when (platform) {
    Platform.STEAM -> steam.accent
    Platform.PLAYSTATION -> playstation.accent
    Platform.NINTENDO -> nintendo.accent
}
