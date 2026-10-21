package app.playlog.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.component.DailyBarChart
import app.playlog.core.designsystem.component.GameImage
import app.playlog.core.designsystem.component.PlatformBadgeRow
import app.playlog.core.designsystem.component.PlatformMonogram
import app.playlog.core.designsystem.component.PlatformSplitBar
import app.playlog.core.designsystem.component.SectionHeader
import app.playlog.core.designsystem.component.Skeleton
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.BeatProgress
import app.playlog.core.model.ExternalLink
import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.GameMetadata
import app.playlog.core.model.GameStatus
import app.playlog.core.model.HowLongToBeat
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.LinkKind
import app.playlog.core.model.Platform
import app.playlog.core.model.externalLinks
import app.playlog.core.model.progressFor
import app.playlog.core.ui.ScreenshotTile
import app.playlog.core.ui.entryPlaytimeText
import app.playlog.core.ui.formatPlaytime
import app.playlog.core.ui.formatRelativeDate
import app.playlog.core.ui.platformName
import app.playlog.core.ui.platformShares
import app.playlog.core.ui.platformSplitCaption
import java.time.Duration
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun GameDetailRoute(
    onBack: () -> Unit,
    onOpenAllScreenshots: (GameId) -> Unit,
    onFindSimilar: (String) -> Unit = {},
    viewModel: GameDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GameDetailScreen(state, onBack, onOpenAllScreenshots, viewModel::toggleFavorite, viewModel::setStatus, onFindSimilar)
}

@Composable
fun GameDetailScreen(
    state: GameDetailUiState,
    onBack: () -> Unit,
    onOpenAllScreenshots: (GameId) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetStatus: (GameStatus) -> Unit,
    onFindSimilar: (String) -> Unit = {},
) {
    when (state) {
        GameDetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        GameDetailUiState.NotFound -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.game_not_found), style = MaterialTheme.typography.bodyLarge)
        }
        is GameDetailUiState.Content -> {
            val snackbar = remember { SnackbarHostState() }
            val message = stringResource(R.string.game_suggest_completed)
            val action = stringResource(R.string.game_suggest_completed_action)
            // Suggest, never set: completion is the user's call (design spec, Profile section).
            LaunchedEffect(state.entry.key, state.entry.suggestCompleted) {
                if (state.entry.suggestCompleted &&
                    snackbar.showSnackbar(message, actionLabel = action, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed
                ) {
                    onSetStatus(GameStatus.COMPLETED)
                }
            }
            Box(Modifier.fillMaxSize()) {
                GameDetailContent(state, onBack, onOpenAllScreenshots, onToggleFavorite, onSetStatus, onFindSimilar)
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun GameDetailContent(
    state: GameDetailUiState.Content,
    onBack: () -> Unit,
    onOpenAllScreenshots: (GameId) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetStatus: (GameStatus) -> Unit,
    onFindSimilar: (String) -> Unit,
) {
    val entry = state.entry
    val metadata = state.metadata.metadata
    val uriHandler = LocalUriHandler.current
    // No browser installed throws on Android; a dead tap beats a crash.
    val open: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Hero(entry, metadata, onBack, onToggleFavorite)
        Column(
            Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.xs, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            StatusRow(entry.status, onSetStatus)
            PlaytimeCard(entry, state.lastDays, state.metadata)
            if (metadata != null) Scores(metadata, open)
            Owned(entry, open)
            SimilarCard(entry.title, metadata?.genres.orEmpty(), onFindSimilar = { onFindSimilar(entry.title) })
            LastDays(state)
            if (state.screenshots.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader(stringResource(R.string.game_screenshots)) {
                        TextButton(onClick = { onOpenAllScreenshots(entry.primary.id) }) {
                            Text(stringResource(R.string.game_screenshots_all, state.screenshots.size))
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(end = Spacing.lg)) {
                        items(state.screenshots.take(MaxScreenshotsInRow), key = { it.id }) { shot ->
                            ScreenshotTile(shot, onClick = { onOpenAllScreenshots(entry.primary.id) }, modifier = Modifier.width(240.dp))
                        }
                    }
                }
            }
            Links(entry.externalLinks(metadata), open)
        }
    }
}

@Composable
private fun Hero(entry: LibraryEntry, metadata: GameMetadata?, onBack: () -> Unit, onToggleFavorite: () -> Unit) {
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth().height(HeroHeight)) {
        GameImage(
            urls = listOf(entry.heroUrl, entry.coverUrl),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        // Scrim: the title sits on the art and must stay readable on any image.
        Box(
            Modifier
                .fillMaxWidth()
                .height(HeroScrimHeight)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, background.copy(alpha = 0.85f), background))),
        )
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundIconButton(onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.game_back))
            }
            RoundIconButton(onToggleFavorite) {
                Icon(
                    if (entry.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(if (entry.isFavorite) R.string.game_unfavorite else R.string.game_favorite),
                    tint = if (entry.isFavorite) FavoriteRed else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.Bottom,
        ) {
            GameImage(
                urls = listOf(entry.coverUrl, entry.fallbackCoverUrl),
                contentDescription = null,
                placeholderText = entry.title,
                modifier = Modifier
                    .size(width = 92.dp, height = 124.dp)
                    .clip(MaterialTheme.shapes.small)
                    .border(2.dp, background, MaterialTheme.shapes.small),
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.title, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                byline(metadata)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PlatformBadgeRow(entry.platforms)
            }
        }
    }
}

private fun byline(metadata: GameMetadata?): String? =
    listOfNotNull(metadata?.developer, metadata?.releaseYear?.toString(), metadata?.genres?.take(2)?.joinToString(", ")?.ifEmpty { null })
        .joinToString(" · ")
        .ifEmpty { null }

@Composable
private fun StatusRow(status: GameStatus, onSetStatus: (GameStatus) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        StatusChoices.forEach { option ->
            val selected = option == status
            FilterChip(
                selected = selected,
                // Tapping the active status again clears it.
                onClick = { onSetStatus(if (selected) GameStatus.NONE else option) },
                label = { Text(statusLabel(option), style = MaterialTheme.typography.labelLarge) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = Color.Transparent,
                ),
                modifier = Modifier.heightIn(min = 40.dp),
            )
        }
    }
}

/** Total time, recent activity and, when known, HowLongToBeat's estimates with the user's place on them. */
@Composable
private fun PlaytimeCard(entry: LibraryEntry, lastDays: List<app.playlog.core.model.ActivityBucket>, metadata: MetadataUi) {
    Card {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.game_your_time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(entryPlaytimeText(entry), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
            }
            Column(horizontalAlignment = Alignment.End) {
                val today = lastDays.lastOrNull()?.total ?: Duration.ZERO
                Text(
                    if (today.isZero) {
                        stringResource(R.string.game_last_played_at, formatRelativeDate(entry.lastPlayedAt))
                    } else {
                        stringResource(R.string.game_today, formatPlaytime(today))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.firstPlayedAt?.let {
                    Text(
                        stringResource(R.string.game_first_played, formatRelativeDate(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                entry.achievementProgress?.let {
                    Text(
                        stringResource(R.string.game_achievements_value, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val hltb = metadata.metadata?.howLongToBeat
        when {
            hltb != null -> hltb.progressFor(entry.totalPlaytime)?.let { BeatBar(hltb, it) }
            metadata.loading -> Skeleton(Modifier.fillMaxWidth().height(56.dp))
        }
    }
}

@Composable
private fun BeatBar(hltb: HowLongToBeat, progress: BeatProgress) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    val tick = MaterialTheme.colorScheme.background
    val summary = beatSummary(progress)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.semantics(mergeDescendants = true) {}) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(track)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.fraction).background(fill))
            progress.marks.filter { it < 1f }.forEach { mark ->
                Box(Modifier.offset(x = maxWidth * mark).width(2.dp).fillMaxHeight().background(tick))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Estimate(hltb.mainStory, stringResource(R.string.game_hltb_main), Modifier.weight(1f))
            Estimate(hltb.mainExtra, stringResource(R.string.game_hltb_extra), Modifier.weight(1f))
            Estimate(hltb.completionist, stringResource(R.string.game_hltb_100), Modifier.weight(1f))
        }
        Text(summary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun beatSummary(progress: BeatProgress): String {
    val text = when (progress.stage) {
        BeatProgress.Stage.COMPLETIONIST -> stringResource(R.string.game_hltb_stage_100)
        BeatProgress.Stage.EXTRAS_DONE -> stringResource(R.string.game_hltb_stage_extra)
        BeatProgress.Stage.STORY_DONE -> stringResource(R.string.game_hltb_stage_story)
        BeatProgress.Stage.STARTED -> progress.leftForStory?.let { stringResource(R.string.game_hltb_left, formatPlaytime(it)) }
            ?: stringResource(R.string.game_hltb_stage_story)
    }
    return "$text ${stringResource(R.string.game_hltb_source)}"
}

@Composable
private fun Estimate(value: Duration?, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value?.let { formatPlaytime(it) } ?: "—", style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Scores(metadata: GameMetadata, open: (String) -> Unit) {
    val critic = metadata.criticScore
    val reviews = metadata.userReviews
    val players = metadata.howLongToBeat?.userScore
    if (critic == null && reviews == null && players == null) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.game_scores))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (critic != null) {
                ScoreTile(stringResource(R.string.game_score_metacritic), Modifier.weight(1f), onClick = critic.url?.let { { open(it) } }) {
                    Box(
                        Modifier.size(44.dp).clip(MaterialTheme.shapes.small).background(metacriticColor(critic.score)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            critic.score.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (metacriticColor(critic.score) == MetacriticMixed) MetacriticOnMixed else Color.White,
                        )
                    }
                }
            }
            if (reviews != null) {
                ScoreTile(stringResource(R.string.game_score_steam, reviews.total), Modifier.weight(1f)) {
                    Text("${reviews.percentPositive}%", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.heightIn(min = 44.dp))
                }
            }
            if (players != null) {
                ScoreTile(stringResource(R.string.game_score_hltb), Modifier.weight(1f)) {
                    Text("$players", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.heightIn(min = 44.dp))
                }
            }
        }
    }
}

@Composable
private fun ScoreTile(label: String, modifier: Modifier, onClick: (() -> Unit)? = null, value: @Composable () -> Unit) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        value()
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

/** Metacritic's own bands; the number is always shown, so color is not the only cue. */
private fun metacriticColor(score: Int): Color = when {
    score >= MetacriticGoodFrom -> MetacriticGood
    score >= MetacriticMixedFrom -> MetacriticMixed
    else -> MetacriticBad
}

/** One row per platform the title is owned on: where the hours, achievements and last session come from. */
@Composable
private fun Owned(entry: LibraryEntry, open: (String) -> Unit) {
    val links = entry.externalLinks(null).associateBy { it.kind }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.game_owned))
        if (entry.platforms.size > 1) {
            PlatformSplitBar(entry.platformShares(), stringResource(R.string.game_split_description, platformSplitCaption(entry)))
        }
        entry.games.sortedByDescending { it.playtime }.forEach { game ->
            val link = links[storeKind(game.id.platform)]
            OwnedRow(game, onClick = link?.let { { open(it.url) } })
        }
    }
}

@Composable
private fun OwnedRow(game: Game, onClick: (() -> Unit)?) {
    val meta = listOfNotNull(
        game.devices.sorted().joinToString(", ").ifEmpty { null },
        game.lastPlayedAt?.let { stringResource(R.string.game_last_played_at, formatRelativeDate(it)) },
        game.playCount?.let { stringResource(R.string.game_sessions, it) },
    ).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformMonogram(game.id.platform, size = 40.dp)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(platformName(game.id.platform), style = MaterialTheme.typography.titleMedium)
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatPlaytime(game.playtime), style = MaterialTheme.typography.titleLarge)
            game.achievementProgress?.let {
                Text(
                    stringResource(R.string.game_achievements_value, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LastDays(state: GameDetailUiState.Content) {
    val total = state.lastDays.fold(Duration.ZERO) { acc, b -> acc + b.total }
    val activeDays = state.lastDays.count { !it.total.isZero }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.game_last_14_days)) {
            Text(
                stringResource(R.string.game_last_14_days_summary, formatPlaytime(total), activeDays),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DailyBarChart(
            values = state.lastDays.map { it.total.toMinutes().toFloat() },
            contentDescription = stringResource(R.string.game_chart_description, formatPlaytime(total)),
            labels = state.lastDays.mapIndexed { i, b ->
                if (i % 2 == state.lastDays.lastIndex % 2) b.start.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) else ""
            },
        )
    }
}

@Composable
private fun Links(links: List<ExternalLink>, open: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.game_links))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
        ) {
            links.forEachIndexed { i, link ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val label = linkLabel(link.kind)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(role = Role.Button, onClick = { open(link.url) })
                        .semantics { contentDescription = label }
                        .padding(horizontal = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        link.url.substringAfter("://").substringBefore('/'),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun linkLabel(kind: LinkKind): String = stringResource(
    when (kind) {
        LinkKind.STEAM_STORE -> R.string.game_link_steam
        LinkKind.PLAYSTATION_STORE -> R.string.game_link_playstation
        LinkKind.NINTENDO_ESHOP -> R.string.game_link_nintendo
        LinkKind.HOWLONGTOBEAT -> R.string.game_link_hltb
        LinkKind.METACRITIC -> R.string.game_link_metacritic
    },
)

private fun storeKind(platform: Platform) = when (platform) {
    Platform.STEAM -> LinkKind.STEAM_STORE
    Platform.PLAYSTATION -> LinkKind.PLAYSTATION_STORE
    Platform.NINTENDO -> LinkKind.NINTENDO_ESHOP
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraLarge)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) { content() }
}

@Composable
private fun statusLabel(status: GameStatus): String = stringResource(
    when (status) {
        GameStatus.NONE -> R.string.game_status_NONE
        GameStatus.PLAYING -> R.string.game_status_PLAYING
        GameStatus.COMPLETED -> R.string.game_status_COMPLETED
        GameStatus.SHELVED -> R.string.game_status_SHELVED
        GameStatus.DROPPED -> R.string.game_status_DROPPED
    },
)

@Composable
private fun RoundIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) { content() }
}

private val StatusChoices = listOf(GameStatus.PLAYING, GameStatus.COMPLETED, GameStatus.SHELVED, GameStatus.DROPPED)
private val FavoriteRed = Color(0xFFFF6B6B)
private val HeroHeight = 320.dp
private val HeroScrimHeight = 220.dp
private const val MaxScreenshotsInRow = 10
private const val MetacriticGoodFrom = 75
private const val MetacriticMixedFrom = 50
private val MetacriticGood = Color(0xFF2E7D32)
private val MetacriticMixed = Color(0xFFF9A825)
private val MetacriticOnMixed = Color(0xFF1B1B1B)
private val MetacriticBad = Color(0xFFC62828)
