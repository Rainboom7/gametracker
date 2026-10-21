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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.component.GameImage
import app.playlog.core.designsystem.component.PlatformBadge
import app.playlog.core.designsystem.component.PlatformFilterChip
import app.playlog.core.designsystem.component.SectionHeader
import app.playlog.core.designsystem.component.Skeleton
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.DealPick
import app.playlog.core.model.DealsState
import app.playlog.core.model.Platform
import app.playlog.core.model.RegionSource
import app.playlog.core.model.StoreDealsState
import app.playlog.core.model.StoreRegion
import app.playlog.core.ui.DealCard
import app.playlog.core.ui.DealDetailSheet
import app.playlog.core.ui.DealPrice
import app.playlog.core.ui.DiscountBadge
import app.playlog.core.ui.platformName
import java.util.Locale

@Composable
fun DealsRoute(onBack: () -> Unit, viewModel: DealsViewModel = hiltViewModel()) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val store by viewModel.store.collectAsStateWithLifecycle()
    val personal by viewModel.personal.collectAsStateWithLifecycle()
    val openDeal by viewModel.openDeal.collectAsStateWithLifecycle()
    DealsScreen(
        platforms = viewModel.platforms,
        selected = selected,
        store = store,
        personal = if (selected == Platform.STEAM) personal else DealsState.NoTaste,
        countries = viewModel.countries(selected),
        onBack = onBack,
        onSelect = viewModel::select,
        onOpen = viewModel::open,
        onLoadMore = viewModel::loadMore,
        onRefresh = viewModel::refresh,
        onSetRegion = viewModel::setRegion,
    )
    openDeal?.let { DealDetailSheet(it, onDismiss = { viewModel.open(null) }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealsScreen(
    platforms: List<Platform>,
    selected: Platform,
    store: StoreDealsState,
    personal: DealsState,
    countries: List<String>,
    onBack: () -> Unit,
    onSelect: (Platform) -> Unit,
    onOpen: (DealPick) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onSetRegion: (String?) -> Unit,
) {
    var pickingRegion by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.deals_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.deals_back)) }
                },
                actions = { IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                platforms.forEach { p -> PlatformFilterChip(platformName(p), selected = p == selected, onClick = { onSelect(p) }, platform = p) }
            }
            regionOf(store)?.let { region -> RegionRow(region, onClick = { pickingRegion = true }) }
            StoreContent(store, personal, onOpen, onLoadMore, onRefresh, onPickRegion = { pickingRegion = true })
        }
    }
    if (pickingRegion) {
        RegionDialog(
            countries = countries,
            current = regionOf(store),
            onPick = { pickingRegion = false; onSetRegion(it) },
            onDismiss = { pickingRegion = false },
        )
    }
}

private fun regionOf(state: StoreDealsState): StoreRegion? = when (state) {
    is StoreDealsState.Ready -> state.region
    is StoreDealsState.Unsupported -> state.region
    is StoreDealsState.Failed -> state.region
    StoreDealsState.Loading -> null
}

@Composable
private fun RegionRow(region: StoreRegion, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(
            stringResource(R.string.deals_region, countryName(region.country)) + " · " + stringResource(regionSourceLabel(region.source)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(stringResource(R.string.deals_change_region), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun StoreContent(
    store: StoreDealsState,
    personal: DealsState,
    onOpen: (DealPick) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onPickRegion: () -> Unit,
) {
    val grid = rememberLazyGridState()
    val ready = store as? StoreDealsState.Ready
    val nearEnd by remember {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= grid.layoutInfo.totalItemsCount - LoadAhead
        }
    }
    LaunchedEffect(nearEnd, ready?.deals?.size) { if (nearEnd && ready?.hasMore == true) onLoadMore() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        state = grid,
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxSize(),
    ) {
        val picks = (personal as? DealsState.Ready)?.picks ?: (personal as? DealsState.Failed)?.stale.orEmpty()
        if (picks.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "for-you") {
                Column {
                    SectionHeader(stringResource(R.string.deals_for_you))
                    Spacer(Modifier.height(Spacing.xs))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(picks, key = { it.key }) { DealCard(it, onClick = { onOpen(it) }) }
                    }
                    Spacer(Modifier.height(Spacing.md))
                    SectionHeader(stringResource(R.string.deals_all_store))
                }
            }
        }
        when (store) {
            StoreDealsState.Loading -> items(SkeletonCount) { Skeleton(Modifier.fillMaxWidth().aspectRatio(SkeletonAspect)) }
            is StoreDealsState.Unsupported -> item(span = { GridItemSpan(maxLineSpan) }) {
                Message(
                    stringResource(R.string.deals_unsupported, countryName(store.region.country)),
                    stringResource(R.string.deals_change_region),
                    onPickRegion,
                )
            }
            is StoreDealsState.Failed -> item(span = { GridItemSpan(maxLineSpan) }) {
                Message(stringResource(R.string.deals_failed_store, store.message.orEmpty()), stringResource(R.string.deals_retry_store), onRefresh)
            }
            is StoreDealsState.Ready -> {
                if (store.deals.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Message(stringResource(R.string.deals_empty_store), null, {}) }
                }
                items(store.deals, key = { it.key }) { DealTile(it, onClick = { onOpen(it) }) }
                if (store.loadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

/** Compact grid cell: art, discount, title, price. */
@Composable
private fun DealTile(pick: DealPick, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box {
            GameImage(
                urls = listOf(pick.headerUrl, pick.screenshots.firstOrNull()),
                contentDescription = pick.title,
                placeholderText = pick.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(TileAspect),
            )
            DiscountBadge(pick.discountPercent, Modifier.align(Alignment.TopStart).padding(6.dp))
            PlatformBadge(pick.platform, onImage = true, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp).heightIn(min = 64.dp)) {
            Text(pick.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f, fill = false))
            Spacer(Modifier.height(4.dp))
            DealPrice(pick)
        }
    }
}

@Composable
private fun RegionDialog(countries: List<String>, current: StoreRegion?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val sorted = remember(countries) { countries.sortedBy { countryName(it) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.deals_region_pick)) },
        text = {
            Column {
                Text(stringResource(R.string.deals_region_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.sm))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item { RegionOption(stringResource(R.string.deals_region_auto), current?.source != RegionSource.MANUAL) { onPick(null) } }
                    items(sorted) { code ->
                        RegionOption(countryName(code), current?.source == RegionSource.MANUAL && current.country == code) { onPick(code) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.deals_cancel)) } },
    )
}

@Composable
private fun RegionOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick).padding(vertical = 10.dp),
    )
}

private fun countryName(code: String): String =
    Locale("", code).getDisplayCountry(Locale.getDefault()).ifBlank { code }

@StringRes
private fun regionSourceLabel(source: RegionSource): Int = when (source) {
    RegionSource.ACCOUNT -> R.string.deals_region_ACCOUNT
    RegionSource.MANUAL -> R.string.deals_region_MANUAL
    RegionSource.DEVICE -> R.string.deals_region_DEVICE
}

private const val LoadAhead = 6
private const val SkeletonCount = 6
private const val SkeletonAspect = 1.1f
private const val TileAspect = 460f / 215f
