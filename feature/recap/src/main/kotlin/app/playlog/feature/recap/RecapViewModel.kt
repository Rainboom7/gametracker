package app.playlog.feature.recap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.playlog.core.common.AppClock
import app.playlog.core.data.ActivityRepository
import app.playlog.core.data.DealsRepository
import app.playlog.core.model.ActivityPeriod
import app.playlog.core.model.DealsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

sealed interface RecapUiState {
    data object Loading : RecapUiState
    data class Story(val pages: List<RecapPage>) : RecapUiState
}

@HiltViewModel
class RecapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    activity: ActivityRepository,
    deals: DealsRepository,
    clock: AppClock,
) : ViewModel() {
    private val period = runCatching { ActivityPeriod.valueOf(savedStateHandle.toRoute<RecapDestination>().period) }
        .getOrDefault(ActivityPeriod.WEEK)

    val uiState: StateFlow<RecapUiState> = combine(
        activity.observeSummary(period, LocalDate.ofInstant(clock.now(), clock.zone())),
        activity.observeInsights().map { it.longestStreak },
        deals.observe().map { state -> (state as? DealsState.Ready)?.taste.orEmpty().entries.sortedByDescending { it.value }.map { it.key } }
            .onStart { emit(emptyList()) },
    ) { summary, streak, taste ->
        RecapUiState.Story(RecapPages.build(summary, streak, taste))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecapUiState.Loading)
}
