package app.playlog.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class UserSettings(
    /** WorkManager sync every 6 hours. */
    val backgroundSync: Boolean = true,
    /** Load full-size screenshots only on unmetered networks. */
    val wifiOnlyMedia: Boolean = true,
    val weeklySummary: Boolean = false,
    val onboardingFinished: Boolean = false,
    val nintendoImportBannerDismissed: Boolean = false,
    /** Library shows a cover grid instead of the list with per-platform sources. */
    val libraryGrid: Boolean = false,
    /** Weekly playtime target on Home, in hours; 0 means no goal. */
    val weeklyGoalHours: Int = 0,
    /** The target is a ceiling to stay under rather than a goal to reach. */
    val weeklyGoalIsLimit: Boolean = false,
)

interface UserSettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun update(transform: (UserSettings) -> UserSettings)
}

class InMemoryUserSettingsRepository(initial: UserSettings = UserSettings()) : UserSettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<UserSettings> = state

    override suspend fun update(transform: (UserSettings) -> UserSettings) = state.update(transform)
}
