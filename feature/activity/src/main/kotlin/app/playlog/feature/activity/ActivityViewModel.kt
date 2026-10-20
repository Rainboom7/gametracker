package app.playlog.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.playlog.core.common.AppClock
import app.playlog.core.data.ActivityCalculator
import app.playlog.core.data.ActivityRepository
import app.playlog.core.model.ActivityPeriod
import app.playlog.core.model.ActivitySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

data class ActivitySelection(val period: ActivityPeriod, val anchor: LocalDate)

sealed interface ActivityUiState {
    data object Loading : ActivityUiState
    data class Content(val summary: ActivitySummary, val canGoForward: Boolean, val streakDays: Int) : ActivityUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val activity: ActivityRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val today get() = LocalDate.ofInstant(clock.now(), clock.zone())
    private val selection = MutableStateFlow(ActivitySelection(ActivityPeriod.WEEK, today))

    val uiState: StateFlow<ActivityUiState> = combine(
        selection.flatMapLatest { s -> activity.observeSummary(s.period, s.anchor).map { s to it } },
        activity.observeStreak(),
    ) { (s, summary), streak ->
        val currentEnd = ActivityCalculator.range(s.period, today).endInclusive
        ActivityUiState.Content(summary, canGoForward = summary.endInclusive.isBefore(currentEnd), streakDays = streak)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState.Loading)

    fun selectPeriod(period: ActivityPeriod) = selection.update { ActivitySelection(period, today) }

    fun previous() = selection.update { it.copy(anchor = ActivityCalculator.previousAnchor(it.period, it.anchor)) }

    fun next() = selection.update { s ->
        val next = when (s.period) {
            ActivityPeriod.WEEK -> s.anchor.plusWeeks(1)
            ActivityPeriod.MONTH -> s.anchor.plusMonths(1)
            ActivityPeriod.YEAR -> s.anchor.plusYears(1)
        }
        if (next.isAfter(today)) s.copy(anchor = today) else s.copy(anchor = next)
    }
}
