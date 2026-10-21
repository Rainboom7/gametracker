package app.playlog.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.playlog.core.designsystem.theme.platformColors
import app.playlog.core.model.GameStatus
import app.playlog.core.model.Platform

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
