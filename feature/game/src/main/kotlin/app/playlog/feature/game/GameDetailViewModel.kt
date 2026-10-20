package app.playlog.feature.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.playlog.core.data.ActivityRepository
import app.playlog.core.data.LibraryRepository
import app.playlog.core.data.ScreenshotRepository
import app.playlog.core.model.ActivityBucket
import app.playlog.core.model.GameId
import app.playlog.core.model.GameStatus
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Screenshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface GameDetailUiState {
    data object Loading : GameDetailUiState
    data object NotFound : GameDetailUiState
    data class Content(
        val entry: LibraryEntry,
        val lastDays: List<ActivityBucket>,
        val screenshots: List<Screenshot>,
    ) : GameDetailUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GameDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val library: LibraryRepository,
    activity: ActivityRepository,
    screenshots: ScreenshotRepository,
) : ViewModel() {
    private val gameId = GameId.decode(savedStateHandle.toRoute<GameDestination>().gameId)

    val uiState: StateFlow<GameDetailUiState> = library.observeEntry(gameId)
        .flatMapLatest { entry ->
            if (entry == null) {
                flowOf(GameDetailUiState.NotFound)
            } else {
                combine(activity.observeEntryDays(entry, DAYS), screenshots.observeForEntry(entry)) { days, shots ->
                    GameDetailUiState.Content(entry, days, shots)
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
    }
}
