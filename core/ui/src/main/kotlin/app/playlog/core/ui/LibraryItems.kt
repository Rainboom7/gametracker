package app.playlog.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.playlog.core.designsystem.component.GameImage
import app.playlog.core.designsystem.component.PlatformBadge
import app.playlog.core.designsystem.component.PlatformBadgeRow
import app.playlog.core.designsystem.component.PlatformDot
import app.playlog.core.designsystem.theme.platformColors
import app.playlog.core.model.GameStatus
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Platform
import app.playlog.core.model.Screenshot
import app.playlog.core.model.TopGame
import java.time.Duration

/** "No data" for PS4-only titles that report zero time (PSN tracks PS4 playtime poorly). */
@Composable
fun entryPlaytimeText(entry: LibraryEntry): String =
    if (entry.totalPlaytime.isZero && entry.games.all { "PS4" in it.devices }) {
        stringResource(R.string.no_playtime_data)
    } else {
        formatPlaytime(entry.totalPlaytime)
    }

@Composable
fun GameCoverCard(entry: LibraryEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable(role = Role.Button, onClick = onClick)) {
        Box {
            GameImage(
                urls = listOf(entry.coverUrl, entry.fallbackCoverUrl),
                contentDescription = stringResource(R.string.cover_description, entry.title),
                placeholderText = entry.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.small),
            )
            PlatformBadgeRow(entry.platforms, onImage = true, modifier = Modifier.padding(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(entry.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            entry.platforms.forEach { p ->
                PlatformDot(platformColors(p).accent, size = 7.dp)
                Spacer(Modifier.width(3.dp))
            }
            Spacer(Modifier.width(2.dp))
            Text(entryPlaytimeText(entry), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Library list row: cover, title with the user's status, and where the hours come from
 * ("Steam 118 ч · PS5 24 ч"), so the merged library always shows each game's sources.
 */
@Composable
fun LibraryListRow(entry: LibraryEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameImage(
            urls = listOf(entry.coverUrl, entry.fallbackCoverUrl),
            contentDescription = null,
            placeholderText = entry.title,
            modifier = Modifier.size(width = 52.dp, height = 70.dp).clip(MaterialTheme.shapes.small),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.status != GameStatus.NONE) {
                    Spacer(Modifier.width(6.dp))
                    StatusPill(entry.status)
                }
            }
            SourcesLine(entry)
            val progress = entry.achievementProgress
            if (progress != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.weight(1f).height(4.dp).clip(MaterialTheme.shapes.extraSmall),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.achievements_progress, progress),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    formatRelativeDate(entry.lastPlayedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(entryPlaytimeText(entry), style = MaterialTheme.typography.titleLarge)
    }
}

/** "● Steam 118 ч  ● PS5 24 ч": dot in the platform color plus a text label, never color alone. */
@Composable
fun SourcesLine(entry: LibraryEntry, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        entry.platforms.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformDot(platformColors(p).accent, size = 7.dp)
                Spacer(Modifier.width(5.dp))
                Text(
                    "${sourceName(entry, p)} ${formatPlaytime(entry.playtimeOn(p))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Console name when the platform reports one ("PS5", "Switch"), else the platform. */
@Composable
fun sourceName(entry: LibraryEntry, platform: Platform): String {
    val device = entry.games.filter { it.id.platform == platform }.flatMap { it.devices }.sorted().lastOrNull()
    return if (platform != Platform.STEAM && device != null) device else platformName(platform)
}

@Composable
fun StatusPill(status: GameStatus, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        GameStatus.PLAYING -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        GameStatus.COMPLETED -> platformColors(Platform.PLAYSTATION).badgeBg to platformColors(Platform.PLAYSTATION).badgeText
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        statusName(status),
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = modifier.clip(MaterialTheme.shapes.extraSmall).background(bg).padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

@Composable
@ReadOnlyComposable
fun statusName(status: GameStatus): String = stringResource(
    when (status) {
        GameStatus.NONE -> R.string.status_NONE
        GameStatus.PLAYING -> R.string.status_PLAYING
        GameStatus.COMPLETED -> R.string.status_COMPLETED
        GameStatus.SHELVED -> R.string.status_SHELVED
        GameStatus.DROPPED -> R.string.status_DROPPED
    },
)

@Composable
fun NowPlayingCard(entry: LibraryEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameImage(
            urls = listOf(entry.coverUrl, entry.fallbackCoverUrl),
            contentDescription = null,
            placeholderText = entry.title,
            modifier = Modifier.size(width = 72.dp, height = 96.dp).clip(MaterialTheme.shapes.small),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.now_playing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(entryPlaytimeText(entry), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            entry.achievementProgress?.let { progress ->
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.extraSmall),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    stringResource(R.string.achievements_progress, progress),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PlatformBadgeRow(entry.platforms)
    }
}

@Composable
fun TopGameRow(rank: Int, game: TopGame, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val platform = game.entry.primary.id.platform
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(24.dp))
        GameImage(
            urls = listOf(game.entry.coverUrl, game.entry.fallbackCoverUrl),
            contentDescription = null,
            placeholderText = game.entry.title,
            modifier = Modifier.size(width = 40.dp, height = 52.dp).clip(MaterialTheme.shapes.extraSmall),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(game.entry.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(game.share.coerceIn(0.02f, 1f))
                        .height(6.dp)
                        .background(platformColors(platform).accent),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatPlaytime(game.playtime), style = MaterialTheme.typography.labelLarge)
            PlatformBadge(platform)
        }
    }
}

@Composable
fun ScreenshotTile(screenshot: Screenshot, onClick: () -> Unit, modifier: Modifier = Modifier, title: String? = null) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Image, onClick = onClick),
    ) {
        GameImage(
            urls = listOf(screenshot.thumbnailUrl, screenshot.fullUrl),
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
        )
        PlatformBadge(screenshot.platform, onImage = true, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
        if (screenshot.isVideo) {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = stringResource(R.string.video_capture),
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(36.dp),
            )
        }
    }
}

/** Playtime per platform for the split bar caption, e.g. "Steam 118 ч · Nintendo 24 ч". */
@Composable
fun platformSplitCaption(entry: LibraryEntry): String {
    // Composable calls can't go inside joinToString's lambda, so the parts are built first.
    val parts = mutableListOf<String>()
    for (p in entry.platforms) parts += "${platformName(p)} ${formatPlaytime(entry.playtimeOn(p))}"
    return parts.joinToString(" · ")
}

fun LibraryEntry.platformShares(): List<Pair<Platform, Float>> =
    platforms.map { it to playtimeOn(it).toMinutes().toFloat() }.filter { it.second > 0f }

fun Duration.toHoursFloat(): Float = toMinutes() / 60f

