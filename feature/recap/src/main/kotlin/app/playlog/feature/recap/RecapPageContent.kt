package app.playlog.feature.recap

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.playlog.core.designsystem.component.GameImage
import app.playlog.core.designsystem.component.PrimaryButton
import app.playlog.core.designsystem.theme.PlaylogColors
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.designsystem.theme.platformColors
import app.playlog.core.model.ActivityPeriod
import app.playlog.core.model.GameId
import app.playlog.core.ui.formatPlaytime
import app.playlog.core.ui.platformName
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@Composable
internal fun BoxScope.RecapPageContent(page: RecapPage, active: Boolean, onOpenGame: (GameId) -> Unit) {
    // Numbers count up each time their card comes on screen.
    val progress by animateFloatAsState(if (active) 1f else 0f, tween(CountUpMillis), label = "countUp")
    when (page) {
        is RecapPage.Intro -> Intro(page)
        is RecapPage.Total -> Total(page, progress)
        is RecapPage.TopGameOf -> TopGamePage(page, onOpenGame)
        is RecapPage.Ranking -> Ranking(page, progress)
        is RecapPage.Platforms -> Platforms(page, progress)
        is RecapPage.Rhythm -> Rhythm(page, progress)
        is RecapPage.Vibe -> Vibe(page)
        is RecapPage.Outro -> Outro(page.summary)
        RecapPage.Empty -> Centered { Body(stringResource(R.string.recap_empty)) }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
    ) { content() }
}

@Composable
private fun Kicker(text: String, color: Color = PlaylogColors.Lime) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp), color = color)
}

@Composable
private fun Huge(text: String) {
    Text(text, style = MaterialTheme.typography.displayLarge.copy(fontSize = HugeSize, lineHeight = HugeSize), color = Color.White)
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = BodyAlpha))
}

@Composable
private fun Intro(page: RecapPage.Intro) = Centered {
    Kicker(rangeText(page.start, page.endInclusive))
    Text(
        stringResource(introRes(page.period)),
        style = MaterialTheme.typography.displayMedium,
        color = Color.White,
    )
    Body(stringResource(R.string.recap_intro_hint))
}

@Composable
private fun Total(page: RecapPage.Total, progress: Float) = Centered {
    Kicker(stringResource(R.string.recap_total_title))
    Huge(formatPlaytime(Duration.ofMinutes((page.total.toMinutes() * progress).toLong())))
    val diff = page.total - page.previous
    Body(
        when {
            diff.isZero -> stringResource(R.string.recap_total_same)
            diff.isNegative -> stringResource(R.string.recap_total_less, formatPlaytime(diff.negated()))
            else -> stringResource(R.string.recap_total_more, formatPlaytime(diff))
        },
    )
    Body(stringResource(R.string.recap_total_days, page.activeDays, page.daysInPeriod))
    val films = (page.total.toMinutes() / FilmMinutes).toInt()
    if (films > 0) Body(stringResource(R.string.recap_total_films, films))
}

@Composable
private fun BoxScope.TopGamePage(page: RecapPage.TopGameOf, onOpenGame: (GameId) -> Unit) {
    val entry = page.top.entry
    GameImage(
        urls = listOf(entry.heroUrl, entry.coverUrl, entry.fallbackCoverUrl),
        contentDescription = null,
        placeholderText = entry.title,
        modifier = Modifier.fillMaxSize(),
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Scrim))))
    Column(
        Modifier
            .align(Alignment.BottomStart)
            
            .padding(Spacing.xl)
            .clickable(role = Role.Button) { onOpenGame(entry.primary.id) },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Kicker(stringResource(R.string.recap_top_title))
        Text(entry.title, style = MaterialTheme.typography.displaySmall, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Body(stringResource(R.string.recap_top_share, formatPlaytime(page.top.playtime), (page.top.share * Percent).roundToInt()))
    }
}

@Composable
private fun Ranking(page: RecapPage.Ranking, progress: Float) = Centered {
    Kicker(stringResource(R.string.recap_ranking_title))
    val max = page.games.first().playtime.toMinutes().coerceAtLeast(1).toFloat()
    page.games.forEachIndexed { i, top ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}", style = MaterialTheme.typography.headlineMedium, color = RankColors[i % RankColors.size], modifier = Modifier.width(36.dp))
            Column(Modifier.weight(1f)) {
                Text(top.entry.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(
                    Modifier
                        .fillMaxWidth(top.playtime.toMinutes() / max * progress)
                        .height(8.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(RankColors[i % RankColors.size]),
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Text(formatPlaytime(top.playtime), style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

@Composable
private fun Platforms(page: RecapPage.Platforms, progress: Float) = Centered {
    Kicker(stringResource(R.string.recap_platforms_title))
    Text(platformName(page.leader), style = MaterialTheme.typography.displayMedium, color = platformColors(page.leader).accent)
    Body(stringResource(R.string.recap_platforms_share, (page.shares.first().second * Percent * progress).roundToInt()))
    Row(Modifier.fillMaxWidth().height(20.dp).clip(MaterialTheme.shapes.small), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        page.shares.forEach { (platform, share) ->
            Box(Modifier.weight(share.coerceAtLeast(MinShare)).height(20.dp).background(platformColors(platform).accent))
        }
    }
    page.shares.forEach { (platform, share) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(MaterialTheme.shapes.extraSmall).background(platformColors(platform).accent))
            Spacer(Modifier.width(Spacing.xs))
            Body("${platformName(platform)} · ${(share * Percent).roundToInt()}%")
        }
    }
}

@Composable
private fun Rhythm(page: RecapPage.Rhythm, progress: Float) = Centered {
    Kicker(stringResource(R.string.recap_rhythm_title))
    page.bestDay?.let {
        Stat(formatPlaytime(it.playtime), stringResource(R.string.recap_rhythm_best_day) + " · " + shortDate(it.date), PlaylogColors.Orange)
    }
    Stat("${(page.gamesPlayed * progress).roundToInt()}", stringResource(R.string.recap_rhythm_games), PlaylogColors.Sky)
    if (page.longestStreak > 0) Stat("${(page.longestStreak * progress).roundToInt()}", stringResource(R.string.recap_rhythm_streak), PlaylogColors.Pink)
}

@Composable
private fun Stat(value: String, label: String, accent: Color) {
    Column {
        Text(value, style = MaterialTheme.typography.displaySmall, color = accent)
        Body(label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Vibe(page: RecapPage.Vibe) = Centered {
    Kicker(stringResource(R.string.recap_vibe_title), PlaylogColors.Pink)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        page.tags.forEachIndexed { i, tag ->
            Text(
                tag,
                style = MaterialTheme.typography.headlineSmall,
                color = PlaylogColors.Background,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(RankColors[i % RankColors.size])
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }
    }
    Body(stringResource(R.string.recap_vibe_hint))
}

/** The shareable card: rendered into a graphics layer so "Поделиться" exports exactly what is on screen. */
@Composable
private fun Outro(summary: RecapSummary) {
    val layer = rememberGraphicsLayer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .aspectRatio(CardAspect)
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
                .clip(MaterialTheme.shapes.extraLarge)
                .background(Brush.linearGradient(listOf(PlaylogColors.Violet, PlaylogColors.Pink, PlaylogColors.Orange)))
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Kicker(stringResource(R.string.recap_outro_title) + " · " + rangeText(summary.start, summary.endInclusive), PlaylogColors.Background)
                Text(formatPlaytime(summary.total), style = MaterialTheme.typography.displayLarge, color = PlaylogColors.Background)
            }
            summary.topGame?.let {
                Column {
                    Text(stringResource(R.string.recap_outro_top), style = MaterialTheme.typography.labelLarge, color = PlaylogColors.Background)
                    Text(it, style = MaterialTheme.typography.headlineSmall, color = PlaylogColors.Background, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                OutroStat(summary.gamesPlayed.toString(), stringResource(R.string.recap_outro_games))
                OutroStat(summary.activeDays.toString(), stringResource(R.string.recap_outro_days))
            }
            if (summary.vibe.isNotEmpty()) {
                Text(summary.vibe.joinToString(" · "), style = MaterialTheme.typography.titleMedium, color = PlaylogColors.Background)
            }
            Text(stringResource(R.string.recap_brand), style = MaterialTheme.typography.labelLarge, color = PlaylogColors.Background.copy(alpha = BodyAlpha))
        }
        PrimaryButton(
            stringResource(R.string.recap_share),
            onClick = { scope.launch { runCatching { shareRecapImage(context, layer.toImageBitmap().asAndroidBitmap()) } } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OutroStat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = PlaylogColors.Background)
        Text(label, style = MaterialTheme.typography.labelMedium, color = PlaylogColors.Background)
    }
}

private fun introRes(period: ActivityPeriod) = when (period) {
    ActivityPeriod.WEEK -> R.string.recap_intro_WEEK
    ActivityPeriod.MONTH -> R.string.recap_intro_MONTH
    ActivityPeriod.YEAR -> R.string.recap_intro_YEAR
}

private fun rangeText(start: java.time.LocalDate, end: java.time.LocalDate): String =
    shortDate(start) + " – " + shortDate(end)

private fun shortDate(date: java.time.LocalDate): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(date)

private const val CountUpMillis = 900
private const val FilmMinutes = 120L
private const val Percent = 100
private const val BodyAlpha = 0.8f
private const val MinShare = 0.02f
private const val CardAspect = 0.8f
private val HugeSize = 72.sp
private val Scrim = Color(0xF0101018)
private val RankColors = listOf(PlaylogColors.Lime, PlaylogColors.Sky, PlaylogColors.Violet, PlaylogColors.Pink, PlaylogColors.Orange)
