package app.playlog.core.data

import app.playlog.core.common.AppClock
import app.playlog.core.common.runSuspendCatching
import app.playlog.core.model.DailyPlaytime
import app.playlog.core.model.Game
import app.playlog.core.model.Platform
import app.playlog.core.model.PlaytimeSnapshot
import app.playlog.core.model.SyncFailure
import app.playlog.core.model.SyncState
import app.playlog.platform.api.GamePlatformSource
import app.playlog.platform.api.PlatformException
import app.playlog.platform.api.SourceCapability
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Pulls every linked platform into the local store. Platforms sync in parallel and fail
 * independently: an expired PSN token never blocks Steam.
 *
 * Activity history: on each sync the lifetime total of every game is compared with the previous
 * snapshot, and the difference is booked on the day the game was last played (or today). The very
 * first sync only records a baseline. Platforms with exact daily data overwrite the derived rows.
 */
class SyncEngine(
    sources: Set<GamePlatformSource>,
    private val library: LibraryStore,
    private val accounts: AccountStore,
    private val clock: AppClock,
    private val dailyLookbackDays: Long = 30,
) {
    private val sources = sources.associateBy { it.platform }
    private val locks = Platform.entries.associateWith { Mutex() }

    val platforms: Set<Platform> get() = sources.keys

    suspend fun syncAll(): SyncReport = coroutineScope {
        val results = sources.keys.map { platform -> async { sync(platform) } }.awaitAll()
        SyncReport(results.filterNotNull())
    }

    /** Returns null when [platform] has no linked account. */
    suspend fun sync(platform: Platform): PlatformSyncResult? {
        val source = sources[platform] ?: return null
        return locks.getValue(platform).withLock {
            val account = source.linker.current() ?: return@withLock null
            accounts.upsert(account.copy(syncState = SyncState.Running))
            val started = clock.now()
            try {
                val result = syncSource(source, started)
                accounts.updateSyncState(platform, SyncState.Idle, syncedAt = started)
                result
            } catch (e: PlatformException) {
                accounts.updateSyncState(platform, SyncState.Failed(e.toFailure(), e.message))
                PlatformSyncResult(platform, error = e)
            }
        }
    }

    private suspend fun syncSource(source: GamePlatformSource, now: Instant): PlatformSyncResult {
        val platform = source.platform
        val games = source.fetchLibrary()
        val previous = library.latestTotals(platform)
        val today = now.atZone(clock.zone()).toLocalDate()
        val hasExactDaily = SourceCapability.DAILY_PLAYTIME in source.capabilities

        val snapshots = mutableListOf<PlaytimeSnapshot>()
        val derived = mutableListOf<DailyPlaytime>()
        for (game in games) {
            val before = previous[game.id]
            if (before == game.playtime) continue
            snapshots += PlaytimeSnapshot(game.id, now, game.playtime)
            // No baseline yet (first sync, or a game new to the library): don't book its lifetime on today.
            if (before == null || hasExactDaily) continue
            val delta = game.playtime - before
            if (delta.isNegative || delta.isZero) continue
            derived += DailyPlaytime(game.id, attributionDay(game, today), delta)
        }

        library.upsertGames(platform, games)
        library.insertSnapshots(snapshots)
        library.addDerivedDaily(derived)

        val warnings = mutableListOf<PlatformException>()
        if (hasExactDaily) {
            runSuspendCatching { source.fetchDailyPlaytime(today.minusDays(dailyLookbackDays)) }
                .onSuccess { library.replaceExactDaily(it) }
                .onFailure { warnings += it.asPlatformException(platform) }
        }
        var screenshotCount = 0
        if (SourceCapability.SCREENSHOTS in source.capabilities) {
            // Captures are a bonus: a broken gallery endpoint must not fail the whole sync.
            runSuspendCatching { source.fetchScreenshots() }
                .onSuccess {
                    library.upsertScreenshots(it)
                    screenshotCount = it.size
                }
                .onFailure { warnings += it.asPlatformException(platform) }
        }
        return PlatformSyncResult(
            platform = platform,
            gameCount = games.size,
            changedGames = snapshots.size,
            playtimeAdded = derived.fold(Duration.ZERO) { acc, d -> acc + d.playtime },
            screenshotCount = screenshotCount,
            warnings = warnings,
        )
    }

    /** The day the play most likely happened: the game's last-played date if it is recent, else today. */
    private fun attributionDay(game: Game, today: LocalDate): LocalDate {
        val lastPlayed = game.lastPlayedAt?.let { it.atZone(clock.zone()).toLocalDate() } ?: return today
        if (lastPlayed.isAfter(today)) return today
        if (lastPlayed.isBefore(today.minusDays(MAX_ATTRIBUTION_LAG_DAYS))) return today
        return lastPlayed
    }

    private fun Throwable.asPlatformException(platform: Platform): PlatformException =
        this as? PlatformException ?: PlatformException.Protocol(platform, message ?: javaClass.simpleName, this)

    private fun PlatformException.toFailure(): SyncFailure = when (this) {
        is PlatformException.AuthExpired, is PlatformException.NotLinked -> SyncFailure.AUTH_EXPIRED
        is PlatformException.Network -> SyncFailure.NETWORK
        is PlatformException.RateLimited -> SyncFailure.RATE_LIMITED
        is PlatformException.PrivateProfile -> SyncFailure.PRIVATE_PROFILE
        is PlatformException.InvalidInput, is PlatformException.Protocol -> SyncFailure.UNKNOWN
    }

    private companion object {
        const val MAX_ATTRIBUTION_LAG_DAYS = 7L
    }
}

data class PlatformSyncResult(
    val platform: Platform,
    val gameCount: Int = 0,
    val changedGames: Int = 0,
    val playtimeAdded: Duration = Duration.ZERO,
    val screenshotCount: Int = 0,
    /** Non-fatal problems (screenshots or daily data failed). */
    val warnings: List<PlatformException> = emptyList(),
    /** Set when the platform failed as a whole. */
    val error: PlatformException? = null,
) {
    val isSuccess: Boolean get() = error == null
}

data class SyncReport(val results: List<PlatformSyncResult>) {
    val failed: List<PlatformSyncResult> get() = results.filterNot { it.isSuccess }
    val needsRelink: List<Platform>
        get() = failed.filter { it.error is PlatformException.AuthExpired || it.error is PlatformException.NotLinked }.map { it.platform }
}
