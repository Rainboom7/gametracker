package app.playlog.feature.deals

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.component.GameImage
import app.playlog.core.designsystem.component.SectionHeader
import app.playlog.core.designsystem.component.Skeleton
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.Giveaway
import app.playlog.core.model.GiveawayStore
import app.playlog.core.model.GiveawaysState
import app.playlog.core.model.items
import app.playlog.core.ui.formatShortDate

@Composable
fun GiveawaysRoute(onBack: () -> Unit, viewModel: GiveawaysViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val store by viewModel.store.collectAsStateWithLifecycle()
    GiveawaysScreen(state, store, onBack, viewModel::selectStore, viewModel::refresh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiveawaysScreen(
    state: GiveawaysState,
    store: GiveawayStore?,
    onBack: () -> Unit,
    onSelectStore: (GiveawayStore?) -> Unit,
    onRefresh: () -> Unit,
) {
    val uri = LocalUriHandler.current
    val all = state.items
    val shown = all.filter { store == null || it.store == store }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.giveaways_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.deals_back)) }
                },
                actions = { IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            span { Hint(stringResource(R.string.giveaways_hint)) }
            span { StoreChips(all.map { it.store }.distinct(), store, onSelectStore) }
            when {
                state is GiveawaysState.Loading -> items(SkeletonCount) { Skeleton(Modifier.fillMaxWidth().aspectRatio(SkeletonAspect)) }
                state is GiveawaysState.Failed && all.isEmpty() ->
                    span { Message(stringResource(R.string.giveaways_failed, state.message.orEmpty()), stringResource(R.string.deals_retry_store), onRefresh) }
                shown.isEmpty() -> span { Message(stringResource(R.string.giveaways_empty), null, {}) }
                else -> {
                    section(R.string.giveaways_now, shown.filterNot { it.upcoming }) { uri.openUri(it.url) }
                    section(R.string.giveaways_upcoming, shown.filter { it.upcoming }) { uri.openUri(it.url) }
                }
            }
        }
    }
}

private fun LazyGridScope.span(content: @Composable () -> Unit) = item(span = { GridItemSpan(maxLineSpan) }) { content() }

private fun LazyGridScope.section(@StringRes title: Int, items: List<Giveaway>, onOpen: (Giveaway) -> Unit) {
    if (items.isEmpty()) return
    span { SectionHeader(stringResource(title)) }
    items(items, key = { it.id }) { GiveawayTile(it, onClick = { onOpen(it) }) }
}

@Composable
private fun StoreChips(stores: List<GiveawayStore>, selected: GiveawayStore?, onSelect: (GiveawayStore?) -> Unit) {
    if (stores.size < 2) return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            shape = CircleShape,
            label = { Text(stringResource(R.string.giveaways_store_all)) },
        )
        stores.sortedBy { it.ordinal }.forEach { s ->
            FilterChip(selected = selected == s, onClick = { onSelect(s) }, shape = CircleShape, label = { Text(stringResource(storeLabel(s))) })
        }
    }
}

/** Key art, store, when it ends, the usual price struck through. */
@Composable
internal fun GiveawayTile(giveaway: Giveaway, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box {
            GameImage(
                urls = listOf(giveaway.imageUrl),
                contentDescription = giveaway.title,
                placeholderText = giveaway.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(TileAspect),
            )
            Pill(stringResource(storeLabel(giveaway.store)), Modifier.align(Alignment.TopStart).padding(6.dp))
            if (giveaway.owned) Pill(stringResource(R.string.giveaways_owned), Modifier.align(Alignment.TopEnd).padding(6.dp), accent = false)
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(giveaway.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.giveaways_free), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                giveaway.worth?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough,
                    )
                }
            }
            when {
                giveaway.upcoming && giveaway.startsAt != null ->
                    Hint(stringResource(R.string.giveaways_from, formatShortDate(checkNotNull(giveaway.startsAt))))
                giveaway.endsAt != null ->
                    Hint(stringResource(R.string.giveaways_until, formatShortDate(checkNotNull(giveaway.endsAt))))
            }
        }
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier = Modifier, accent: Boolean = true) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (accent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Drop-in row for Home or Discover: live giveaways, hidden while there are none.
 * [onOpenAll] opens the full screen ([navigateToGiveaways]).
 */
@Composable
fun GiveawaysHomeCard(onOpenAll: () -> Unit, modifier: Modifier = Modifier, viewModel: GiveawaysViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val live = state.items.filter { !it.upcoming && !it.owned }
    if (live.isEmpty()) return
    val uri = LocalUriHandler.current
    Column(modifier) {
        Box(Modifier.padding(horizontal = Spacing.lg)) {
            SectionHeader(stringResource(R.string.giveaways_home_title)) {
                TextButton(onClick = onOpenAll) { Text(stringResource(R.string.giveaways_all)) }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(live.take(HomeLimit), key = { it.id }) { g -> GiveawayTile(g, onClick = { uri.openUri(g.url) }, modifier = Modifier.width(HomeTileWidth)) }
        }
    }
}

@StringRes
private fun storeLabel(store: GiveawayStore): Int = when (store) {
    GiveawayStore.EPIC -> R.string.giveaway_store_EPIC
    GiveawayStore.STEAM -> R.string.giveaway_store_STEAM
    GiveawayStore.GOG -> R.string.giveaway_store_GOG
    GiveawayStore.PLAYSTATION -> R.string.giveaway_store_PLAYSTATION
    GiveawayStore.XBOX -> R.string.giveaway_store_XBOX
    GiveawayStore.NINTENDO -> R.string.giveaway_store_NINTENDO
    GiveawayStore.ITCH -> R.string.giveaway_store_ITCH
    GiveawayStore.OTHER -> R.string.giveaway_store_OTHER
}

private const val SkeletonCount = 4
private const val SkeletonAspect = 1.1f
private const val TileAspect = 16f / 9f
private const val HomeLimit = 10
private val HomeTileWidth = 220.dp
