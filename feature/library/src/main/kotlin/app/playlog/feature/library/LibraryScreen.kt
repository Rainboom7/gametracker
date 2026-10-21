package app.playlog.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.component.PlatformDot
import app.playlog.core.designsystem.component.PrimaryButton
import app.playlog.core.designsystem.component.Skeleton
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.designsystem.theme.platformColors
import app.playlog.core.model.GameId
import app.playlog.core.model.GameStatus
import app.playlog.core.model.Platform
import app.playlog.core.ui.GameCoverCard
import app.playlog.core.ui.LibraryListRow
import app.playlog.core.ui.NowPlayingCard
import app.playlog.core.ui.formatPlaytime
import app.playlog.core.ui.platformName
import app.playlog.core.ui.statusName

@Composable
fun LibraryRoute(
    onOpenGame: (GameId) -> Unit,
    onConnect: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(
        state = state,
        onOpenGame = onOpenGame,
        onConnect = onConnect,
        onQueryChange = viewModel::onQueryChange,
        onPlatformSelected = viewModel::onPlatformSelected,
        onSortSelected = viewModel::onSortSelected,
        onRefresh = viewModel::refresh,
        onFiltersChange = viewModel::onFiltersChange,
        onLayoutChange = viewModel::onLayoutChange,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onOpenGame: (GameId) -> Unit,
    onConnect: () -> Unit,
    onQueryChange: (String) -> Unit,
    onPlatformSelected: (Platform?) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onFiltersChange: (LibraryFilters) -> Unit = {},
    onLayoutChange: (LibraryLayout) -> Unit = {},
) {
    when (state) {
        LibraryUiState.Loading -> LibrarySkeleton(modifier)
        is LibraryUiState.Empty -> LibraryEmpty(state, onConnect, modifier)
        is LibraryUiState.Content -> {
            var showSort by rememberSaveable { mutableStateOf(false) }
            val controls = state.controls
            val columns = if (controls.layout == LibraryLayout.GRID) 3 else 1
            PullToRefreshBox(isRefreshing = state.isSyncing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(if (columns == 1) 0.dp else Spacing.sm),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    fullWidth { LibraryHeader(state, onLayoutChange) }
                    if (state.platformSummaries.size > 1) {
                        fullWidth {
                            SourceTiles(state.platformSummaries, controls.platform, onPlatformSelected, Modifier.padding(top = Spacing.sm))
                        }
                    }
                    fullWidth { SearchField(controls.query, onQueryChange, Modifier.padding(top = Spacing.sm)) }
                    fullWidth {
                        FilterRow(
                            controls = controls,
                            onSortClick = { showSort = true },
                            onFiltersChange = onFiltersChange,
                            modifier = Modifier.padding(vertical = Spacing.sm),
                        )
                    }
                    state.nowPlaying?.let { entry ->
                        fullWidth {
                            NowPlayingCard(entry, onClick = { onOpenGame(entry.primary.id) }, modifier = Modifier.padding(bottom = Spacing.sm))
                        }
                    }
                    if (state.entries.isEmpty()) {
                        fullWidth {
                            Text(
                                stringResource(R.string.library_no_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Spacing.xl),
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        fullWidth { ResultsCaption(state.entries.size, state.totalCount) }
                    }
                    items(state.entries, key = { it.key }) { entry ->
                        if (columns == 1) {
                            LibraryListRow(entry, onClick = { onOpenGame(entry.primary.id) })
                        } else {
                            GameCoverCard(entry, onClick = { onOpenGame(entry.primary.id) })
                        }
                    }
                }
            }
            if (showSort) {
                ModalBottomSheet(onDismissRequest = { showSort = false }) {
                    SortSheet(controls.sort) {
                        onSortSelected(it)
                        showSort = false
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun LibraryHeader(state: LibraryUiState.Content, onLayoutChange: (LibraryLayout) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineLarge)
            Text(
                stringResource(R.string.library_subtitle, state.totalCount, formatPlaytime(state.totalPlaytime)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LayoutToggle(state.controls.layout, onLayoutChange)
    }
}

@Composable
private fun LayoutToggle(layout: LibraryLayout, onChange: (LibraryLayout) -> Unit) {
    Row(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(3.dp),
    ) {
        LayoutOption(Icons.AutoMirrored.Filled.ViewList, stringResource(R.string.library_layout_list), layout == LibraryLayout.LIST) {
            onChange(LibraryLayout.LIST)
        }
        LayoutOption(Icons.Default.GridView, stringResource(R.string.library_layout_grid), layout == LibraryLayout.GRID) {
            onChange(LibraryLayout.GRID)
        }
    }
}

@Composable
private fun LayoutOption(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** One tile per linked platform: games and hours there. Tapping filters the library; tapping again clears it. */
@Composable
private fun SourceTiles(
    summaries: List<PlatformSummary>,
    selected: Platform?,
    onSelect: (Platform?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        summaries.forEach { s ->
            val isSelected = s.platform == selected
            val accent = platformColors(s.platform).accent
            Column(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(if (isSelected) null else s.platform) })
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlatformDot(accent)
                    Spacer(Modifier.width(6.dp))
                    Text(platformName(s.platform), style = MaterialTheme.typography.labelMedium, color = accent, maxLines = 1)
                }
                Text(s.games.toString(), style = MaterialTheme.typography.titleLarge)
                Text(formatPlaytime(s.playtime), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        placeholder = { Text(stringResource(R.string.library_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.library_search_clear))
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

/** Sort (opens the sheet), status menu, favorites and multi-platform toggles. */
@Composable
private fun FilterRow(
    controls: LibraryControls,
    onSortClick: () -> Unit,
    onFiltersChange: (LibraryFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = controls.filters
    var statusMenu by remember { mutableStateOf(false) }
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LibraryChip(
            label = sortLabel(controls.sort),
            selected = true,
            onClick = onSortClick,
            leadingIcon = Icons.AutoMirrored.Filled.Sort,
            contentDescription = stringResource(R.string.library_sort_current, sortLabel(controls.sort)),
        )
        Box {
            LibraryChip(
                label = filters.status?.let { statusName(it) } ?: stringResource(R.string.library_filter_status),
                selected = filters.status != null,
                onClick = { statusMenu = true },
                trailingIcon = Icons.Default.ArrowDropDown,
            )
            DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_filter_status_any)) },
                    onClick = {
                        statusMenu = false
                        onFiltersChange(filters.copy(status = null))
                    },
                )
                GameStatus.entries.forEach { status ->
                    DropdownMenuItem(
                        text = { Text(statusName(status)) },
                        trailingIcon = if (status == filters.status) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            statusMenu = false
                            onFiltersChange(filters.copy(status = status))
                        },
                    )
                }
            }
        }
        LibraryChip(
            label = stringResource(R.string.library_filter_favorites),
            selected = filters.favoritesOnly,
            onClick = { onFiltersChange(filters.copy(favoritesOnly = !filters.favoritesOnly)) },
        )
        LibraryChip(
            label = stringResource(R.string.library_filter_multi),
            selected = filters.multiPlatformOnly,
            onClick = { onFiltersChange(filters.copy(multiPlatformOnly = !filters.multiPlatformOnly)) },
        )
    }
}

@Composable
private fun LibraryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    contentDescription: String? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(16.dp)) } },
        trailingIcon = trailingIcon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.onSurface,
            selectedLabelColor = MaterialTheme.colorScheme.background,
            selectedLeadingIconColor = MaterialTheme.colorScheme.background,
            selectedTrailingIconColor = MaterialTheme.colorScheme.background,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
        ),
        modifier = Modifier
            .heightIn(min = 40.dp)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
    )
}

@Composable
private fun ResultsCaption(shown: Int, total: Int) {
    if (shown == total) return
    Text(
        stringResource(R.string.library_results, shown, total),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = Spacing.xs),
    )
}

@Composable
private fun sortLabel(sort: LibrarySort): String = stringResource(
    when (sort) {
        LibrarySort.LAST_PLAYED -> R.string.library_sort_last_played
        LibrarySort.PLAYTIME -> R.string.library_sort_playtime
        LibrarySort.TITLE -> R.string.library_sort_title
        LibrarySort.FIRST_PLAYED -> R.string.library_sort_first_played
        LibrarySort.ACHIEVEMENTS -> R.string.library_sort_achievements
        LibrarySort.PLATFORM_COUNT -> R.string.library_sort_platforms
    },
)

@Composable
private fun SortSheet(current: LibrarySort, onSelect: (LibrarySort) -> Unit) {
    Column(Modifier.padding(bottom = Spacing.xl)) {
        Text(
            stringResource(R.string.library_sort),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        LibrarySort.entries.forEach { sort ->
            ListItem(
                headlineContent = { Text(sortLabel(sort)) },
                trailingContent = { if (sort == current) Icon(Icons.Default.Check, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().selectable(selected = sort == current, role = Role.RadioButton) { onSelect(sort) },
            )
        }
    }
}

@Composable
private fun LibrarySkeleton(modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GridColumns),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false,
    ) {
        fullWidth { Skeleton(Modifier.fillMaxWidth().height(64.dp)) }
        fullWidth { Skeleton(Modifier.fillMaxWidth().height(48.dp)) }
        items(List(GridColumns * SkeletonRows) { it }) { Skeleton(Modifier.fillMaxWidth().aspectRatio(CoverAspect)) }
    }
}

@Composable
private fun LibraryEmpty(state: LibraryUiState.Empty, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.library_empty_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(if (state.hasAccounts) R.string.library_empty_syncing else R.string.library_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!state.hasAccounts) {
                Spacer(Modifier.height(Spacing.xl))
                PrimaryButton(stringResource(R.string.library_connect), onClick = onConnect)
            }
        }
    }
}

private const val GridColumns = 3
private const val SkeletonRows = 3
private const val CoverAspect = 3f / 4f
