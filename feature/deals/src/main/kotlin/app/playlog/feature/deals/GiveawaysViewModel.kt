package app.playlog.feature.deals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.playlog.core.data.GiveawaysRepository
import app.playlog.core.model.GiveawayStore
import app.playlog.core.model.GiveawaysState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shared by the giveaways screen and the Home/Discover card. */
@HiltViewModel
class GiveawaysViewModel @Inject constructor(private val repository: GiveawaysRepository) : ViewModel() {
    val state: StateFlow<GiveawaysState> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), GiveawaysState.Loading)

    /** Null shows every store. */
    val store = MutableStateFlow<GiveawayStore?>(null)

    fun selectStore(value: GiveawayStore?) {
        store.value = value
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
