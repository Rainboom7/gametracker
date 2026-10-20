package app.playlog.core.data

import app.playlog.core.model.Account
import app.playlog.core.model.DailyPlaytime
import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.GameStatus
import app.playlog.core.model.Platform
import app.playlog.core.model.PlaytimeSnapshot
import app.playlog.core.model.Screenshot
import app.playlog.core.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Thread-safe in-memory [LibraryStore] for tests, previews and the JVM CLI. */
class InMemoryLibraryStore : LibraryStore {
    private data class DailyKey(val gameId: GameId, val date: LocalDate)
    private data class DailyValue(val playtime: Duration, val exact: Boolean)

    private val games = MutableStateFlow<Map<GameId, Game>>(emptyMap())
    private val snapshots = MutableStateFlow<List<PlaytimeSnapshot>>(emptyList())
    private val daily = MutableStateFlow<Map<DailyKey, DailyValue>>(emptyMap())
    private val screenshots = MutableStateFlow<Map<String, Screenshot>>(emptyMap())

    val allSnapshots: List<PlaytimeSnapshot> get() = snapshots.value

    override fun observeGames(): Flow<List<Game>> = games.map { it.values.toList() }

    override suspend fun upsertGames(platform: Platform, games: List<Game>) {
        this.games.update { current ->
            current + games.associate { g ->
                val existing = current[g.id]
                g.id to g.copy(
                    isFavorite = existing?.isFavorite ?: g.isFavorite,
                    isHidden = existing?.isHidden ?: g.isHidden,
                    status = existing?.status ?: g.status,
                )
            }
        }
    }

    override suspend fun setFavorite(id: GameId, favorite: Boolean) {
        games.update { m -> m[id]?.let { m + (id to it.copy(isFavorite = favorite)) } ?: m }
    }

    override suspend fun setHidden(id: GameId, hidden: Boolean) {
        games.update { m -> m[id]?.let { m + (id to it.copy(isHidden = hidden)) } ?: m }
    }

    override suspend fun setStatus(id: GameId, status: GameStatus) {
        games.update { m -> m[id]?.let { m + (id to it.copy(status = status)) } ?: m }
    }

    override suspend fun latestTotals(platform: Platform): Map<GameId, Duration> =
        snapshots.value.filter { it.gameId.platform == platform }
            .groupBy { it.gameId }
            .mapValues { (_, list) -> list.maxBy { it.observedAt }.totalPlaytime }

    override suspend fun insertSnapshots(snapshots: List<PlaytimeSnapshot>) {
        this.snapshots.update { it + snapshots }
    }

    override suspend fun addDerivedDaily(entries: List<DailyPlaytime>) {
        daily.update { current ->
            val next = current.toMutableMap()
            for (e in entries) {
                val key = DailyKey(e.gameId, e.date)
                val existing = next[key]
                if (existing?.exact == true) continue
                next[key] = DailyValue((existing?.playtime ?: Duration.ZERO) + e.playtime, exact = false)
            }
            next
        }
    }

    override suspend fun replaceExactDaily(entries: List<DailyPlaytime>) {
        daily.update { current -> current + entries.associate { DailyKey(it.gameId, it.date) to DailyValue(it.playtime, exact = true) } }
    }

    override fun observeDaily(from: LocalDate, toInclusive: LocalDate): Flow<List<DailyPlaytime>> =
        daily.map { m ->
            m.filterKeys { !it.date.isBefore(from) && !it.date.isAfter(toInclusive) }
                .map { (k, v) -> DailyPlaytime(k.gameId, k.date, v.playtime) }
        }

    override fun observeScreenshots(): Flow<List<Screenshot>> = screenshots.map { it.values.toList() }

    override suspend fun upsertScreenshots(screenshots: List<Screenshot>) {
        this.screenshots.update { it + screenshots.associateBy { s -> s.id } }
    }

    override suspend fun deleteScreenshot(id: String) {
        screenshots.update { it - id }
    }

    override suspend fun deletePlatform(platform: Platform) {
        games.update { m -> m.filterKeys { it.platform != platform } }
        snapshots.update { l -> l.filter { it.gameId.platform != platform } }
        daily.update { m -> m.filterKeys { it.gameId.platform != platform } }
        screenshots.update { m -> m.filterValues { it.platform != platform } }
    }
}

class InMemoryAccountStore : AccountStore {
    private val accounts = MutableStateFlow<Map<Platform, Account>>(emptyMap())

    override fun observeAccounts(): Flow<List<Account>> = accounts.map { m -> m.values.sortedBy { it.platform.ordinal } }

    override suspend fun upsert(account: Account) {
        accounts.update { m ->
            val lastSynced = account.lastSyncedAt ?: m[account.platform]?.lastSyncedAt
            m + (account.platform to account.copy(lastSyncedAt = lastSynced))
        }
    }

    override suspend fun updateSyncState(platform: Platform, state: SyncState, syncedAt: Instant?) {
        accounts.update { m ->
            val a = m[platform] ?: return@update m
            m + (platform to a.copy(syncState = state, lastSyncedAt = syncedAt ?: a.lastSyncedAt))
        }
    }

    override suspend fun delete(platform: Platform) {
        accounts.update { it - platform }
    }
}
