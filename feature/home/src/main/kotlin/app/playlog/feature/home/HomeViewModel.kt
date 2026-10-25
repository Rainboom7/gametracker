package app.playlog.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.playlog.core.common.AppClock
import app.playlog.core.data.ActivityRepository
import app.playlog.core.data.DealsRepository
import app.playlog.core.data.DiscoverRepository
import app.playlog.core.data.LibraryRepository
import app.playlog.core.data.ScreenshotRepository
import app.playlog.core.data.UserSettings
import app.playlog.core.data.UserSettingsRepository
import app.playlog.core.model.ActivityPeriod
import app.playlog.core.model.ActivitySummary
import app.playlog.core.model.DealPick
import app.playlog.core.model.DealsState
import app.playlog.core.model.DiscoverSummary
import app.playlog.core.model.GameId
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Pick
import app.playlog.core.model.Screenshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class DayPart { MORNING, DAY, EVENING, NIGHT }

/** Weekly playtime target set on Home: something to reach, or with [isLimit] a ceiling to stay under. */
data class WeeklyGoal(val hours: Int, val isLimit: Boolean) {
    /** Fraction of the target played so far; above 1 when it is passed. */
    fun progress(played: Duration): Float = played.toMinutes().toFloat() / (hours * MINUTES_PER_HOUR)

    companion object {
        private const val MINUTES_PER_HOUR = 60

        fun of(settings: UserSettings): WeeklyGoal? =
            settings.weeklyGoalHours.takeIf { it > 0 }?.let { WeeklyGoal(it, settings.weeklyGoalIsLimit) }
    }
}

/** A recent capture with the title it belongs to, when the game is in the library. */
data class HomeShot(val screenshot: Screenshot, val title: String?)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Empty(val dayPart: DayPart) : HomeUiState
    data class Content(
        val dayPart: DayPart,
        val gameCount: Int,
        val totalPlaytime: Duration,
        /** Most recently played title; the hero card. */
        val continuePlaying: LibraryEntry?,
        /** Recently played after [continuePlaying], newest first. */
        val recent: List<LibraryEntry>,
        val week: ActivitySummary,
        val streakDays: Int,
        val backlogCount: Int,
        val topPick: Pick?,
        val screenshots: List<HomeShot>,
        /** Games on sale that match what the user plays; empty until the stores answer. */
        val deals: List<DealPick> = emptyList(),
        val goal: WeeklyGoal? = null,
    ) : HomeUiState

    companion object {
        const val RECENT_LIMIT = 10
        const val SHOTS_LIMIT = 8
        const val DEALS_LIMIT = 8

        fun build(
            library: List<LibraryEntry>,
            week: ActivitySummary,
            streakDays: Int,
            discover: DiscoverSummary,
            screenshots: List<Screenshot>,
            dayPart: DayPart,
            deals: DealsState = DealsState.Loading,
        ): HomeUiState {
            if (library.isEmpty()) return Empty(dayPart)
            val played = library.filter { it.lastPlayedAt != null }.sortedByDescending { it.lastPlayedAt }
            val titles: Map<GameId, String> = library.flatMap { e -> e.games.map { it.id to e.title } }.toMap()
            val continuePlaying = played.firstOrNull()
            return Content(
                dayPart = dayPart,
                gameCount = library.size,
                totalPlaytime = library.fold(Duration.ZERO) { acc, e -> acc + e.totalPlaytime },
                continuePlaying = continuePlaying,
                recent = played.drop(1).take(RECENT_LIMIT),
                week = week,
                streakDays = streakDays,
                backlogCount = discover.backlogCount,
                // Tonight's pick should be something other than the hero, or the two cards repeat each other.
                topPick = (listOfNotNull(discover.topPick) + discover.shelves.flatMap { it.picks })
                    .firstOrNull { it.entry.key != continuePlaying?.key },
                screenshots = screenshots
                    .sortedByDescending { it.takenAt }
                    .take(SHOTS_LIMIT)
                    .map { HomeShot(it, it.gameId?.let(titles::get)) },
                deals = when (deals) {
                    is DealsState.Ready -> deals.picks
                    is DealsState.Failed -> deals.stale
                    DealsState.Loading, DealsState.NoTaste -> emptyList()
                }.take(DEALS_LIMIT),
            )
        }

        fun dayPart(time: LocalTime): DayPart = when (time.hour) {
            in MORNING_FROM until DAY_FROM -> DayPart.MORNING
            in DAY_FROM until EVENING_FROM -> DayPart.DAY
            in EVENING_FROM until NIGHT_FROM -> DayPart.EVENING
            else -> DayPart.NIGHT
        }

        private const val MORNING_FROM = 5
        private const val DAY_FROM = 12
        private const val EVENING_FROM = 18
        private const val NIGHT_FROM = 23
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    library: LibraryRepository,
    activity: ActivityRepository,
    discover: DiscoverRepository,
    screenshots: ScreenshotRepository,
    deals: DealsRepository,
    private val settings: UserSettingsRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val today: LocalDate get() = clock.now().atZone(clock.zone()).toLocalDate()

    private val sources = combine(
        library.observeLibrary(),
        activity.observeSummary(ActivityPeriod.WEEK, today),
        activity.observeStreak(),
        discover.observeSummary(),
        screenshots.observeScreenshots(),
    ) { entries, week, streak, summary, shots -> Sources(entries, week, streak, summary, shots) }

    val uiState: StateFlow<HomeUiState> = combine(sources, deals.observe(), settings.settings) { s, dealsState, prefs ->
        val dayPart = HomeUiState.dayPart(clock.now().atZone(clock.zone()).toLocalTime())
        when (val state = HomeUiState.build(s.library, s.week, s.streak, s.discover, s.screenshots, dayPart, dealsState)) {
            is HomeUiState.Content -> state.copy(goal = WeeklyGoal.of(prefs))
            else -> state
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /** [hours] 0 removes the goal. */
    fun setGoal(hours: Int, isLimit: Boolean) {
        viewModelScope.launch { settings.update { it.copy(weeklyGoalHours = hours, weeklyGoalIsLimit = isLimit) } }
    }
}

private data class Sources(
    val library: List<LibraryEntry>,
    val week: ActivitySummary,
    val streak: Int,
    val discover: DiscoverSummary,
    val screenshots: List<Screenshot>,
)
