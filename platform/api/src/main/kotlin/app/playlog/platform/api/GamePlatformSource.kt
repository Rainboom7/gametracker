package app.playlog.platform.api

import app.playlog.core.model.DailyPlaytime
import app.playlog.core.model.Game
import app.playlog.core.model.Platform
import app.playlog.core.model.Screenshot
import java.time.LocalDate

/**
 * One gaming platform plugged into the tracker. Implementations live in `:platform:<name>` and are
 * registered in the app's DI graph; the rest of the app only talks to this interface.
 *
 * All calls use the credentials saved by [linker] and throw [PlatformException] on failure.
 */
interface GamePlatformSource {
    val platform: Platform
    val capabilities: Set<SourceCapability>
    val linker: AccountLinker

    /** Every game the account owns or has played, with lifetime playtime and artwork. */
    suspend fun fetchLibrary(): List<Game>

    /** User captures. Only called when [SourceCapability.SCREENSHOTS] is declared. */
    suspend fun fetchScreenshots(): List<Screenshot> = emptyList()

    /**
     * Exact per-day playtime starting at [from], when the platform reports it (Nintendo parental
     * controls). Only called when [SourceCapability.DAILY_PLAYTIME] is declared.
     */
    suspend fun fetchDailyPlaytime(from: LocalDate): List<DailyPlaytime> = emptyList()
}

enum class SourceCapability {
    /** Lifetime playtime per game. */
    TOTAL_PLAYTIME,

    /** Playtime over a recent window (Steam: last two weeks). */
    RECENT_PLAYTIME,

    /** Exact playtime per day. */
    DAILY_PLAYTIME,

    /** First / last played timestamps. */
    PLAY_DATES,
    SCREENSHOTS,
}
