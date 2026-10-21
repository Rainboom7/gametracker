package app.playlog.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.playlog.core.designsystem.component.SectionHeader
import app.playlog.core.designsystem.component.Skeleton
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.DealPick
import app.playlog.core.model.DealsState
import app.playlog.core.ui.DealCard

/** "On sale for you": Steam deals similar to what the user plays. Hidden while there is no taste yet. */
@Composable
fun DealsShelf(
    state: DealsState,
    onOpen: (DealPick) -> Unit,
    onRefresh: () -> Unit,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is DealsState.NoTaste) return
    val picks = when (state) {
        is DealsState.Ready -> state.picks
        is DealsState.Failed -> state.stale
        else -> emptyList()
    }
    Column(modifier) {
        Column(Modifier.padding(horizontal = Spacing.lg)) {
            SectionHeader(stringResource(R.string.deals_title)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state !is DealsState.Loading) {
                        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.deals_refresh)) }
                    }
                    TextButton(onClick = onOpenAll) { Text(stringResource(R.string.deals_all)) }
                }
            }
            Text(
                stringResource(
                    when {
                        state is DealsState.Loading -> R.string.deals_loading
                        state is DealsState.Failed -> R.string.deals_failed
                        picks.isEmpty() -> R.string.deals_empty
                        else -> R.string.deals_hint
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        when {
            picks.isNotEmpty() -> LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(picks, key = { it.key }) { pick -> DealCard(pick, onClick = { onOpen(pick) }) }
            }
            state is DealsState.Loading -> Row(Modifier.padding(horizontal = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                repeat(2) { Skeleton(Modifier.width(260.dp).height(190.dp)) }
            }
            state is DealsState.Failed -> TextButton(onClick = onRefresh, modifier = Modifier.padding(horizontal = Spacing.sm)) {
                Text(stringResource(R.string.deals_retry))
            }
        }
    }
}

