package app.playlog.feature.deals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.playlog.core.common.runSuspendCatching
import app.playlog.core.data.DealsRepository
import app.playlog.core.data.PriceHistory
import app.playlog.core.data.StoreDealsRepository
import app.playlog.core.data.WishlistRepository
import app.playlog.core.model.DealPick
import app.playlog.core.model.DealsState
import app.playlog.core.model.Platform
import app.playlog.core.model.PriceLow
import app.playlog.core.model.StoreDealsState
import app.playlog.core.model.WishlistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The store tabs, then the wishlist. */
sealed interface DealsTab {
    data class Store(val platform: Platform) : DealsTab

    data object Wishlist : DealsTab
}

sealed interface SearchState {
    data object Idle : SearchState

    data object Loading : SearchState

    data class Ready(val query: String, val results: List<DealPick>) : SearchState

    data class Failed(val message: String?) : SearchState
}

sealed interface WishlistAction {
    data object Idle : WishlistAction

    data object Working : WishlistAction

    /** [drops] games went on sale since the last check. */
    data class Checked(val drops: Int) : WishlistAction

    data class Imported(val added: Int) : WishlistAction
}

/** The selected store's deals after taste scoring, filters and sort. */
data class StoreView(
    val state: StoreDealsState,
    val visible: List<DealPick>,
    val genres: List<String>,
    val maxPrice: Double?,
    val hasTaste: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DealsViewModel @Inject constructor(
    private val stores: StoreDealsRepository,
    personal: DealsRepository,
    private val wishlistRepository: WishlistRepository,
    private val history: PriceHistory,
) : ViewModel() {
    val platforms: List<Platform> = stores.platforms

    val tab = MutableStateFlow<DealsTab>(DealsTab.Store(platforms.firstOrNull() ?: Platform.STEAM))
    val filter = MutableStateFlow(DealsFilter())
    val query = MutableStateFlow("")
    val search = MutableStateFlow<SearchState>(SearchState.Idle)
    val wishlistAction = MutableStateFlow<WishlistAction>(WishlistAction.Idle)

    private var lastPlatform: Platform = platforms.firstOrNull() ?: Platform.STEAM
    private var searchJob: Job? = null

    private val selected = tab.map { (it as? DealsTab.Store)?.platform ?: lastPlatform }.distinctUntilChanged()

    /** "For you" picks (Steam), shown above the Steam store list; their tag profile scores every store. */
    val personal: StateFlow<DealsState> = personal.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), DealsState.Loading)

    val wishlist: StateFlow<List<WishlistItem>> = wishlistRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val store: StateFlow<StoreView> = combine(selected.flatMapLatest { stores.observe(it) }, this.personal, filter, query) { state, picks, f, q ->
        val taste = (picks as? DealsState.Ready)?.taste.orEmpty()
        val scored = DealsFilter.withTaste((state as? StoreDealsState.Ready)?.deals.orEmpty(), taste)
        StoreView(
            state = state,
            visible = f.apply(scored, q),
            genres = DealsFilter.topGenres(scored),
            maxPrice = scored.mapNotNull { it.priceValue }.maxOrNull(),
            hasTaste = scored.any { it.match > 0f },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), StoreView(StoreDealsState.Loading, emptyList(), emptyList(), null, false))

    val openDeal = MutableStateFlow<DealPick?>(null)
    val lowest = MutableStateFlow<PriceLow?>(null)

    fun countries(platform: Platform) = stores.countries(platform)

    fun select(tab: DealsTab) {
        val previous = this.tab.value
        this.tab.value = tab
        if (tab is DealsTab.Store) {
            if (previous is DealsTab.Store && previous.platform != tab.platform) filter.value = filter.value.forOtherStore()
            lastPlatform = tab.platform
            search.value = SearchState.Idle
        }
    }

    fun setFilter(value: DealsFilter) {
        filter.value = value
    }

    fun setQuery(value: String) {
        query.value = value
        if (value.isBlank()) search.value = SearchState.Idle
    }

    /** Searches the whole store, not just the sale, so full-price games can be wishlisted. */
    fun searchStore() {
        val q = query.value.trim()
        if (q.isEmpty()) return
        val platform = lastPlatform
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            search.value = SearchState.Loading
            search.value = runSuspendCatching { stores.search(platform, q) }
                .fold({ SearchState.Ready(q, it) }, { SearchState.Failed(it.message) })
        }
    }

    fun loadMore() {
        viewModelScope.launch { stores.loadMore(lastPlatform) }
    }

    fun refresh() {
        viewModelScope.launch { stores.refresh(lastPlatform) }
    }

    fun setRegion(country: String?) {
        viewModelScope.launch { stores.setRegion(lastPlatform, country) }
    }

    fun open(pick: DealPick?) {
        openDeal.value = pick
        lowest.value = null
        if (pick != null) {
            viewModelScope.launch {
                lowest.value = runSuspendCatching { history.lowest(pick.platform, pick.storeId) }.getOrNull()
            }
        }
    }

    fun openWish(item: WishlistItem) = open(
        DealPick(
            platform = item.platform,
            storeId = item.storeId,
            title = item.title,
            headerUrl = item.headerUrl,
            storeUrl = item.storeUrl,
            discountPercent = item.price?.discountPercent ?: 0,
            priceText = item.price?.priceText.orEmpty(),
            originalPriceText = item.price?.originalPriceText,
            endsAt = item.price?.endsAt,
            priceValue = item.price?.priceValue,
        ),
    )

    fun toggleWish(pick: DealPick) {
        viewModelScope.launch { wishlistRepository.toggle(pick) }
    }

    fun removeWish(item: WishlistItem) {
        viewModelScope.launch { wishlistRepository.remove(item.key) }
    }

    fun checkPrices() = wishlistJob { WishlistAction.Checked(wishlistRepository.checkPrices().size) }

    fun importWishlists() = wishlistJob { WishlistAction.Imported(wishlistRepository.importFromAccounts()) }

    private fun wishlistJob(block: suspend () -> WishlistAction) {
        if (wishlistAction.value == WishlistAction.Working) return
        viewModelScope.launch {
            wishlistAction.value = WishlistAction.Working
            wishlistAction.value = runSuspendCatching { block() }.getOrDefault(WishlistAction.Idle)
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
