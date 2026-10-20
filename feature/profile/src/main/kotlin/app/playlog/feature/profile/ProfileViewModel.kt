package app.playlog.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.playlog.core.data.AccountRepository
import app.playlog.core.data.LibraryRepository
import app.playlog.core.data.SyncScheduler
import app.playlog.core.data.UserSettings
import app.playlog.core.data.UserSettingsRepository
import app.playlog.core.model.Account
import app.playlog.core.model.Platform
import app.playlog.core.model.ProfileStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

data class ProfileUiState(
    val stats: ProfileStats = ProfileStats(0, Duration.ZERO, 0),
    /** Every supported platform, with its account when linked. */
    val accounts: List<Pair<Platform, Account?>> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val isSyncing: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    library: LibraryRepository,
    private val accounts: AccountRepository,
    private val settingsRepository: UserSettingsRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        library.observeProfileStats(),
        accounts.observeAccounts(),
        settingsRepository.settings,
        syncScheduler.isSyncing,
    ) { stats, linked, settings, syncing ->
        val byPlatform = linked.associateBy { it.platform }
        ProfileUiState(
            stats = stats,
            accounts = Platform.entries.filter { it in accounts.availablePlatforms }.map { it to byPlatform[it] },
            settings = settings,
            isSyncing = syncing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun refresh(platform: Platform? = null) = syncScheduler.requestSync(platform)

    fun unlink(platform: Platform, deleteData: Boolean) {
        viewModelScope.launch { accounts.unlink(platform, deleteData) }
    }

    fun setBackgroundSync(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.update { it.copy(backgroundSync = enabled) } }
        syncScheduler.setPeriodicSync(enabled)
    }

    fun setWifiOnlyMedia(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.update { it.copy(wifiOnlyMedia = enabled) } }
    }

    fun setWeeklySummary(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.update { it.copy(weeklySummary = enabled) } }
    }

    fun finishOnboarding() {
        viewModelScope.launch { settingsRepository.update { it.copy(onboardingFinished = true) } }
    }
}
