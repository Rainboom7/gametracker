package app.playlog.feature.discover

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.component.GameImage
import app.playlog.core.designsystem.component.PlatformBadgeRow
import app.playlog.core.designsystem.component.PlatformFilterChip
import app.playlog.core.designsystem.component.SectionHeader
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.Anniversary
import app.playlog.core.model.DealPick
import app.playlog.core.model.DealsState
import app.playlog.core.model.DiscoverShelf
import app.playlog.core.model.DiscoverSummary
import app.playlog.core.model.GameId
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Milestone
import app.playlog.core.model.MilestoneKind
import app.playlog.core.model.Pick
import app.playlog.core.model.PickReason
import app.playlog.core.model.Platform
import app.playlog.core.ui.DealDetailSheet
import app.playlog.core.ui.entryPlaytimeText
import app.playlog.core.ui.formatPlaytime
import app.playlog.core.ui.platformName
import app.playlog.feature.deals.GiveawaysHomeCard
import java.time.Duration

@Composable
fun DiscoverRoute(
    onOpenGame: (GameId) -> Unit,
    onOpenAllDeals: () -> Unit,
    onOpenGiveaways: () -> Unit = {},
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deals by viewModel.deals.collectAsStateWithLifecycle()
    val openDeal by viewModel.openDeal.collectAsStateWithLifecycle()
    DiscoverScreen(
        state,
        onOpenGame,
        viewModel::spin,
        viewModel::selectRoulettePlatform,
        deals = deals,
        onOpenDeal = viewModel::openDeal,
        onRefreshDeals = viewModel::refreshDeals,
        onOpenAllDeals = onOpenAllDeals,
        giveaways = { GiveawaysHomeCard(onOpenAll = onOpenGiveaways) },
    )
    openDeal?.let { DealDetailSheet(it, onDismiss = { viewModel.openDeal(null) }) }
}

@Composable
fun DiscoverScreen(
    state: DiscoverUiState,
    onOpenGame: (GameId) -> Unit,
    onSpin: () -> Unit,
    onSelectRoulettePlatform: (Platform?) -> Unit,
    deals: DealsState = DealsState.NoTaste,
    onOpenDeal: (DealPick) -> Unit = {},
    onRefreshDeals: () -> Unit = {},
    onOpenAllDeals: () -> Unit = {},
    giveaways: @Composable () -> Unit = {},
) {
    val content = state as? DiscoverUiState.Content
    val summary = content?.summary
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        item {
            Column(Modifier.padding(horizontal = Spacing.lg)) {
                Text(stringResource(R.string.discover_title), style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(R.string.discover_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            summary == null -> item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            summary.isEmpty() -> item {
                Text(
                    stringResource(R.string.discover_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
            else -> {
                summary.topPick?.let { pick ->
                    item(key = "top") { TopPickCard(pick, onClick = { onOpenGame(pick.entry.primary.id) }, Modifier.padding(horizontal = Spacing.lg)) }
                }
                item(key = "deals") { DealsShelf(deals, onOpen = onOpenDeal, onRefresh = onRefreshDeals, onOpenAll = onOpenAllDeals) }
                item(key = "giveaways") { giveaways() }
                item(key = "roulette") {
                    RouletteCard(
                        state = content?.roulette ?: RouletteState(),
                        backlogCount = summary.backlogCount,
                        onSpin = onSpin,
                        onSelectPlatform = onSelectRoulettePlatform,
                        onOpen = onOpenGame,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
                items(summary.shelves, key = { it.reason }) { shelf -> Shelf(shelf, onOpenGame) }
                if (summary.anniversaries.isNotEmpty()) {
                    item(key = "anniversaries") { Anniversaries(summary.anniversaries, onOpenGame, Modifier.padding(horizontal = Spacing.lg)) }
                }
                item(key = "milestones") { Milestones(summary.milestones, Modifier.padding(horizontal = Spacing.lg)) }
            }
        }
    }
}

private fun DiscoverSummary.isEmpty() = milestones.first { it.kind == MilestoneKind.COLLECTOR }.value == 0L

@Composable
private fun TopPickCard(pick: Pick, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val entry = pick.entry
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(TopPickAspect)
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        GameImage(
            urls = listOf(entry.heroUrl, entry.coverUrl, entry.fallbackCoverUrl),
            contentDescription = null,
            placeholderText = entry.title,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(TopPickScrimStart to Color.Transparent, 1f to TopPickScrim),
            ),
        )
        PlatformBadgeRow(entry.platforms, onImage = true, modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm))
        Column(Modifier.align(Alignment.BottomStart).padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.discover_top_pick), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(4.dp))
            Text(entry.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(reasonText(pick), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f), maxLines = 2)
        }
    }
}

@Composable
private fun RouletteCard(
    state: RouletteState,
    backlogCount: Int,
    onSpin: () -> Unit,
    onSelectPlatform: (Platform?) -> Unit,
    onOpen: (GameId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Casino, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.discover_roulette_title), style = MaterialTheme.typography.titleLarge)
            }
            Text(
                stringResource(R.string.discover_roulette_hint, backlogCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                PlatformFilterChip(platformName(null), selected = state.platform == null, onClick = { onSelectPlatform(null) })
                Platform.entries.forEach { p ->
                    PlatformFilterChip(platformName(p), selected = state.platform == p, onClick = { onSelectPlatform(p) }, platform = p)
                }
            }
            AnimatedContent(
                targetState = state,
                contentKey = { it.spins },
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut() },
                label = "roulette",
            ) { s ->
                when {
                    s.result != null -> RouletteResult(s.result, onClick = { onOpen(s.result.primary.id) })
                    s.empty -> Text(stringResource(R.string.discover_roulette_empty), style = MaterialTheme.typography.bodyMedium)
                    else -> Spacer(Modifier.height(0.dp))
                }
            }
            Button(onClick = onSpin, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.result == null) R.string.discover_roulette_spin else R.string.discover_roulette_again))
            }
        }
    }
}

@Composable
private fun RouletteResult(entry: LibraryEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameImage(
            urls = listOf(entry.coverUrl, entry.fallbackCoverUrl),
            contentDescription = null,
            placeholderText = entry.title,
            modifier = Modifier.size(width = 60.dp, height = 80.dp).clip(MaterialTheme.shapes.small),
        )
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(entryPlaytimeText(entry), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            PlatformBadgeRow(entry.platforms)
        }
        FilledTonalButton(onClick = onClick) { Text(stringResource(R.string.discover_open)) }
    }
}

@Composable
private fun Shelf(shelf: DiscoverShelf, onOpenGame: (GameId) -> Unit) {
    Column {
        Column(Modifier.padding(horizontal = Spacing.lg)) {
            SectionHeader(stringResource(shelfTitle(shelf.reason)))
            Text(
                stringResource(shelfHint(shelf.reason)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(shelf.picks, key = { it.entry.key }) { pick -> PickCard(pick, onClick = { onOpenGame(pick.entry.primary.id) }) }
        }
    }
}

@Composable
private fun PickCard(pick: Pick, onClick: () -> Unit) {
    val entry = pick.entry
    Column(Modifier.width(128.dp).clickable(role = Role.Button, onClick = onClick)) {
        Box {
            GameImage(
                urls = listOf(entry.coverUrl, entry.fallbackCoverUrl),
                contentDescription = entry.title,
                placeholderText = entry.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(CoverAspect).clip(MaterialTheme.shapes.small),
            )
            PlatformBadgeRow(entry.platforms, onImage = true, modifier = Modifier.padding(6.dp))
        }
        if (pick.reason == PickReason.ALMOST_DONE) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (entry.achievementProgress ?: 0) / 100f },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(MaterialTheme.shapes.extraSmall),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(entry.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            pickCaption(pick),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Anniversaries(items: List<Anniversary>, onOpenGame: (GameId) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(stringResource(R.string.discover_anniversaries))
        items.take(MaxAnniversaries).forEach { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(role = Role.Button) { onOpenGame(a.entry.primary.id) }
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    GameImage(
                        urls = listOf(a.entry.coverUrl, a.entry.fallbackCoverUrl),
                        contentDescription = null,
                        placeholderText = a.entry.title,
                        modifier = Modifier.size(width = 44.dp, height = 58.dp).clip(MaterialTheme.shapes.extraSmall),
                    )
                    Icon(
                        Icons.Default.Cake,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(3.dp),
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(a.entry.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        pluralStringResource(R.plurals.discover_years_ago, a.years, a.years),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(formatPlaytime(a.entry.totalPlaytime), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun Milestones(milestones: List<Milestone>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.discover_milestones))
        // Earned badges first, the closest to levelling up leading.
        val ordered = milestones.sortedWith(compareByDescending<Milestone> { it.level > 0 }.thenByDescending { it.progressToNext })
        ordered.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { MilestoneCard(it, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MilestoneCard(milestone: Milestone, modifier: Modifier = Modifier) {
    val earned = milestone.level > 0
    val accent = if (earned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (earned) accent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(milestoneIcon(milestone.kind), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(Spacing.xs))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(milestoneTitle(milestone.kind)),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (milestone.isMaxed) {
                            stringResource(R.string.discover_milestone_maxed)
                        } else {
                            stringResource(R.string.discover_milestone_level, milestone.level, milestone.maxLevel)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(milestoneValue(milestone.kind, milestone.value), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(milestoneDescription(milestone.kind)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xs))
            LinearProgressIndicator(
                progress = { milestone.progressToNext },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(MaterialTheme.shapes.extraSmall),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            milestone.nextTarget?.let { target ->
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.discover_milestone_next, milestoneValue(milestone.kind, target)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun milestoneValue(kind: MilestoneKind, value: Long): String = when (kind) {
    MilestoneKind.MARATHON, MilestoneKind.VETERAN -> formatPlaytime(Duration.ofHours(value))
    else -> value.toString()
}

@Composable
private fun daysAgoText(days: Long): String = when {
    days <= 0 -> stringResource(R.string.discover_today)
    days == 1L -> stringResource(R.string.discover_yesterday)
    else -> pluralStringResource(R.plurals.discover_days_ago, days.toInt(), days.toInt())
}

@Composable
private fun reasonText(pick: Pick): String = when (pick.reason) {
    PickReason.CONTINUE -> stringResource(R.string.discover_reason_CONTINUE, daysAgoText(pick.daysSinceLastPlayed ?: 0))
    PickReason.ALMOST_DONE -> stringResource(R.string.discover_reason_ALMOST_DONE, pick.entry.achievementProgress ?: 0)
    PickReason.RETURN_TO -> pick.daysSinceLastPlayed?.let { days ->
        stringResource(
            R.string.discover_reason_RETURN_TO,
            formatPlaytime(pick.entry.totalPlaytime),
            pluralStringResource(R.plurals.discover_for_days, days.toInt(), days.toInt()),
        )
    } ?: stringResource(R.string.discover_reason_RETURN_TO_unknown, formatPlaytime(pick.entry.totalPlaytime))
    PickReason.BACKLOG -> stringResource(R.string.discover_reason_BACKLOG)
}

@Composable
private fun pickCaption(pick: Pick): String = when (pick.reason) {
    PickReason.CONTINUE -> daysAgoText(pick.daysSinceLastPlayed ?: 0)
    PickReason.ALMOST_DONE -> stringResource(R.string.discover_caption_progress, pick.entry.achievementProgress ?: 0)
    PickReason.RETURN_TO -> pick.daysSinceLastPlayed?.let { "${formatPlaytime(pick.entry.totalPlaytime)} · ${daysAgoText(it)}" }
        ?: formatPlaytime(pick.entry.totalPlaytime)
    PickReason.BACKLOG ->
        if (pick.entry.totalPlaytime.isZero) stringResource(R.string.discover_caption_not_started) else formatPlaytime(pick.entry.totalPlaytime)
}

@StringRes
private fun shelfTitle(reason: PickReason): Int = when (reason) {
    PickReason.CONTINUE -> R.string.discover_shelf_CONTINUE
    PickReason.ALMOST_DONE -> R.string.discover_shelf_ALMOST_DONE
    PickReason.RETURN_TO -> R.string.discover_shelf_RETURN_TO
    PickReason.BACKLOG -> R.string.discover_shelf_BACKLOG
}

@StringRes
private fun shelfHint(reason: PickReason): Int = when (reason) {
    PickReason.CONTINUE -> R.string.discover_shelf_hint_CONTINUE
    PickReason.ALMOST_DONE -> R.string.discover_shelf_hint_ALMOST_DONE
    PickReason.RETURN_TO -> R.string.discover_shelf_hint_RETURN_TO
    PickReason.BACKLOG -> R.string.discover_shelf_hint_BACKLOG
}

@StringRes
private fun milestoneTitle(kind: MilestoneKind): Int = when (kind) {
    MilestoneKind.MARATHON -> R.string.discover_milestone_MARATHON
    MilestoneKind.VETERAN -> R.string.discover_milestone_VETERAN
    MilestoneKind.COLLECTOR -> R.string.discover_milestone_COLLECTOR
    MilestoneKind.FINISHER -> R.string.discover_milestone_FINISHER
    MilestoneKind.PERFECTIONIST -> R.string.discover_milestone_PERFECTIONIST
    MilestoneKind.MULTIPLATFORM -> R.string.discover_milestone_MULTIPLATFORM
    MilestoneKind.STREAK -> R.string.discover_milestone_STREAK
}

@StringRes
private fun milestoneDescription(kind: MilestoneKind): Int = when (kind) {
    MilestoneKind.MARATHON -> R.string.discover_milestone_desc_MARATHON
    MilestoneKind.VETERAN -> R.string.discover_milestone_desc_VETERAN
    MilestoneKind.COLLECTOR -> R.string.discover_milestone_desc_COLLECTOR
    MilestoneKind.FINISHER -> R.string.discover_milestone_desc_FINISHER
    MilestoneKind.PERFECTIONIST -> R.string.discover_milestone_desc_PERFECTIONIST
    MilestoneKind.MULTIPLATFORM -> R.string.discover_milestone_desc_MULTIPLATFORM
    MilestoneKind.STREAK -> R.string.discover_milestone_desc_STREAK
}

private fun milestoneIcon(kind: MilestoneKind): ImageVector = when (kind) {
    MilestoneKind.MARATHON -> Icons.Default.Timer
    MilestoneKind.VETERAN -> Icons.Default.MilitaryTech
    MilestoneKind.COLLECTOR -> Icons.Default.CollectionsBookmark
    MilestoneKind.FINISHER -> Icons.Default.Flag
    MilestoneKind.PERFECTIONIST -> Icons.Default.EmojiEvents
    MilestoneKind.MULTIPLATFORM -> Icons.Default.Devices
    MilestoneKind.STREAK -> Icons.Default.LocalFireDepartment
}

private const val MaxAnniversaries = 5
private const val TopPickAspect = 16f / 10f
private const val CoverAspect = 3f / 4f
private val TopPickScrim = Color(0xF20D0E12)
private const val TopPickScrimStart = 0.25f
