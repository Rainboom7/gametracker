package app.playlog.feature.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.component.PlatformDot
import app.playlog.core.designsystem.component.SectionHeader
import app.playlog.core.designsystem.component.StackedPlatformChart
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.designsystem.theme.platformColors
import app.playlog.core.model.ActivityBucket
import app.playlog.core.model.ActivityPeriod
import app.playlog.core.model.ActivitySummary
import app.playlog.core.model.GameId
import app.playlog.core.model.Platform
import app.playlog.core.ui.TopGameRow
import app.playlog.core.ui.formatPlaytime
import app.playlog.core.ui.platformName
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ActivityRoute(onOpenGame: (GameId) -> Unit, viewModel: ActivityViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ActivityScreen(state, viewModel::selectPeriod, viewModel::previous, viewModel::next, onOpenGame)
}

@Composable
fun ActivityScreen(
    state: ActivityUiState,
    onSelectPeriod: (ActivityPeriod) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenGame: (GameId) -> Unit,
) {
    val content = state as? ActivityUiState.Content
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(stringResource(R.string.activity_title), style = MaterialTheme.typography.headlineLarge)
        PeriodSelector(content?.summary?.period ?: ActivityPeriod.WEEK, onSelectPeriod)
        if (content == null) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val summary = content.summary
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.activity_previous))
            }
            Text(periodLabel(summary), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onNext, enabled = content.canGoForward) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.activity_next))
            }
        }
        Column {
            Text(formatPlaytime(summary.total), style = MaterialTheme.typography.displayLarge)
            DeltaText(summary)
            if (content.streakDays > 1) {
                Text(stringResource(R.string.activity_streak, content.streakDays), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        StackedPlatformChart(
            buckets = summary.buckets.map { b -> b.perPlatform.mapValues { it.value.toMinutes().toFloat() } },
            contentDescription = stringResource(R.string.activity_chart_description, formatPlaytime(summary.total)),
            labels = summary.buckets.map { bucketLabel(summary.period, it) },
        )
        Legend(summary)
        if (summary.topGames.isEmpty()) {
            Text(stringResource(R.string.activity_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column {
                SectionHeader(stringResource(R.string.activity_top))
                Spacer(Modifier.height(Spacing.xs))
                summary.topGames.forEachIndexed { i, top ->
                    TopGameRow(rank = i + 1, game = top, onClick = { onOpenGame(top.entry.primary.id) })
                }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun PeriodSelector(selected: ActivityPeriod, onSelect: (ActivityPeriod) -> Unit) {
    val labels = mapOf(
        ActivityPeriod.WEEK to stringResource(R.string.activity_week),
        ActivityPeriod.MONTH to stringResource(R.string.activity_month),
        ActivityPeriod.YEAR to stringResource(R.string.activity_year),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ActivityPeriod.entries.forEachIndexed { i, period ->
            SegmentedButton(
                selected = period == selected,
                onClick = { onSelect(period) },
                shape = SegmentedButtonDefaults.itemShape(i, ActivityPeriod.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.onSurface,
                    activeContentColor = MaterialTheme.colorScheme.background,
                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                ),
                icon = {},
            ) { Text(labels.getValue(period)) }
        }
    }
}

@Composable
private fun DeltaText(summary: ActivitySummary) {
    val diff = summary.total - summary.previousTotal
    val (text, color) = when {
        diff.isZero -> stringResource(R.string.activity_delta_same) to MaterialTheme.colorScheme.onSurfaceVariant
        diff.isNegative -> stringResource(R.string.activity_delta_less, formatPlaytime(diff.negated())) to MaterialTheme.colorScheme.onSurfaceVariant
        else -> stringResource(R.string.activity_delta_more, formatPlaytime(diff)) to MaterialTheme.colorScheme.primary
    }
    Text(text, style = MaterialTheme.typography.labelLarge, color = color)
}

@Composable
private fun Legend(summary: ActivitySummary) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Platform.entries.filter { (summary.perPlatform[it]?.isZero) == false }.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformDot(platformColors(p).accent)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${platformName(p)} ${formatPlaytime(summary.perPlatform.getValue(p))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun periodLabel(summary: ActivitySummary): String {
    val locale = Locale.getDefault()
    return when (summary.period) {
        ActivityPeriod.WEEK -> {
            val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            "${f.format(summary.start)} – ${f.format(summary.endInclusive)}"
        }
        ActivityPeriod.MONTH -> DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(summary.start).replaceFirstChar { it.titlecase(locale) }
        ActivityPeriod.YEAR -> summary.start.year.toString()
    }
}

private fun bucketLabel(period: ActivityPeriod, bucket: ActivityBucket): String {
    val locale = Locale.getDefault()
    return when (period) {
        ActivityPeriod.WEEK -> bucket.start.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        ActivityPeriod.MONTH -> "${bucket.start.dayOfMonth}–${bucket.endInclusive.dayOfMonth}"
        ActivityPeriod.YEAR -> bucket.start.month.getDisplayName(TextStyle.NARROW_STANDALONE, locale)
    }
}
