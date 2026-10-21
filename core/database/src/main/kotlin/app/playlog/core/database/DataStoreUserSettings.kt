package app.playlog.core.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.playlog.core.data.UserSettings
import app.playlog.core.data.UserSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreUserSettings(context: Context) : UserSettingsRepository {
    private val dataStore = context.applicationContext.settingsDataStore

    override val settings: Flow<UserSettings> = dataStore.data.map { it.toSettings() }

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[BACKGROUND_SYNC] = next.backgroundSync
            prefs[WIFI_ONLY_MEDIA] = next.wifiOnlyMedia
            prefs[WEEKLY_SUMMARY] = next.weeklySummary
            prefs[ONBOARDING_FINISHED] = next.onboardingFinished
            prefs[NINTENDO_BANNER_DISMISSED] = next.nintendoImportBannerDismissed
            prefs[LIBRARY_GRID] = next.libraryGrid
            prefs[WEEKLY_GOAL_HOURS] = next.weeklyGoalHours
            prefs[WEEKLY_GOAL_IS_LIMIT] = next.weeklyGoalIsLimit
        }
    }

    private fun Preferences.toSettings(): UserSettings {
        val defaults = UserSettings()
        return UserSettings(
            backgroundSync = this[BACKGROUND_SYNC] ?: defaults.backgroundSync,
            wifiOnlyMedia = this[WIFI_ONLY_MEDIA] ?: defaults.wifiOnlyMedia,
            weeklySummary = this[WEEKLY_SUMMARY] ?: defaults.weeklySummary,
            onboardingFinished = this[ONBOARDING_FINISHED] ?: defaults.onboardingFinished,
            nintendoImportBannerDismissed = this[NINTENDO_BANNER_DISMISSED] ?: defaults.nintendoImportBannerDismissed,
            libraryGrid = this[LIBRARY_GRID] ?: defaults.libraryGrid,
            weeklyGoalHours = this[WEEKLY_GOAL_HOURS] ?: defaults.weeklyGoalHours,
            weeklyGoalIsLimit = this[WEEKLY_GOAL_IS_LIMIT] ?: defaults.weeklyGoalIsLimit,
        )
    }

    private companion object {
        val BACKGROUND_SYNC = booleanPreferencesKey("background_sync")
        val WIFI_ONLY_MEDIA = booleanPreferencesKey("wifi_only_media")
        val WEEKLY_SUMMARY = booleanPreferencesKey("weekly_summary")
        val ONBOARDING_FINISHED = booleanPreferencesKey("onboarding_finished")
        val NINTENDO_BANNER_DISMISSED = booleanPreferencesKey("nintendo_banner_dismissed")
        val LIBRARY_GRID = booleanPreferencesKey("library_grid")
        val WEEKLY_GOAL_HOURS = intPreferencesKey("weekly_goal_hours")
        val WEEKLY_GOAL_IS_LIMIT = booleanPreferencesKey("weekly_goal_is_limit")
    }
}
