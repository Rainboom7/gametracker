package app.playlog.feature.library

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.component.PlatformFilterChip
import app.playlog.core.designsystem.component.PlaylogIconButton
import app.playlog.core.designsystem.component.PrimaryButton
import app.playlog.core.designsystem.component.Skeleton
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.GameId
import app.playlog.core.model.Platform
import app.playlog.core.ui.GameCoverCard
import app.playlog.core.ui.NowPlayingCard
import app.playlog.core.ui.formatPlaytime
import app.playlog.core.ui.platformName

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
) {
    when (state) {
        LibraryUiState.Loading -> LibrarySkeleton(modifier)
        is LibraryUiState.Empty -> LibraryEmpty(state, onConnect, modifier)
        is LibraryUiState.Content -> {
            var showSort by rememberSaveable { mutableStateOf(false) }
            PullToRefreshBox(isRefreshing = state.isSyncing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    fullWidth { LibraryHeader(state, onSortClick = { showSort = true }) }
                    fullWidth { SearchField(state.controls.query, onQueryChange) }
                    fullWidth { PlatformChips(state.availablePlatforms, state.controls.platform, onPlatformSelected) }
                    state.nowPlaying?.let { entry ->
                        fullWidth { NowPlayingCard(entry, onClick = { onOpenGame(entry.primary.id) }) }
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
                    }
                    items(state.entries, key = { it.key }) { entry ->
                        GameCoverCard(entry, onClick = { onOpenGame(entry.primary.id) })
                    }
                }
            }
            if (showSort) {
                ModalBottomSheet(onDismissRequest = { showSort = false }) {
                    SortSheet(state.controls.sort) {
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
private fun LibraryHeader(state: LibraryUiState.Content, onSortClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineLarge)
            Text(
                stringResource(R.string.library_subtitle, state.totalCount, formatPlaytime(state.totalPlaytime)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PlaylogIconButton(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.library_sort), onSortClick)
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        placeholder = { Text(stringResource(R.string.library_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

@Composable
private fun PlatformChips(available: Set<Platform>, selected: Platform?, onSelect: (Platform?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        item { PlatformFilterChip(platformName(null), selected == null, onClick = { onSelect(null) }) }
        items(Platform.entries.filter { it in available }) { p ->
            PlatformFilterChip(platformName(p), selected == p, onClick = { onSelect(p) }, platform = p)
        }
    }
}

@Composable
private fun SortSheet(current: LibrarySort, onSelect: (LibrarySort) -> Unit) {
    Column(Modifier.padding(bottom = Spacing.xl)) {
        LibrarySort.entries.forEach { sort ->
            val label = stringResource(
                when (sort) {
                    LibrarySort.LAST_PLAYED -> R.string.library_sort_last_played
                    LibrarySort.PLAYTIME -> R.string.library_sort_playtime
                    LibrarySort.TITLE -> R.string.library_sort_title
                    LibrarySort.FIRST_PLAYED -> R.string.library_sort_first_played
                },
            )
            ListItem(
                headlineContent = { Text(label) },
                trailingContent = { if (sort == current) Icon(Icons.Default.Check, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().clickable { onSelect(sort) },
            )
        }
    }
}

@Composable
private fun LibrarySkeleton(modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false,
    ) {
        fullWidth { Skeleton(Modifier.fillMaxWidth().height(64.dp)) }
        fullWidth { Skeleton(Modifier.fillMaxWidth().height(48.dp)) }
        items(List(9) { it }) { Skeleton(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) }
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
