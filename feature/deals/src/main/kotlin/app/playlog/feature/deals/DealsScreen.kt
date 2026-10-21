package app.playlog.feature.deals

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
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
import app.playlog.core.model.WishlistItem
import app.playlog.core.ui.DealCard
import app.playlog.core.ui.DealDetailSheet
import app.playlog.core.ui.DealPrice
import app.playlog.core.ui.DiscountBadge
import app.playlog.core.ui.platformName
import java.util.Locale

@Composable
fun DealsRoute(onBack: () -> Unit, onOpenGiveaways: () -> Unit = {}, viewModel: DealsViewModel = hiltViewModel()) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val store by viewModel.store.collectAsStateWithLifecycle()
    val personal by viewModel.personal.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val wishlist by viewModel.wishlist.collectAsStateWithLifecycle()
    val wishlistAction by viewModel.wishlistAction.collectAsStateWithLifecycle()
    val openDeal by viewModel.openDeal.collectAsStateWithLifecycle()
    val lowest by viewModel.lowest.collectAsStateWithLifecycle()
    val wished = remember(wishlist) { wishlist.mapTo(HashSet()) { it.key } }

    // Price-drop alerts need the notification permission; ask the first time something is wishlisted.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val toggleWish: (DealPick) -> Unit = { pick ->
        if (wished.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        viewModel.toggleWish(pick)
    }
    val platform = (tab as? DealsTab.Store)?.platform

    DealsScreen(
        platforms = viewModel.platforms,
        tab = tab,
        store = store,
        personal = if (platform == Platform.STEAM && query.isBlank() && filter.activeCount == 0) personal else DealsState.NoTaste,
        filter = filter,
        query = query,
        search = search,
        wishlist = wishlist,
        wishlistAction = wishlistAction,
        wished = wished,
        countries = platform?.let(viewModel::countries).orEmpty(),
        actions = DealsActions(
            onBack = onBack,
            onOpenGiveaways = onOpenGiveaways,
            onSelect = viewModel::select,
            onOpen = viewModel::open,
            onOpenWish = viewModel::openWish,
            onToggleWish = toggleWish,
            onRemoveWish = viewModel::removeWish,
            onLoadMore = viewModel::loadMore,
            onRefresh = viewModel::refresh,
            onSetRegion = viewModel::setRegion,
            onFilter = viewModel::setFilter,
            onQuery = viewModel::setQuery,
            onSearchStore = viewModel::searchStore,
            onCheckPrices = viewModel::checkPrices,
            onImport = viewModel::importWishlists,
        ),
    )
    openDeal?.let { pick ->
        DealDetailSheet(
            pick,
            onDismiss = { viewModel.open(null) },
            wished = pick.key in wished,
            onToggleWish = { toggleWish(pick) },
            lowest = lowest,
        )
    }
}

/** Everything the screen can ask for, so the composable signature stays readable. */
class DealsActions(
    val onBack: () -> Unit,
    val onOpenGiveaways: () -> Unit,
    val onSelect: (DealsTab) -> Unit,
    val onOpen: (DealPick) -> Unit,
    val onOpenWish: (WishlistItem) -> Unit,
    val onToggleWish: (DealPick) -> Unit,
    val onRemoveWish: (WishlistItem) -> Unit,
    val onLoadMore: () -> Unit,
    val onRefresh: () -> Unit,
    val onSetRegion: (String?) -> Unit,
    val onFilter: (DealsFilter) -> Unit,
    val onQuery: (String) -> Unit,
    val onSearchStore: () -> Unit,
    val onCheckPrices: () -> Unit,
    val onImport: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealsScreen(
    platforms: List<Platform>,
    tab: DealsTab,
    store: StoreView,
    personal: DealsState,
    filter: DealsFilter,
    query: String,
    search: SearchState,
    wishlist: List<WishlistItem>,
    wishlistAction: WishlistAction,
    wished: Set<String>,
    countries: List<String>,
    actions: DealsActions,
) {
    var pickingRegion by rememberSaveable { mutableStateOf(false) }
    var editingFilters by rememberSaveable { mutableStateOf(false) }
    // The title bar slides away while scrolling down and returns on the first scroll up, so the grid gets the room.
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                scrollBehavior = scroll,
                title = { Text(stringResource(R.string.deals_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.deals_back))
                    }
                },
                actions = {
                    IconButton(onClick = actions.onOpenGiveaways) {
                        Icon(Icons.Default.Redeem, contentDescription = stringResource(R.string.deals_open_giveaways))
                    }
                    IconButton(onClick = if (tab is DealsTab.Wishlist) actions.onCheckPrices else actions.onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Tabs(platforms, tab, wishlist.size, actions.onSelect)
            when (tab) {
                // Only the store tabs stay pinned; search, region and filters scroll away with the grid.
                is DealsTab.Store -> StoreContent(store, personal, query, search, wished, actions, onPickRegion = { pickingRegion = true }) {
                    Column {
                        SearchField(query, actions.onQuery, actions.onSearchStore)
                        Controls(filter, store.hasTaste, regionOf(store.state), actions.onFilter, { editingFilters = true }, { pickingRegion = true })
                    }
                }
                DealsTab.Wishlist -> WishlistContent(wishlist, wishlistAction, actions)
            }
        }
    }
    if (pickingRegion) {
        RegionDialog(
            countries = countries,
            current = regionOf(store.state),
            onPick = { pickingRegion = false; actions.onSetRegion(it) },
            onDismiss = { pickingRegion = false },
        )
    }
    if (editingFilters) {
        FilterSheet(filter, store.genres, store.maxPrice, store.hasTaste, actions.onFilter, onDismiss = { editingFilters = false })
    }
}

@Composable
private fun Tabs(platforms: List<Platform>, tab: DealsTab, wishCount: Int, onSelect: (DealsTab) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        platforms.forEach { p ->
            PlatformFilterChip(platformName(p), selected = tab == DealsTab.Store(p), onClick = { onSelect(DealsTab.Store(p)) }, platform = p)
        }
        FilterChip(
            selected = tab == DealsTab.Wishlist,
            onClick = { onSelect(DealsTab.Wishlist) },
            shape = CircleShape,
            leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) },
            label = {
                Text(
                    if (wishCount > 0) stringResource(R.string.deals_wishlist_count, wishCount) else stringResource(R.string.deals_wishlist),
                    style = MaterialTheme.typography.labelMedium,
                )
            },
        )
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onSearchStore: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(stringResource(R.string.deals_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.deals_search_clear)) }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearchStore() }),
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xxs),
    )
}

@Composable
private fun Controls(
    filter: DealsFilter,
    hasTaste: Boolean,
    region: StoreRegion?,
    onFilter: (DealsFilter) -> Unit,
    onOpenFilters: () -> Unit,
    onPickRegion: () -> Unit,
) {
    var sorting by remember { mutableStateOf(false) }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (region != null) RegionChip(region, onPickRegion)
        FilterChip(
            selected = filter.activeCount > 0,
            onClick = onOpenFilters,
            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) },
            label = {
                Text(
                    if (filter.activeCount > 0) stringResource(R.string.deals_filters_count, filter.activeCount) else stringResource(R.string.deals_filters),
                )
            },
        )
        Box {
            FilterChip(
                selected = filter.sort != DealsSort.POPULAR,
                onClick = { sorting = true },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text(stringResource(sortLabel(filter.sort))) },
            )
            DropdownMenu(expanded = sorting, onDismissRequest = { sorting = false }) {
                DealsSort.entries.filter { it != DealsSort.MATCH || hasTaste }.forEach { sort ->
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(sortLabel(sort)), color = if (sort == filter.sort) MaterialTheme.colorScheme.primary else Color.Unspecified)
                        },
                        onClick = { sorting = false; onFilter(filter.copy(sort = sort)) },
                    )
                }
            }
        }
        FilterChip(
            selected = filter.minDiscount >= HalfOff,
            onClick = { onFilter(filter.copy(minDiscount = if (filter.minDiscount >= HalfOff) 0 else HalfOff)) },
            label = { Text(stringResource(R.string.deals_quick_half)) },
        )
        if (hasTaste) {
            FilterChip(
                selected = filter.similarOnly,
                onClick = { onFilter(filter.copy(similarOnly = !filter.similarOnly)) },
                label = { Text(stringResource(R.string.deals_filter_similar)) },
            )
        }
    }
}

private fun regionOf(state: StoreDealsState): StoreRegion? = when (state) {
    is StoreDealsState.Ready -> state.region
    is StoreDealsState.Unsupported -> state.region
    is StoreDealsState.Failed -> state.region
    StoreDealsState.Loading -> null
}

/** The store's country as the first chip; the source (account, device, manual) goes in the dialog. */
@Composable
private fun RegionChip(region: StoreRegion, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp)) },
        label = { Text(countryName(region.country)) },
    )
}

@Composable
private fun StoreContent(
    store: StoreView,
    personal: DealsState,
    query: String,
    search: SearchState,
    wished: Set<String>,
    actions: DealsActions,
    onPickRegion: () -> Unit,
    header: @Composable () -> Unit,
) {
    val grid = rememberLazyGridState()
    val ready = store.state as? StoreDealsState.Ready
    val nearEnd by remember {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= grid.layoutInfo.totalItemsCount - LoadAhead
        }
    }
    // Filters can hide most of a page; keep loading while the visible list is short, up to a cap.
    LaunchedEffect(nearEnd, ready?.deals?.size, store.visible.size) {
        if (ready != null && wantsMore(ready, nearEnd, store.visible.size)) actions.onLoadMore()
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        state = grid,
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.xs, bottom = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "controls") { header() }
        if (query.isNotBlank()) searchSection(query, search, wished, actions)
        val picks = (personal as? DealsState.Ready)?.picks ?: (personal as? DealsState.Failed)?.stale.orEmpty()
        if (picks.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "for-you") {
                Column {
                    SectionHeader(stringResource(R.string.deals_for_you))
                    Spacer(Modifier.height(Spacing.xs))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(picks, key = { it.key }) { DealCard(it, onClick = { actions.onOpen(it) }) }
                    }
                    Spacer(Modifier.height(Spacing.md))
                    SectionHeader(stringResource(R.string.deals_all_store))
                }
            }
        }
        when (val state = store.state) {
            StoreDealsState.Loading -> items(SkeletonCount) { Skeleton(Modifier.fillMaxWidth().aspectRatio(SkeletonAspect)) }
            is StoreDealsState.Unsupported -> fullWidth {
                Message(
                    stringResource(R.string.deals_unsupported, countryName(state.region.country)),
                    stringResource(R.string.deals_change_region),
                    onPickRegion,
                )
            }
            is StoreDealsState.Failed -> fullWidth {
                Message(stringResource(R.string.deals_failed_store, state.message.orEmpty()), stringResource(R.string.deals_retry_store), actions.onRefresh)
            }
            is StoreDealsState.Ready -> {
                when {
                    state.deals.isEmpty() -> fullWidth { Message(stringResource(R.string.deals_empty_store), null, {}) }
                    store.visible.isEmpty() && !state.loadingMore -> fullWidth { Message(stringResource(R.string.deals_nothing_filtered), null, {}) }
                }
                items(store.visible, key = { it.key }) { pick ->
                    DealTile(pick, wished = pick.key in wished, onClick = { actions.onOpen(pick) }, onWish = { actions.onToggleWish(pick) })
                }
                if (state.loadingMore) fullWidth { Progress() }
            }
        }
    }
}

private fun wantsMore(ready: StoreDealsState.Ready, nearEnd: Boolean, visible: Int): Boolean {
    if (!ready.hasMore || ready.loadingMore) return false
    return nearEnd || (visible < MinVisible && ready.deals.size < MaxAutoLoaded)
}

private fun LazyGridScope.searchSection(query: String, search: SearchState, wished: Set<String>, actions: DealsActions) {
    when (search) {
        SearchState.Idle -> fullWidth {
            OutlinedButton(onClick = actions.onSearchStore, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.deals_search_store, query.trim()), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        SearchState.Loading -> fullWidth { Progress() }
        is SearchState.Failed -> fullWidth {
            Message(stringResource(R.string.deals_search_failed, search.message.orEmpty()), stringResource(R.string.deals_retry_store), actions.onSearchStore)
        }
        is SearchState.Ready -> {
            fullWidth {
                SectionHeader(
                    if (search.results.isEmpty()) stringResource(R.string.deals_search_nothing) else stringResource(R.string.deals_search_results),
                )
            }
            items(search.results, key = { "search:" + it.key }) { pick ->
                DealTile(pick, wished = pick.key in wished, onClick = { actions.onOpen(pick) }, onWish = { actions.onToggleWish(pick) })
            }
            fullWidth { SectionHeader(stringResource(R.string.deals_all_store)) }
        }
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) = item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun Progress() {
    Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun Message(text: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

/** Compact grid cell: art, discount, wishlist heart, title, price. */
@Composable
private fun DealTile(pick: DealPick, wished: Boolean, onClick: () -> Unit, onWish: () -> Unit) {
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
            if (pick.isOnSale) DiscountBadge(pick.discountPercent, Modifier.align(Alignment.TopStart).padding(6.dp))
            PlatformBadge(pick.platform, onImage = true, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            WishButton(wished, onWish, Modifier.align(Alignment.BottomEnd))
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
internal fun WishButton(wished: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.padding(2.dp).size(36.dp)) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.scrim.copy(alpha = ScrimAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (wished) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = stringResource(if (wished) R.string.deals_wish_remove else R.string.deals_wish_add),
                tint = if (wished) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(18.dp),
            )
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
                current?.let {
                    Text(
                        stringResource(R.string.deals_region, countryName(it.country)) + " · " + stringResource(regionSourceLabel(it.source)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                }
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

@StringRes
internal fun sortLabel(sort: DealsSort): Int = when (sort) {
    DealsSort.POPULAR -> R.string.deals_sort_POPULAR
    DealsSort.DISCOUNT -> R.string.deals_sort_DISCOUNT
    DealsSort.PRICE_LOW -> R.string.deals_sort_PRICE_LOW
    DealsSort.PRICE_HIGH -> R.string.deals_sort_PRICE_HIGH
    DealsSort.RATING -> R.string.deals_sort_RATING
    DealsSort.MATCH -> R.string.deals_sort_MATCH
    DealsSort.ENDING -> R.string.deals_sort_ENDING
}

private const val LoadAhead = 6
private const val MinVisible = 8
private const val MaxAutoLoaded = 400
private const val HalfOff = 50
private const val SkeletonCount = 6
private const val SkeletonAspect = 1.1f
private const val TileAspect = 460f / 215f
private const val ScrimAlpha = 0.45f
