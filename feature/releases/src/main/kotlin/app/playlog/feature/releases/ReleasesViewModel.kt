package app.playlog.feature.releases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.playlog.core.common.AppClock
import app.playlog.core.data.DealsRepository
import app.playlog.core.data.ReleaseCalendar
import app.playlog.core.data.ReleaseCard
import app.playlog.core.data.ReleaseFilter
import app.playlog.core.data.ReleasesRepository
import app.playlog.core.data.WishlistRepository
import app.playlog.core.model.DealPick
import app.playlog.core.model.DealsState
import app.playlog.core.model.ReleasesState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed interface ReleasesUiState {
    data object Loading : ReleasesUiState

    data class Failed(val message: String?) : ReleasesUiState

    data class Content(
        val calendar: ReleaseCalendar,
        val filter: ReleaseFilter,
        val today: LocalDate,
        /** The list is from an earlier fetch because the store did not answer now. */
        val stale: Boolean = false,
    ) : ReleasesUiState
}

@HiltViewModel
class ReleasesViewModel @Inject constructor(
    private val releases: ReleasesRepository,
    private val wishlist: WishlistRepository,
    deals: DealsRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val filter = MutableStateFlow(ReleaseFilter.ALL)

    private val taste = deals.observe()
        .map { (it as? DealsState.Ready)?.taste.orEmpty() }
        .onStart { emit(emptyMap()) }

    val uiState: StateFlow<ReleasesUiState> = combine(releases.observe(), wishlist.observe(), taste, filter) { state, wished, taste, filter ->
        val today = clock.now().atZone(clock.zone()).toLocalDate()
        val (games, stale) = when (state) {
            ReleasesState.Loading -> return@combine ReleasesUiState.Loading
            is ReleasesState.Ready -> state.games to false
            is ReleasesState.Failed -> if (state.stale.isEmpty() && wished.none { it.comingSoon }) {
                return@combine ReleasesUiState.Failed(state.message)
            } else {
                state.stale to true
            }
        }
        ReleasesUiState.Content(ReleaseCalendar.build(games, wished, taste, today, filter, clock.zone()), filter, today, stale)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReleasesUiState.Loading)

    fun setFilter(value: ReleaseFilter) {
        filter.value = value
    }

    fun refresh() {
        viewModelScope.launch { releases.refresh() }
    }

    fun toggleWishlist(card: ReleaseCard) {
        val game = card.game
        viewModelScope.launch {
            wishlist.toggle(
                DealPick(
                    platform = game.platform,
                    storeId = game.storeId,
                    title = game.title,
                    headerUrl = game.headerUrl,
                    storeUrl = game.storeUrl,
                    discountPercent = 0,
                    priceText = "",
                    tags = game.tags,
                ),
                comingSoon = true,
                releaseDate = game.date?.atStartOfDay(clock.zone())?.toInstant(),
            )
        }
    }
}
