package app.playlog.feature.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.playlog.core.common.runSuspendCatching
import app.playlog.core.data.BadgesRepository
import app.playlog.core.model.Badge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BadgesUiState {
    data object Loading : BadgesUiState

    data class Ready(val badges: List<Badge>) : BadgesUiState {
        val unlocked: Int get() = badges.count { it.level > 0 }
        val levels: Int get() = badges.sumOf { it.level }
        val maxLevels: Int get() = badges.sumOf { it.milestone.maxLevel }
        val recent: List<Badge> get() = badges.filter { it.isRecent }
    }
}

@HiltViewModel
class BadgesViewModel @Inject constructor(private val repository: BadgesRepository) : ViewModel() {
    val state: StateFlow<BadgesUiState> = repository.observeBadges()
        .map<List<Badge>, BadgesUiState> { BadgesUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), BadgesUiState.Loading)

    /** Called by the full screen only: the Home strip keeps the "new" flags until the user looks. */
    fun markSeen() {
        viewModelScope.launch { runSuspendCatching { repository.markSeen() } }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
