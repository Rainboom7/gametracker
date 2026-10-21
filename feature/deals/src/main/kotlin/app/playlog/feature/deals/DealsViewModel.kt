package app.playlog.feature.deals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.playlog.core.data.DealsRepository
import app.playlog.core.data.StoreDealsRepository
import app.playlog.core.model.DealPick
import app.playlog.core.model.DealsState
import app.playlog.core.model.Platform
import app.playlog.core.model.StoreDealsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DealsViewModel @Inject constructor(
    private val stores: StoreDealsRepository,
    personal: DealsRepository,
) : ViewModel() {
    val platforms: List<Platform> = stores.platforms

    val selected = MutableStateFlow(platforms.firstOrNull() ?: Platform.STEAM)

    val store: StateFlow<StoreDealsState> = selected
        .flatMapLatest { stores.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoreDealsState.Loading)

    /** "For you" picks (Steam), shown above the Steam store list. */
    val personal: StateFlow<DealsState> = personal.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DealsState.Loading)

    val openDeal = MutableStateFlow<DealPick?>(null)

    fun countries(platform: Platform) = stores.countries(platform)

    fun select(platform: Platform) {
        selected.value = platform
    }

    fun loadMore() {
        viewModelScope.launch { stores.loadMore(selected.value) }
    }

    fun refresh() {
        viewModelScope.launch { stores.refresh(selected.value) }
    }

    fun setRegion(country: String?) {
        viewModelScope.launch { stores.setRegion(selected.value, country) }
    }

    fun open(pick: DealPick?) {
        openDeal.value = pick
    }
}
