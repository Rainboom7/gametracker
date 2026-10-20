package app.playlog.core.data

import app.playlog.core.model.Account
import app.playlog.core.model.DailyPlaytime
import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.Platform
import app.playlog.core.model.PlaytimeSnapshot
import app.playlog.core.model.Screenshot
import app.playlog.core.model.SyncState
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Persistence port for the library. `:core:database` implements it with Room; tests use
 * [InMemoryLibraryStore]. Keeping it an interface lets sync and stats logic stay plain JVM.
 */
interface LibraryStore {
    fun observeGames(): Flow<List<Game>>

    /**
     * Replaces the platform's games with [games], keeping user flags (favorite, hidden) of games
     * that already exist. Games missing from [games] are kept: platforms drop delisted titles.
     */
    suspend fun upsertGames(platform: Platform, games: List<Game>)

    suspend fun setFavorite(id: GameId, favorite: Boolean)

    suspend fun setHidden(id: GameId, hidden: Boolean)

    /** Most recent snapshot total per game of [platform]. */
    suspend fun latestTotals(platform: Platform): Map<GameId, Duration>

    suspend fun insertSnapshots(snapshots: List<PlaytimeSnapshot>)

    /**
     * Adds derived playtime (snapshot deltas) to the given days. Rows written by
     * [replaceExactDaily] win over derived ones for the same game and day.
     */
    suspend fun addDerivedDaily(entries: List<DailyPlaytime>)

    /** Stores exact per-day playtime reported by the platform, replacing what was there. */
    suspend fun replaceExactDaily(entries: List<DailyPlaytime>)

    fun observeDaily(from: LocalDate, toInclusive: LocalDate): Flow<List<DailyPlaytime>>

    fun observeScreenshots(): Flow<List<Screenshot>>

    /** Upserts synced screenshots by id. Sony and Nintendo expire captures, so old rows are kept. */
    suspend fun upsertScreenshots(screenshots: List<Screenshot>)

    suspend fun deleteScreenshot(id: String)

    /** Removes everything stored for [platform] (unlinking with "delete data"). */
    suspend fun deletePlatform(platform: Platform)
}

interface AccountStore {
    fun observeAccounts(): Flow<List<Account>>

    /** Inserts or replaces; a null `lastSyncedAt` keeps the stored value. */
    suspend fun upsert(account: Account)

    suspend fun updateSyncState(platform: Platform, state: SyncState, syncedAt: Instant? = null)

    suspend fun delete(platform: Platform)
}
