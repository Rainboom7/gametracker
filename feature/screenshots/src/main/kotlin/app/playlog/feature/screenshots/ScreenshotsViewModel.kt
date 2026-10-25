package app.playlog.feature.screenshots

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.playlog.core.data.LibraryRepository
import app.playlog.core.data.ScreenshotRepository
import app.playlog.core.data.UserSettingsRepository
import app.playlog.core.model.GameId
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Platform
import app.playlog.core.model.Screenshot
import app.playlog.core.model.ScreenshotOrigin
import app.playlog.core.common.AppClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class ScreenshotGroup(
    val date: LocalDate?,
    val entry: LibraryEntry?,
    val screenshots: List<Screenshot>,
)

data class ScreenshotsUiState(
    val groups: List<ScreenshotGroup> = emptyList(),
    /** Flat list in display order, for the full-screen pager. */
    val all: List<Screenshot> = emptyList(),
    val filterEntry: LibraryEntry? = null,
    val showNintendoBanner: Boolean = false,
    val isLoading: Boolean = true,
)

/** A picked file before it becomes a [Screenshot]. */
data class PickedCapture(val uri: String, val takenAt: Instant?, val width: Int? = null, val height: Int? = null)

@HiltViewModel
class ScreenshotsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val screenshots: ScreenshotRepository,
    library: LibraryRepository,
    private val settings: UserSettingsRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val filterGameId: GameId? = runCatching { savedStateHandle.toRoute<ScreenshotsDestination>().gameId }
        .getOrNull()?.let(GameId::decode)

    val uiState: StateFlow<ScreenshotsUiState> = combine(
        screenshots.observeScreenshots(),
        library.observeLibrary(),
        settings.settings,
    ) { shots, entries, prefs ->
        val entryByGame = entries.flatMap { e -> e.games.map { it.id to e } }.toMap()
        val filterEntry = filterGameId?.let { entryByGame[it] }
        val visible = if (filterEntry != null) shots.filter { s -> s.gameId != null && entryByGame[s.gameId] == filterEntry } else shots
        val groups = group(visible, entryByGame)
        ScreenshotsUiState(
            groups = groups,
            all = groups.flatMap { it.screenshots },
            filterEntry = filterEntry,
            showNintendoBanner = !prefs.nintendoImportBannerDismissed && shots.none { it.platform == Platform.NINTENDO },
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenshotsUiState())

    fun dismissNintendoBanner() {
        viewModelScope.launch { settings.update { it.copy(nintendoImportBannerDismissed = true) } }
    }

    fun import(captures: List<PickedCapture>, platform: Platform) {
        if (captures.isEmpty()) return
        val gameId = uiState.value.filterEntry?.games?.firstOrNull { it.id.platform == platform }?.id
        viewModelScope.launch {
            screenshots.addImported(
                captures.map {
                    Screenshot(
                        id = "import-${it.uri.hashCode().toUInt()}-${it.uri.length}",
                        gameId = gameId,
                        platform = platform,
                        fullUrl = it.uri,
                        takenAt = it.takenAt ?: clock.now(),
                        width = it.width,
                        height = it.height,
                        origin = ScreenshotOrigin.IMPORTED,
                    )
                },
            )
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { screenshots.delete(id) }
    }

    private fun group(shots: List<Screenshot>, entryByGame: Map<GameId, LibraryEntry>): List<ScreenshotGroup> {
        val zone = clock.zone()
        return shots
            .sortedByDescending { it.takenAt }
            .groupBy { s -> s.takenAt?.let { it.atZone(zone).toLocalDate() } to s.gameId?.let { entryByGame[it]?.key } }
            .map { (_, list) ->
                val first = list.first()
                ScreenshotGroup(
                    date = first.takenAt?.let { it.atZone(zone).toLocalDate() },
                    entry = first.gameId?.let { entryByGame[it] },
                    screenshots = list,
                )
            }
    }
}
