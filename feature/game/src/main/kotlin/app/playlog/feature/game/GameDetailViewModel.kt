package app.playlog.feature.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.playlog.core.data.ActivityRepository
import app.playlog.core.data.GameMetadataRepository
import app.playlog.core.data.LibraryRepository
import app.playlog.core.data.ScreenshotRepository
import app.playlog.core.model.ActivityBucket
import app.playlog.core.model.GameId
import app.playlog.core.model.GameMetadata
import app.playlog.core.model.GameStatus
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Screenshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** HowLongToBeat / store facts for the page; [loading] while the first lookup runs. */
data class MetadataUi(val metadata: GameMetadata? = null, val loading: Boolean = true)

sealed interface GameDetailUiState {
    data object Loading : GameDetailUiState
    data object NotFound : GameDetailUiState
    data class Content(
        val entry: LibraryEntry,
        val lastDays: List<ActivityBucket>,
        val screenshots: List<Screenshot>,
        val metadata: MetadataUi = MetadataUi(loading = false),
    ) : GameDetailUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GameDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val library: LibraryRepository,
    activity: ActivityRepository,
    screenshots: ScreenshotRepository,
    metadata: GameMetadataRepository,
) : ViewModel() {
    private val gameId = GameId.decode(savedStateHandle.toRoute<GameDestination>().gameId)

    private val entry = library.observeEntry(gameId).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    // Keyed by title, not by entry: toggling a favorite re-emits the entry and must not restart the lookup.
    private val metadataUi: Flow<MetadataUi> = entry.filterNotNull()
        .distinctUntilChangedBy { it.key }
        .flatMapLatest { e -> observeMetadata(metadata, e) }
        .onStart { emit(MetadataUi()) }

    val uiState: StateFlow<GameDetailUiState> = entry
        .flatMapLatest { e ->
            if (e == null) {
                flowOf(GameDetailUiState.NotFound)
            } else {
                combine(activity.observeEntryDays(e, DAYS), screenshots.observeForEntry(e), metadataUi) { days, shots, meta ->
                    GameDetailUiState.Content(e, days, shots, meta)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameDetailUiState.Loading)

    fun toggleFavorite() {
        val entry = (uiState.value as? GameDetailUiState.Content)?.entry ?: return
        viewModelScope.launch { library.setFavorite(entry, !entry.isFavorite) }
    }

    fun setStatus(status: GameStatus) {
        val entry = (uiState.value as? GameDetailUiState.Content)?.entry ?: return
        viewModelScope.launch { library.setStatus(entry, status) }
    }

    private companion object {
        const val DAYS = 14

        fun observeMetadata(repository: GameMetadataRepository, entry: LibraryEntry): Flow<MetadataUi> = flow {
            var last: GameMetadata? = null
            repository.observe(entry).collect {
                last = it
                emit(MetadataUi(it, loading = it == null))
            }
            if (last == null) emit(MetadataUi(null, loading = false))
        }
    }
}
