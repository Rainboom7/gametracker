package app.playlog.core.model

import java.time.Instant
import java.time.LocalDate

/** A game that is not out yet. [date] is null when the store only gives a vague window ([dateText]). */
data class UpcomingGame(
    val platform: Platform,
    val storeId: String,
    val title: String,
    val headerUrl: String,
    val storeUrl: String,
    val date: LocalDate?,
    /** The store's own wording, e.g. "Q1 2027" or "Coming soon", for undated games. */
    val dateText: String? = null,
    /** Store user tags, most relevant first. */
    val tags: List<String> = emptyList(),
) {
    val key: String get() = "${platform.name}:$storeId"
}

sealed interface ReleasesState {
    data object Loading : ReleasesState

    data class Ready(val games: List<UpcomingGame>, val fetchedAt: Instant) : ReleasesState

    /** The store could not be reached; [stale] is the last good list, if any. */
    data class Failed(val stale: List<UpcomingGame>, val message: String?) : ReleasesState
}
