package app.playlog.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.playlog.core.designsystem.component.GameImage
import app.playlog.core.designsystem.component.SectionHeader
import app.playlog.core.designsystem.component.Skeleton
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.DealPick
import app.playlog.core.model.DealsState
import app.playlog.core.ui.DealCard
import app.playlog.core.ui.DealPrice
import app.playlog.core.ui.DiscountBadge
import app.playlog.core.ui.dealReason

/** "On sale for you": Steam deals similar to what the user plays. Hidden while there is no taste yet. */
@Composable
fun DealsShelf(state: DealsState, onOpen: (DealPick) -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    if (state is DealsState.NoTaste) return
    val picks = when (state) {
        is DealsState.Ready -> state.picks
        is DealsState.Failed -> state.stale
        else -> emptyList()
    }
    Column(modifier) {
        Column(Modifier.padding(horizontal = Spacing.lg)) {
            SectionHeader(stringResource(R.string.deals_title)) {
                if (state !is DealsState.Loading) {
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.deals_refresh)) }
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
                items(picks, key = { it.steamAppId }) { pick -> DealCard(pick, onClick = { onOpen(pick) }) }
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

/** Screenshots, price, description and a link to the store page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealDetailSheet(pick: DealPick, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Screenshots(pick)
            Column(Modifier.padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pick.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    DiscountBadge(pick.discountPercent)
                }
                DealPrice(pick)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocalOffer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(dealReason(pick), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (pick.match > 0f) {
                    Text(
                        stringResource(R.string.deals_match, (pick.match * Percent).toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (pick.sharedTags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        pick.sharedTags.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
                    }
                }
                pick.shortDescription?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Button(onClick = { uriHandler.openUri(pick.storeUrl) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.deals_open_store))
                }
            }
        }
    }
}

@Composable
private fun Screenshots(pick: DealPick) {
    val images = pick.screenshots.ifEmpty { listOf(pick.headerUrl) }
    val pager = rememberPagerState { images.size }
    Box {
        HorizontalPager(pager, contentPadding = PaddingValues(horizontal = Spacing.lg), pageSpacing = Spacing.xs) { page ->
            GameImage(
                urls = listOf(images[page], pick.headerUrl),
                contentDescription = stringResource(R.string.deals_screenshot, page + 1, pick.title),
                modifier = Modifier.fillMaxWidth().aspectRatio(ScreenshotAspect).clip(MaterialTheme.shapes.medium),
            )
        }
        if (images.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(images.size) { i ->
                    Box(
                        Modifier
                            .size(if (i == pager.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = DotAlpha),
                            ),
                    )
                }
            }
        }
    }
}

private const val ScreenshotAspect = 16f / 9f
private const val Percent = 100
private const val DotAlpha = 0.5f
