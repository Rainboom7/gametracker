package app.playlog.feature.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.component.SectionHeader
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.Badge

/**
 * A row of medals for Home or Discover: recent unlocks first, then earned, then the closest to
 * unlocking. Hidden while nothing is earned or in progress, so a fresh install shows no empty shelf.
 * [contentPadding] is the row's horizontal inset, so medals scroll edge to edge.
 */
@Composable
fun BadgesStrip(
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.lg),
    viewModel: BadgesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ready = state as? BadgesUiState.Ready ?: return
    val shown = ready.badges.filter { it.level > 0 || it.milestone.progressToNext > 0f }.take(MaxInStrip)
    if (shown.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(
            title = if (ready.recent.isEmpty()) stringResource(R.string.badges_title) else stringResource(R.string.badges_title_new, ready.recent.size),
            modifier = Modifier.padding(contentPadding),
        ) {
            TextButton(onClick = onOpenAll) { Text(stringResource(R.string.badges_count, ready.unlocked, ready.badges.size)) }
        }
        LazyRow(contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(shown, key = { it.kind }) { badge -> StripItem(badge, onOpenAll) }
        }
    }
}

@Composable
private fun StripItem(badge: Badge, onClick: () -> Unit) {
    Column(
        Modifier.width(ItemWidth).clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        BadgeMedal(badge)
        Text(
            stringResource(badgeTitle(badge.kind)),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            if (badge.isRecent) stringResource(R.string.badges_new) else stringResource(R.string.badges_level_short, badge.level, badge.milestone.maxLevel),
            style = MaterialTheme.typography.labelSmall,
            color = if (badge.isRecent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


private const val MaxInStrip = 8
private val ItemWidth = 76.dp
