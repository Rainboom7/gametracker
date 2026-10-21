package app.playlog.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.playlog.core.common.AppClock
import app.playlog.core.data.AccountRepository
import app.playlog.core.data.LibraryRepository
import app.playlog.core.data.SyncScheduler
import app.playlog.core.data.UserSettingsRepository
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Platform
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

data class LibraryControls(
    val query: String = "",
    val platform: Platform? = null,
    val sort: LibrarySort = LibrarySort.LAST_PLAYED,
    val filters: LibraryFilters = LibraryFilters(),
    val layout: LibraryLayout = LibraryLayout.LIST,
)

sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    /** Nothing synced yet; [hasAccounts] decides between "connect" and "syncing" copy. */
    data class Empty(val hasAccounts: Boolean, val isSyncing: Boolean) : LibraryUiState

    data class Content(
        val entries: List<LibraryEntry>,
        val nowPlaying: LibraryEntry?,
        val totalCount: Int,
        val totalPlaytime: Duration,
        val availablePlatforms: Set<Platform>,
        val controls: LibraryControls,
        val isSyncing: Boolean,
        val platformSummaries: List<PlatformSummary> = emptyList(),
    ) : LibraryUiState
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    library: LibraryRepository,
    accounts: AccountRepository,
    private val syncScheduler: SyncScheduler,
    private val clock: AppClock,
    private val settings: UserSettingsRepository,
) : ViewModel() {
    private val controls = MutableStateFlow(LibraryControls())

    val uiState: StateFlow<LibraryUiState> = combine(
        library.observeLibrary(),
        accounts.observeAccounts(),
        controls.combine(settings.settings) { c, s -> c.copy(layout = if (s.libraryGrid) LibraryLayout.GRID else LibraryLayout.LIST) },
        syncScheduler.isSyncing,
    ) { entries, linked, c, syncing ->
        if (entries.isEmpty()) {
            LibraryUiState.Empty(hasAccounts = linked.isNotEmpty(), isSyncing = syncing)
        } else {
            LibraryUiState.Content(
                entries = LibraryFilter.apply(entries, c.query, c.platform, c.sort, c.filters),
                nowPlaying = LibraryFilter.nowPlaying(entries, clock.now()).takeIf { c.query.isEmpty() },
                totalCount = entries.size,
                totalPlaytime = entries.fold(Duration.ZERO) { acc, e -> acc + e.totalPlaytime },
                availablePlatforms = entries.flatMapTo(sortedSetOf()) { it.platforms },
                controls = c,
                isSyncing = syncing,
                platformSummaries = LibraryFilter.platformSummaries(entries),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)

    fun onQueryChange(query: String) = controls.update { it.copy(query = query) }

    fun onPlatformSelected(platform: Platform?) = controls.update { it.copy(platform = platform) }

    fun onSortSelected(sort: LibrarySort) = controls.update { it.copy(sort = sort) }

    fun onFiltersChange(filters: LibraryFilters) = controls.update { it.copy(filters = filters) }

    fun onLayoutChange(layout: LibraryLayout) {
        viewModelScope.launch { settings.update { it.copy(libraryGrid = layout == LibraryLayout.GRID) } }
    }

    fun refresh() = syncScheduler.requestSync()
}
