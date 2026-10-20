package app.playlog.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.component.DailyBarChart
import app.playlog.core.designsystem.component.GameImage
import app.playlog.core.designsystem.component.PlatformBadgeRow
import app.playlog.core.designsystem.component.PlatformSplitBar
import app.playlog.core.designsystem.component.SectionHeader
import app.playlog.core.designsystem.component.StatTile
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.GameId
import app.playlog.core.ui.ScreenshotTile
import app.playlog.core.ui.entryPlaytimeText
import app.playlog.core.ui.formatPlaytime
import app.playlog.core.ui.formatRelativeDate
import app.playlog.core.ui.platformShares
import app.playlog.core.ui.platformSplitCaption
import java.time.Duration
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun GameDetailRoute(
    onBack: () -> Unit,
    onOpenAllScreenshots: (GameId) -> Unit,
    viewModel: GameDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GameDetailScreen(state, onBack, onOpenAllScreenshots, viewModel::toggleFavorite)
}

@Composable
fun GameDetailScreen(
    state: GameDetailUiState,
    onBack: () -> Unit,
    onOpenAllScreenshots: (GameId) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    when (state) {
        GameDetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        GameDetailUiState.NotFound -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.game_not_found), style = MaterialTheme.typography.bodyLarge)
        }
        is GameDetailUiState.Content -> GameDetailContent(state, onBack, onOpenAllScreenshots, onToggleFavorite)
    }
}

@Composable
private fun GameDetailContent(
    state: GameDetailUiState.Content,
    onBack: () -> Unit,
    onOpenAllScreenshots: (GameId) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val entry = state.entry
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(HERO_HEIGHT)) {
            GameImage(
                urls = listOf(entry.heroUrl, entry.coverUrl),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                Modifier.fillMaxWidth().padding(Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RoundIconButton(onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.game_back))
                }
                RoundIconButton(onToggleFavorite) {
                    Icon(
                        if (entry.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(if (entry.isFavorite) R.string.game_unfavorite else R.string.game_favorite),
                    )
                }
            }
        }
        Row(
            Modifier.padding(horizontal = Spacing.lg).offset(y = -COVER_OVERLAP),
            verticalAlignment = Alignment.Bottom,
        ) {
            GameImage(
                urls = listOf(entry.coverUrl, entry.fallbackCoverUrl),
                contentDescription = null,
                placeholderText = entry.title,
                modifier = Modifier.size(width = 88.dp, height = 118.dp).clip(MaterialTheme.shapes.small),
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Spacing.xs))
                PlatformBadgeRow(entry.platforms)
            }
        }
        Column(
            Modifier.padding(horizontal = Spacing.lg).offset(y = -COVER_OVERLAP + Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile(entryPlaytimeText(entry), stringResource(R.string.game_total_hours), Modifier.weight(1f), valueColor = MaterialTheme.colorScheme.primary)
                StatTile(formatRelativeDate(entry.lastPlayedAt), stringResource(R.string.game_last_played), Modifier.weight(1f))
                StatTile(
                    entry.achievementProgress?.let { "$it%" } ?: stringResource(R.string.game_no_value),
                    stringResource(R.string.game_achievements),
                    Modifier.weight(1f),
                )
            }
            if (entry.platforms.size > 1) {
                val caption = platformSplitCaption(entry)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    PlatformSplitBar(entry.platformShares(), stringResource(R.string.game_split_description, caption))
                    Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val total = state.lastDays.fold(Duration.ZERO) { acc, b -> acc + b.total }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionHeader(stringResource(R.string.game_last_14_days)) {
                    Text(formatPlaytime(total), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DailyBarChart(
                    values = state.lastDays.map { it.total.toMinutes().toFloat() },
                    contentDescription = stringResource(R.string.game_chart_description, formatPlaytime(total)),
                    labels = state.lastDays.mapIndexed { i, b ->
                        if (i % 2 == state.lastDays.lastIndex % 2) b.start.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) else ""
                    },
                )
            }
            if (state.screenshots.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader(stringResource(R.string.game_screenshots)) {
                        TextButton(onClick = { onOpenAllScreenshots(entry.primary.id) }) {
                            Text(stringResource(R.string.game_screenshots_all, state.screenshots.size))
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(end = Spacing.lg)) {
                        items(state.screenshots.take(MAX_SCREENSHOTS_IN_ROW), key = { it.id }) { shot ->
                            ScreenshotTile(shot, onClick = { onOpenAllScreenshots(entry.primary.id) }, modifier = Modifier.width(240.dp))
                        }
                    }
                }
            }
            entry.firstPlayedAt?.let {
                Text(
                    stringResource(R.string.game_first_played, formatRelativeDate(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val devices = entry.games.flatMap { it.devices }.distinct()
            if (devices.isNotEmpty()) {
                Text(
                    stringResource(R.string.game_devices, devices.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun RoundIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) { content() }
}

private val HERO_HEIGHT = 220.dp
private val COVER_OVERLAP = 60.dp
