package app.playlog.feature.deals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.playlog.core.common.runSuspendCatching
import app.playlog.core.data.CatalogGame
import app.playlog.core.data.CatalogSearch
import app.playlog.core.data.PriceHistory
import app.playlog.core.data.WishlistRepository
import app.playlog.core.model.DealPick
import app.playlog.core.model.PriceLow
import app.playlog.core.model.WishMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CatalogState {
    data object Idle : CatalogState

    data object Loading : CatalogState

    data class Ready(val query: String, val games: List<CatalogGame>) : CatalogState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CatalogSearchViewModel @Inject constructor(
    private val catalog: CatalogSearch,
    private val wishlist: WishlistRepository,
    private val history: PriceHistory,
) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<CatalogState> = query
        .map { it.trim() }
        .distinctUntilChanged()
        .debounce(TYPING_PAUSE_MS)
        .transformLatest { q ->
            if (q.length < MIN_QUERY) {
                emit(CatalogState.Idle)
            } else {
                emit(CatalogState.Loading)
                emit(CatalogState.Ready(q, runSuspendCatching { catalog.search(q) }.getOrDefault(emptyList())))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), CatalogState.Idle)

    val wished: StateFlow<WishMatcher> = wishlist.observe().map(::WishMatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), WishMatcher.EMPTY)

    val openDeal = MutableStateFlow<DealPick?>(null)
    val lowest = MutableStateFlow<PriceLow?>(null)

    fun setQuery(value: String) {
        query.value = value
    }

    fun toggleWish(pick: DealPick) {
        viewModelScope.launch { runSuspendCatching { wishlist.toggle(pick) } }
    }

    fun open(pick: DealPick?) {
        openDeal.value = pick
        lowest.value = null
        if (pick != null) viewModelScope.launch { lowest.value = runSuspendCatching { history.lowest(pick.platform, pick.storeId) }.getOrNull() }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val TYPING_PAUSE_MS = 450L
        const val MIN_QUERY = 2
    }
}
