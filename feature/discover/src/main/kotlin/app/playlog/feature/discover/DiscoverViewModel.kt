package app.playlog.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.playlog.core.data.DealsRepository
import app.playlog.core.data.DiscoverRepository
import app.playlog.core.model.DealPick
import app.playlog.core.model.DealsState
import app.playlog.core.model.DiscoverSummary
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
import javax.inject.Inject

/** Backlog roulette: the last drawn title and the platform filter. */
data class RouletteState(
    val platform: Platform? = null,
    val result: LibraryEntry? = null,
    /** True after a spin found nothing on the selected platform. */
    val empty: Boolean = false,
    /** Bumped on every spin so the card can animate even when the same title comes up. */
    val spins: Int = 0,
)

sealed interface DiscoverUiState {
    data object Loading : DiscoverUiState
    data class Content(val summary: DiscoverSummary, val roulette: RouletteState) : DiscoverUiState
}

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val discover: DiscoverRepository,
    private val dealsRepository: DealsRepository,
) : ViewModel() {
    private val roulette = MutableStateFlow(RouletteState())

    val deals: StateFlow<DealsState> = dealsRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DealsState.Loading)

    /** The deal whose detail sheet is open. */
    val openDeal = MutableStateFlow<DealPick?>(null)

    val uiState: StateFlow<DiscoverUiState> = combine(discover.observeSummary(), roulette) { summary, r ->
        DiscoverUiState.Content(summary, r)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverUiState.Loading)

    fun selectRoulettePlatform(platform: Platform?) = roulette.update { RouletteState(platform = platform) }

    fun refreshDeals() {
        viewModelScope.launch { dealsRepository.refresh() }
    }

    fun openDeal(pick: DealPick?) {
        openDeal.value = pick
    }

    fun spin() {
        viewModelScope.launch {
            val current = roulette.value
            val result = discover.roulette(current.platform, exclude = current.result)
            roulette.update { it.copy(result = result, empty = result == null, spins = it.spins + 1) }
        }
    }
}
