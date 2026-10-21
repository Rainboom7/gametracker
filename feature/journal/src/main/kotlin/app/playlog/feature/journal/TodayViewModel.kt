package app.playlog.feature.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.playlog.core.data.TodayRepository
import app.playlog.core.model.TodayItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(repository: TodayRepository) : ViewModel() {
    val items: StateFlow<List<TodayItem>> = repository.observeToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
