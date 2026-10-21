package app.playlog.feature.activity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.playlog.core.designsystem.component.GradientCard
import app.playlog.core.designsystem.theme.PlaylogColors
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.PlayInsights
import app.playlog.core.ui.formatPlaytime
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** GitHub-style year: one column per week, Monday on top, lime getting brighter with more play. */
@Composable
internal fun YearCalendar(insights: PlayInsights) {
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val levels = HeatLevels
    val playedMinutes = insights.days.map { it.playtime.toMinutes() }.filter { it > 0 }.sorted()
    // Quartiles of the user's own days, so a casual and a heavy player both get a full range of color.
    val cuts = if (playedMinutes.isEmpty()) emptyList() else Quartiles.map { q -> playedMinutes[((playedMinutes.size - 1) * q).toInt()] }
    GradientCard(accents = listOf(PlaylogColors.Lime, PlaylogColors.Mint), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.activity_calendar), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.activity_calendar_caption, insights.activeDays, insights.longestStreak, formatPlaytime(insights.total)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        val description = stringResource(R.string.activity_calendar_description, insights.activeDays)
        Canvas(Modifier.fillMaxWidth().height(CalendarHeight).semantics { contentDescription = description }) {
            val weeks = (insights.days.size + DaysInWeek - 1) / DaysInWeek
            val cell = minOf(size.width / weeks, size.height / DaysInWeek)
            val gap = cell * GapFraction
            insights.days.forEachIndexed { i, day ->
                val minutes = day.playtime.toMinutes()
                val color = if (minutes == 0L) empty else levels[cuts.count { minutes > it }.coerceAtMost(levels.lastIndex)]
                drawRoundRect(
                    color = color,
                    topLeft = Offset((i / DaysInWeek) * cell, (i % DaysInWeek) * cell),
                    size = Size(cell - gap, cell - gap),
                    cornerRadius = CornerRadius(cell * CornerFraction),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Spacer(Modifier.weight(1f))
            Legend(stringResource(R.string.activity_calendar_less))
            (listOf(empty) + levels).forEach { Box(Modifier.size(10.dp).clip(MaterialTheme.shapes.extraSmall).background(it)) }
            Legend(stringResource(R.string.activity_calendar_more))
        }
    }
}

@Composable
private fun Legend(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Seven bars, violet to pink, average hours per weekday; the busiest day is named under the chart. */
@Composable
internal fun WeekdayRhythm(insights: PlayInsights) {
    val locale = Locale.getDefault()
    val days = DayOfWeek.entries
    val values = days.map { insights.weekdayAverage[it]?.toMinutes() ?: 0L }
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: return
    val busiest = days[values.indexOf(max)]
    GradientCard(accents = listOf(PlaylogColors.Violet, PlaylogColors.Pink), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.activity_weekdays), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(
                R.string.activity_weekdays_caption,
                busiest.getDisplayName(TextStyle.FULL_STANDALONE, locale),
                formatPlaytime(insights.weekdayAverage.getValue(busiest)),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        val description = stringResource(R.string.activity_weekdays_description)
        Row(
            Modifier.fillMaxWidth().height(RhythmHeight).semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { i, v ->
                val fraction = (v.toFloat() / max).coerceAtLeast(MinBar)
                val brush = if (days[i] == busiest) {
                    Brush.verticalGradient(listOf(PlaylogColors.Pink, PlaylogColors.Violet))
                } else {
                    Brush.verticalGradient(listOf(PlaylogColors.Violet.copy(alpha = DimAlpha), PlaylogColors.Violet.copy(alpha = DimAlpha / 2)))
                }
                Box(Modifier.weight(1f).fillMaxHeight(fraction).clip(MaterialTheme.shapes.small).background(brush))
            }
        }
        Spacer(Modifier.height(Spacing.xxs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            days.forEach {
                Text(
                    it.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (it == busiest) PlaylogColors.Pink else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Top Steam tags of the user's most played games, each bar in its own color. */
@Composable
internal fun TasteChart(taste: List<Pair<String, Double>>) {
    val max = taste.maxOf { it.second }.takeIf { it > 0 } ?: return
    GradientCard(accents = listOf(PlaylogColors.Sky, PlaylogColors.Violet), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.activity_taste), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.activity_taste_caption), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            taste.forEachIndexed { i, (tag, weight) ->
                val accent = TasteColors[i % TasteColors.size]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tag,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(TagLabelWidth),
                    )
                    Box(Modifier.weight(1f).height(12.dp).clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth((weight / max).toFloat()).height(12.dp).clip(MaterialTheme.shapes.extraSmall).background(accent))
                    }
                }
            }
        }
    }
}

private const val DaysInWeek = 7
private const val GapFraction = 0.18f
private const val CornerFraction = 0.25f
private const val MinBar = 0.04f
private const val DimAlpha = 0.55f
private val Quartiles = listOf(0.25, 0.5, 0.75)
private val CalendarHeight = 64.dp
private val RhythmHeight = 96.dp
private val TagLabelWidth = 120.dp
private val HeatLevels = listOf(Color(0xFF3B4A1E), Color(0xFF6A8A26), Color(0xFF9BC93A), PlaylogColors.Lime)
private val TasteColors = listOf(PlaylogColors.Lime, PlaylogColors.Sky, PlaylogColors.Violet, PlaylogColors.Pink, PlaylogColors.Orange, PlaylogColors.Mint)
