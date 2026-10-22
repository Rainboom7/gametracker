package app.playlog.feature.deals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.playlog.core.common.runSuspendCatching
import app.playlog.core.data.PriceHistory
import app.playlog.core.data.WishlistRepository
import app.playlog.core.model.DealPick
import app.playlog.core.model.PriceLow
import app.playlog.core.model.WishMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WishSheetViewModel @Inject constructor(
    private val wishlist: WishlistRepository,
    private val history: PriceHistory,
) : ViewModel() {
    val wished: StateFlow<WishMatcher> = wishlist.observe().map(::WishMatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), WishMatcher.EMPTY)

    fun toggle(pick: DealPick) {
        viewModelScope.launch { runSuspendCatching { wishlist.toggle(pick) } }
    }

    suspend fun lowest(pick: DealPick): PriceLow? = runSuspendCatching { history.lowest(pick.platform, pick.storeId) }.getOrNull()

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
