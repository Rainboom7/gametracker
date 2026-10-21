package app.playlog.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.component.DailyBarChart
import app.playlog.core.designsystem.component.GameImage
import app.playlog.core.designsystem.component.GradientCard
import app.playlog.core.designsystem.component.PlatformBadgeRow
import app.playlog.core.designsystem.component.PrimaryButton
import app.playlog.core.designsystem.component.Skeleton
import app.playlog.core.designsystem.component.StatBubble
import app.playlog.core.designsystem.theme.PlaylogColors
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Pick
import app.playlog.core.model.PickReason
import app.playlog.core.ui.DealCard
import app.playlog.feature.deals.GiveawaysHomeCard
import app.playlog.feature.journal.BadgesStrip
import app.playlog.feature.journal.TodayHomeCard
import app.playlog.core.ui.GameCoverCard
import app.playlog.core.ui.ScreenshotTile
import app.playlog.core.ui.entryPlaytimeText
import app.playlog.core.ui.formatPlaytime
import app.playlog.core.ui.formatRelativeDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeRoute(actions: HomeActions, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(state, actions, onSetGoal = viewModel::setGoal)
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    onSetGoal: (hours: Int, isLimit: Boolean) -> Unit = { _, _ -> },
) {
    when (state) {
        HomeUiState.Loading -> HomeSkeleton(modifier)
        is HomeUiState.Empty -> HomeEmpty(state, actions.onConnect, modifier)
        is HomeUiState.Content -> HomeContent(state, actions, modifier, onSetGoal)
    }
}

@Composable
private fun HomeContent(state: HomeUiState.Content, actions: HomeActions, modifier: Modifier, onSetGoal: (Int, Boolean) -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            Greeting(
                state.dayPart,
                stringResource(R.string.home_subtitle, state.gameCount, formatPlaytime(state.totalPlaytime)),
            )
        }
        item { AskPill(onClick = actions.onAsk) }
        state.continuePlaying?.let { entry ->
            item { ContinueHero(entry, onClick = { actions.onOpenGame(entry.primary.id) }) }
        }
        // Anniversaries of first launches and old screenshots; nothing on an ordinary day.
        item {
            TodayHomeCard(
                onOpenGame = actions.onOpenGame,
                onOpenScreenshots = actions.onOpenScreenshotsOf,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
        item { StatBubbles(state) }
        // Hides itself until the first badge is earned.
        item { BadgesStrip(onOpenAll = actions.onOpenBadges) }
        item { WeekCard(state, onClick = actions.onOpenActivity, onOpenRecap = actions.onOpenRecap, onSetGoal = onSetGoal) }
        state.topPick?.let { pick ->
            item { PickCard(pick, onClick = { actions.onOpenGame(pick.entry.primary.id) }, onMore = actions.onOpenDiscover) }
        }
        if (state.deals.isNotEmpty()) {
            item {
                Section(stringResource(R.string.home_deals), stringResource(R.string.home_all), actions.onOpenDeals) {
                    val uriHandler = LocalUriHandler.current
                    LazyRow(contentPadding = PaddingValues(horizontal = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(state.deals, key = { it.key }) { pick ->
                            DealCard(pick, onClick = { runCatching { uriHandler.openUri(pick.storeUrl) } })
                        }
                    }
                }
            }
        }
        // Renders nothing while no store gives anything away.
        item { GiveawaysHomeCard(onOpenAll = actions.onOpenGiveaways) }
        if (state.recent.isNotEmpty()) {
            item {
                Section(stringResource(R.string.home_recent), stringResource(R.string.home_all_games), actions.onOpenLibrary) {
                    LazyRow(contentPadding = PaddingValues(horizontal = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(state.recent, key = { it.key }) { entry ->
                            GameCoverCard(entry, onClick = { actions.onOpenGame(entry.primary.id) }, modifier = Modifier.width(112.dp))
                        }
                    }
                }
            }
        }
        if (state.screenshots.isNotEmpty()) {
            item {
                Section(stringResource(R.string.home_screenshots), stringResource(R.string.home_all), actions.onOpenScreenshots) {
                    LazyRow(contentPadding = PaddingValues(horizontal = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(state.screenshots, key = { it.screenshot.id }) { shot ->
                            ScreenshotTile(
                                shot.screenshot,
                                onClick = { shot.screenshot.gameId?.let(actions.onOpenGame) ?: actions.onOpenScreenshots() },
                                title = shot.title,
                                modifier = Modifier.width(220.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Greeting(dayPart: DayPart, subtitle: String) {
    Column(Modifier.padding(horizontal = Spacing.lg)) {
        Text(greeting(dayPart), style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
@ReadOnlyComposable
private fun greeting(dayPart: DayPart): String = stringResource(
    when (dayPart) {
        DayPart.MORNING -> R.string.home_greeting_morning
        DayPart.DAY -> R.string.home_greeting_day
        DayPart.EVENING -> R.string.home_greeting_evening
        DayPart.NIGHT -> R.string.home_greeting_night
    },
)

/** Big art card for the last played title, washed violet to pink so it reads as the screen's hero. */
@Composable
private fun ContinueHero(entry: LibraryEntry, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = Spacing.lg)
            .fillMaxWidth()
            .height(HeroHeight)
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        GameImage(
            urls = listOf(entry.heroUrl, entry.coverUrl, entry.fallbackCoverUrl),
            contentDescription = null,
            placeholderText = entry.title,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(HeroTintStart, HeroTintEnd))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(HeroScrimStart to Color.Transparent, 1f to HeroScrim)))
        Column(Modifier.align(Alignment.BottomStart).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                stringResource(R.string.home_hero_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = Spacing.xs, vertical = 2.dp),
            )
            Text(entry.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                PlatformBadgeRow(entry.platforms, onImage = true)
                Text(
                    entryPlaytimeText(entry) + " · " + stringResource(R.string.home_hero_last, formatRelativeDate(entry.lastPlayedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = HeroCaption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatBubbles(state: HomeUiState.Content) {
    val bubbles = listOf(
        Bubble(Icons.Default.LocalFireDepartment, state.streakDays.toString(), stringResource(R.string.home_stat_streak), PlaylogColors.Orange),
        Bubble(Icons.Default.Schedule, formatPlaytime(state.week.total), stringResource(R.string.home_stat_week), PlaylogColors.Lime),
        Bubble(Icons.Default.SportsEsports, state.week.gamesPlayed.toString(), stringResource(R.string.home_stat_games), PlaylogColors.Sky),
        Bubble(Icons.Default.Inventory2, state.backlogCount.toString(), stringResource(R.string.home_stat_backlog), PlaylogColors.Pink),
    )
    LazyRow(contentPadding = PaddingValues(horizontal = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        items(bubbles) { b -> StatBubble(b.icon, b.value, b.label, b.accent, Modifier.width(120.dp)) }
    }
}

private data class Bubble(
    val icon: ImageVector,
    val value: String,
    val label: String,
    val accent: Color,
)

@Composable
private fun WeekCard(state: HomeUiState.Content, onClick: () -> Unit, onOpenRecap: () -> Unit, onSetGoal: (Int, Boolean) -> Unit) {
    val week = state.week
    val locale = Locale.getDefault()
    var editingGoal by rememberSaveable { mutableStateOf(false) }
    GradientCard(
        accents = listOf(PlaylogColors.Lime, PlaylogColors.Mint),
        onClick = onClick,
        modifier = Modifier.padding(horizontal = Spacing.lg).fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_week_title), style = MaterialTheme.typography.labelLarge, color = PlaylogColors.Lime)
                Text(formatPlaytime(week.total), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.home_week_previous, formatPlaytime(week.previousTotal)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GoalBadge(state.goal, week.total, onEdit = { editingGoal = true })
        }
        Spacer(Modifier.height(Spacing.sm))
        DailyBarChart(
            values = week.buckets.map { it.total.toMinutes().toFloat() },
            contentDescription = stringResource(R.string.home_week_chart_description, formatPlaytime(week.total)),
            labels = week.buckets.map { it.start.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) },
            height = 72.dp,
        )
        if (!week.total.isZero) {
            Spacer(Modifier.height(Spacing.sm))
            RecapChip(onOpenRecap)
        }
    }
    if (editingGoal) {
        GoalSheet(
            current = state.goal,
            onPick = { hours, isLimit ->
                onSetGoal(hours, isLimit)
                editingGoal = false
            },
            onDismiss = { editingGoal = false },
        )
    }
}

@Composable
private fun PickCard(pick: Pick, onClick: () -> Unit, onMore: () -> Unit) {
    GradientCard(
        accents = listOf(PlaylogColors.Violet, PlaylogColors.Sky),
        onClick = onClick,
        modifier = Modifier.padding(horizontal = Spacing.lg).fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameImage(
                urls = listOf(pick.entry.coverUrl, pick.entry.fallbackCoverUrl),
                contentDescription = null,
                placeholderText = pick.entry.title,
                modifier = Modifier.size(width = 72.dp, height = 96.dp).clip(MaterialTheme.shapes.small),
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(stringResource(R.string.home_pick_label), style = MaterialTheme.typography.labelLarge, color = PlaylogColors.Violet)
                Text(pick.entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(reason(pick.reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PlatformBadgeRow(pick.entry.platforms)
            }
        }
        TextButton(onClick = onMore, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.home_more_picks), color = PlaylogColors.Violet)
        }
    }
}

@Composable
@ReadOnlyComposable
private fun reason(reason: PickReason): String = stringResource(
    when (reason) {
        PickReason.CONTINUE -> R.string.home_pick_CONTINUE
        PickReason.ALMOST_DONE -> R.string.home_pick_ALMOST_DONE
        PickReason.RETURN_TO -> R.string.home_pick_RETURN_TO
        PickReason.BACKLOG -> R.string.home_pick_BACKLOG
    },
)

@Composable
private fun Section(title: String, action: String, onAction: () -> Unit, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(action) }
        }
        content()
    }
}

@Composable
private fun HomeSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Skeleton(Modifier.fillMaxWidth(SkeletonTitleFraction).height(32.dp))
        Skeleton(Modifier.fillMaxWidth().height(HeroHeight))
        Skeleton(Modifier.fillMaxWidth().height(110.dp))
        Skeleton(Modifier.fillMaxWidth().height(160.dp))
    }
}

@Composable
private fun HomeEmpty(state: HomeUiState.Empty, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(greeting(state.dayPart), style = MaterialTheme.typography.headlineMedium)
        GradientCard(accents = listOf(PlaylogColors.Violet, PlaylogColors.Pink), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.xxs))
            Text(stringResource(R.string.home_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.md))
            PrimaryButton(stringResource(R.string.home_empty_action), onClick = onConnect)
        }
    }
}

private val HeroHeight = 220.dp
private val HeroTintStart = Color(0x598B5CF6)
private val HeroTintEnd = Color(0x40FF4F9A)
private val HeroScrim = Color(0xF00D0E12)
private val HeroCaption = Color(0xFFE6E8EE)
private const val HeroScrimStart = 0.3f
private const val SkeletonTitleFraction = 0.6f
