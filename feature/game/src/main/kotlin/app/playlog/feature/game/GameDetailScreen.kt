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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.playlog.core.model.GameStatus
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
    GameDetailScreen(state, onBack, onOpenAllScreenshots, viewModel::toggleFavorite, viewModel::setStatus)
}

@Composable
fun GameDetailScreen(
    state: GameDetailUiState,
    onBack: () -> Unit,
    onOpenAllScreenshots: (GameId) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetStatus: (GameStatus) -> Unit,
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
                GameDetailContent(state, onBack, onOpenAllScreenshots, onToggleFavorite, onSetStatus)
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
                Spacer(Modifier.height(Spacing.xs))
                StatusChip(entry.status, onSetStatus)
            }
        }
        Column(
            Modifier.padding(horizontal = Spacing.lg).offset(y = -COVER_OVERLAP + Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile(entryPlaytimeText(entry), stringResource(R.string.game_total_hours), Modifier.weight(1f), valueColor = MaterialTheme.colorScheme.primary)
                StatTile(formatRelativeDate(entry.lastPlayedAt), stringResource(R.string.game_last_played), Modifier.weight(1f))
                ThirdTile(state, Modifier.weight(1f))
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

/** Achievements when the platform reports them, else days played in the window, else the first launch. */
@Composable
private fun ThirdTile(state: GameDetailUiState.Content, modifier: Modifier) {
    val progress = state.entry.achievementProgress
    val activeDays = state.lastDays.count { !it.total.isZero }
    when {
        progress != null -> StatTile("$progress%", stringResource(R.string.game_achievements), modifier)
        activeDays > 0 -> StatTile(activeDays.toString(), stringResource(R.string.game_active_days), modifier)
        else -> StatTile(formatRelativeDate(state.entry.firstPlayedAt), stringResource(R.string.game_first_launch), modifier)
    }
}

@Composable
private fun StatusChip(status: GameStatus, onSetStatus: (GameStatus) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(statusLabel(status)) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            colors = if (status == GameStatus.COMPLETED) {
                AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.primary,
                )
            } else {
                AssistChipDefaults.assistChipColors()
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            GameStatus.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(statusLabel(option)) },
                    onClick = {
                        open = false
                        onSetStatus(option)
                    },
                )
            }
        }
    }
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

private val HERO_HEIGHT = 220.dp
private val COVER_OVERLAP = 60.dp
