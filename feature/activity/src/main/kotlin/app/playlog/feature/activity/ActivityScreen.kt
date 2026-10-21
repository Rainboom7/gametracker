package app.playlog.feature.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.component.GameImage
import app.playlog.core.designsystem.component.PlatformBadgeRow
import app.playlog.core.designsystem.component.PlatformDot
import app.playlog.core.designsystem.component.PlatformSplitBar
import app.playlog.core.designsystem.component.SectionHeader
import app.playlog.core.designsystem.component.StackedPlatformChart
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.designsystem.theme.platformColors
import app.playlog.core.model.ActivityBucket
import app.playlog.core.model.ActivityPeriod
import app.playlog.core.model.ActivitySummary
import app.playlog.core.model.GameId
import app.playlog.core.model.Platform
import app.playlog.core.model.TopGame
import app.playlog.core.ui.TopGameRow
import app.playlog.core.ui.formatPlaytime
import app.playlog.core.ui.platformName
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ActivityRoute(onOpenGame: (GameId) -> Unit, viewModel: ActivityViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extras by viewModel.extras.collectAsStateWithLifecycle()
    ActivityScreen(state, viewModel::selectPeriod, viewModel::previous, viewModel::next, onOpenGame, extras)
}

@Composable
fun ActivityScreen(
    state: ActivityUiState,
    onSelectPeriod: (ActivityPeriod) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenGame: (GameId) -> Unit,
    extras: ActivityExtras = ActivityExtras(),
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
        SummaryHero(content, onPrevious, onNext)
        SectionCard(stringResource(bucketTitle(summary.period))) {
            StackedPlatformChart(
                buckets = summary.buckets.map { b -> b.perPlatform.mapValues { it.value.toMinutes().toFloat() } },
                contentDescription = stringResource(R.string.activity_chart_description, formatPlaytime(summary.total)),
                labels = summary.buckets.map { bucketLabel(summary.period, it) },
                height = 140.dp,
            )
        }
        PlatformShare(summary)
        if (summary.topGames.isEmpty()) {
            Text(stringResource(R.string.activity_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionHeader(stringResource(R.string.activity_game_of_period))
                val first = summary.topGames.first()
                GameOfPeriod(first, onClick = { onOpenGame(first.entry.primary.id) })
                summary.topGames.drop(1).forEachIndexed { i, top ->
                    TopGameRow(rank = i + 2, game = top, onClick = { onOpenGame(top.entry.primary.id) })
                }
            }
        }
        extras.insights?.let { YearCalendar(it) }
        extras.insights?.let { WeekdayRhythm(it) }
        if (extras.taste.isNotEmpty()) TasteChart(extras.taste)
        Spacer(Modifier.height(Spacing.lg))
    }
}

/** Lime card: the period, its total against the previous one, and three highlights. */
@Composable
private fun SummaryHero(content: ActivityUiState.Content, onPrevious: () -> Unit, onNext: () -> Unit) {
    val summary = content.summary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.primary)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.activity_previous), enabled = true, onClick = onPrevious)
            Text(
                periodLabel(summary),
                style = MaterialTheme.typography.labelLarge,
                color = onAccent,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            HeroArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.activity_next), enabled = content.canGoForward, onClick = onNext)
        }
        Column {
            Text(formatPlaytime(summary.total), style = MaterialTheme.typography.displayLarge, color = onAccent)
            Text(deltaText(summary), style = MaterialTheme.typography.labelLarge, color = onAccent)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            MiniStat(
                stringResource(R.string.activity_days_value, content.streakDays),
                stringResource(R.string.activity_streak_label),
                Modifier.weight(1f),
            )
            MiniStat(summary.gamesPlayed.toString(), stringResource(R.string.activity_games_label), Modifier.weight(1f))
            MiniStat(
                summary.bestDay?.let { formatPlaytime(it.playtime) } ?: "—",
                summary.bestDay?.let { stringResource(R.string.activity_best_day_label, bestDayLabel(it.date)) }
                    ?: stringResource(R.string.activity_best_day_none),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HeroArrow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f)),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f),
        ),
    ) {
        Icon(icon, contentDescription = label)
    }
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, maxLines = 2)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraLarge)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

/** Share of the period per platform: a split bar plus hours and percent as text. */
@Composable
private fun PlatformShare(summary: ActivitySummary) {
    val played = Platform.entries.filter { summary.perPlatform[it]?.isZero == false }
    if (played.isEmpty()) return
    val totalMinutes = summary.total.toMinutes().coerceAtLeast(1)
    var caption = ""
    for (p in played) caption += "${platformName(p)} ${formatPlaytime(summary.perPlatform.getValue(p))}. "
    SectionCard(stringResource(R.string.activity_platforms)) {
        if (played.size > 1) {
            PlatformSplitBar(played.map { it to summary.perPlatform.getValue(it).toMinutes().toFloat() }, caption)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            played.forEach { p ->
                val time = summary.perPlatform.getValue(p)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlatformDot(platformColors(p).accent)
                        Spacer(Modifier.width(6.dp))
                        Text(platformName(p), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Text(formatPlaytime(time), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.activity_share, (time.toMinutes() * PERCENT / totalMinutes).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The period's most played title, large, on its own cover-tinted card. */
@Composable
private fun GameOfPeriod(top: TopGame, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameImage(
            urls = listOf(top.entry.coverUrl, top.entry.fallbackCoverUrl),
            contentDescription = null,
            placeholderText = top.entry.title,
            modifier = Modifier.size(width = 64.dp, height = 86.dp).clip(MaterialTheme.shapes.small),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.activity_game_share, (top.share * PERCENT).toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(top.entry.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatPlaytime(top.playtime), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(Spacing.xs))
                PlatformBadgeRow(top.entry.platforms)
            }
        }
    }
}

private fun bucketTitle(period: ActivityPeriod): Int = when (period) {
    ActivityPeriod.WEEK -> R.string.activity_by_day
    ActivityPeriod.MONTH -> R.string.activity_by_week
    ActivityPeriod.YEAR -> R.string.activity_by_month
}

private fun bestDayLabel(date: LocalDate): String =
    DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(date)

@Composable
private fun deltaText(summary: ActivitySummary): String {
    val diff = summary.total - summary.previousTotal
    return when {
        diff.isZero -> stringResource(R.string.activity_delta_same)
        diff.isNegative -> stringResource(R.string.activity_delta_less, formatPlaytime(diff.negated()))
        else -> stringResource(R.string.activity_delta_more, formatPlaytime(diff))
    }
}

private const val PERCENT = 100

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
