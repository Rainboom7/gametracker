package app.playlog.feature.releases

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.data.ReleaseCard
import app.playlog.core.data.ReleaseFilter
import app.playlog.core.designsystem.component.PreloadImages
import app.playlog.core.designsystem.theme.PlaylogColors
import app.playlog.core.designsystem.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun ReleasesRoute(onBack: () -> Unit, viewModel: ReleasesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReleasesScreen(state, onBack, viewModel::setFilter, viewModel::toggleWishlist, viewModel::refresh)
}

/**
 * Upcoming releases as a calendar: a compact top bar, then one scrolling list in which the filters
 * scroll away and the strip of release days stays pinned, so most of the screen is games.
 */
@Composable
fun ReleasesScreen(
    state: ReleasesUiState,
    onBack: () -> Unit,
    onFilter: (ReleaseFilter) -> Unit,
    onToggleWishlist: (ReleaseCard) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.releases_back)) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.releases_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.releases_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.releases_refresh)) }
        }
        when (state) {
            ReleasesUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PlaylogColors.Sky) }
            is ReleasesUiState.Failed -> Column(
                Modifier.fillMaxSize().padding(Spacing.xl),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.releases_failed), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.releases_retry)) }
            }
            is ReleasesUiState.Content -> Calendar(state, onFilter, onToggleWishlist)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Calendar(state: ReleasesUiState.Content, onFilter: (ReleaseFilter) -> Unit, onToggleWishlist: (ReleaseCard) -> Unit) {
    val calendar = state.calendar
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val open: (ReleaseCard) -> Unit = { card -> runCatching { uri.openUri(card.game.storeUrl) } }
    // Index of each day's header in the list, for the strip: items before the days are the
    // highlight (optional), the filters, the stale note (optional) and the strip itself.
    val lead = listOfNotNull(calendar.highlight, Unit, Unit.takeIf { state.stale }, Unit).size
    val dayIndex = remember(calendar) {
        var index = lead
        calendar.days.map { day -> index.also { index += 1 + day.games.size } }
    }
    val selected by remember(dayIndex) {
        derivedStateOf { dayIndex.indexOfLast { it <= listState.firstVisibleItemIndex }.takeIf { it >= 0 } ?: 0 }
    }
    val ahead = remember(calendar) {
        (calendar.days.flatMap { it.games } + calendar.undated).take(PreloadAhead).mapNotNull { it.game.artUrls().firstOrNull() }
    }
    PreloadImages(ahead)
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.xxl)) {
        calendar.highlight?.let { card ->
            item(key = "highlight") { HighlightCard(card, state.today, onClick = { open(card) }, onToggleWishlist = { onToggleWishlist(card) }) }
        }
        item(key = "filters") { Filters(state.filter, onFilter) }
        if (state.stale) {
            item(key = "stale") {
                Text(
                    stringResource(R.string.releases_stale),
                    style = MaterialTheme.typography.bodySmall,
                    color = PlaylogColors.Orange,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xxs),
                )
            }
        }
        stickyHeader(key = "strip") {
            DayStrip(calendar.days.map { it.date to it.games.size }, selected, state.today) { i ->
                scope.launch { listState.animateScrollToItem(dayIndex[i]) }
            }
        }
        if (calendar.isEmpty) {
            item(key = "empty") { EmptyNote(state.filter) }
        }
        calendar.days.forEach { day ->
            item(key = "day-${day.date}") { DayHeader(day.date, state.today) }
            items(day.games, key = { "${day.date}-${it.game.key}" }) { card ->
                ReleaseRow(card, onClick = { open(card) }, onToggleWishlist = { onToggleWishlist(card) })
            }
        }
        if (calendar.undated.isNotEmpty()) {
            item(key = "undated") { UndatedHeader() }
            items(calendar.undated, key = { "undated-${it.game.key}" }) { card ->
                ReleaseRow(card, onClick = { open(card) }, onToggleWishlist = { onToggleWishlist(card) }, dateText = card.game.dateText)
            }
        }
    }
}

@Composable
private fun Filters(selected: ReleaseFilter, onFilter: (ReleaseFilter) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        items(ReleaseFilter.entries) { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onFilter(filter) },
                label = { Text(stringResource(filterLabel(filter))) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PlaylogColors.Sky,
                    selectedLabelColor = PlaylogColors.Background,
                ),
            )
        }
    }
}

@Composable
private fun EmptyNote(filter: ReleaseFilter) {
    Text(
        stringResource(
            when (filter) {
                ReleaseFilter.ALL -> R.string.releases_empty_ALL
                ReleaseFilter.FOR_YOU -> R.string.releases_empty_FOR_YOU
                ReleaseFilter.WISHLIST -> R.string.releases_empty_WISHLIST
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(Spacing.lg),
    )
}

private fun filterLabel(filter: ReleaseFilter) = when (filter) {
    ReleaseFilter.ALL -> R.string.releases_filter_ALL
    ReleaseFilter.FOR_YOU -> R.string.releases_filter_FOR_YOU
    ReleaseFilter.WISHLIST -> R.string.releases_filter_WISHLIST
}

/** "Сегодня", "Завтра", "Через 5 дн."; null past two weeks, where the date alone reads better. */
@Composable
internal fun relativeDay(date: LocalDate, today: LocalDate): String? {
    val days = date.toEpochDay() - today.toEpochDay()
    return when {
        days == 0L -> stringResource(R.string.releases_today)
        days == 1L -> stringResource(R.string.releases_tomorrow)
        days in 2..RelativeDaysLimit -> stringResource(R.string.releases_in_days, days.toInt())
        else -> null
    }
}

private const val RelativeDaysLimit = 14L

private const val PreloadAhead = 40
