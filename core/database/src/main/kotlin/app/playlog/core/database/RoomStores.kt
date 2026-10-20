package app.playlog.core.database

import androidx.room.withTransaction
import app.playlog.core.data.AccountStore
import app.playlog.core.data.LibraryStore
import app.playlog.core.model.Account
import app.playlog.core.model.DailyPlaytime
import app.playlog.core.model.Game
import app.playlog.core.model.GameArtwork
import app.playlog.core.model.GameId
import app.playlog.core.model.GameStatus
import app.playlog.core.model.Platform
import app.playlog.core.model.PlaytimeSnapshot
import app.playlog.core.model.Screenshot
import app.playlog.core.model.ScreenshotOrigin
import app.playlog.core.model.SyncFailure
import app.playlog.core.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class RoomLibraryStore(private val db: PlaylogDatabase) : LibraryStore {
    private val games = db.games()
    private val activity = db.activity()
    private val screenshots = db.screenshots()

    override fun observeGames(): Flow<List<Game>> = games.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun upsertGames(platform: Platform, games: List<Game>) {
        db.withTransaction {
            val existing = this.games.byPlatform(platform.name).associateBy { it.externalId }
            this.games.upsert(
                games.map { g ->
                    val old = existing[g.id.externalId]
                    g.toEntity(
                        favorite = old?.isFavorite ?: g.isFavorite,
                        hidden = old?.isHidden ?: g.isHidden,
                        status = old?.status ?: g.status.name,
                    )
                },
            )
        }
    }

    override suspend fun setFavorite(id: GameId, favorite: Boolean) = games.setFavorite(id.platform.name, id.externalId, favorite)

    override suspend fun setHidden(id: GameId, hidden: Boolean) = games.setHidden(id.platform.name, id.externalId, hidden)

    override suspend fun setStatus(id: GameId, status: GameStatus) = games.setStatus(id.platform.name, id.externalId, status.name)

    override suspend fun latestTotals(platform: Platform): Map<GameId, Duration> =
        activity.latestSnapshots(platform.name).associate { GameId(platform, it.externalId) to Duration.ofSeconds(it.totalSeconds) }

    override suspend fun insertSnapshots(snapshots: List<PlaytimeSnapshot>) {
        if (snapshots.isEmpty()) return
        activity.insertSnapshots(
            snapshots.map {
                SnapshotEntity(
                    platform = it.gameId.platform.name,
                    externalId = it.gameId.externalId,
                    observedAt = it.observedAt.toEpochMilli(),
                    totalSeconds = it.totalPlaytime.seconds,
                )
            },
        )
    }

    override suspend fun addDerivedDaily(entries: List<DailyPlaytime>) {
        if (entries.isNotEmpty()) activity.addDerived(entries.map { it.toEntity(exact = false) })
    }

    override suspend fun replaceExactDaily(entries: List<DailyPlaytime>) {
        if (entries.isNotEmpty()) activity.putDaily(entries.map { it.toEntity(exact = true) })
    }

    override fun observeDaily(from: LocalDate, toInclusive: LocalDate): Flow<List<DailyPlaytime>> =
        activity.observeDaily(from.toEpochDay(), toInclusive.toEpochDay()).map { list ->
            list.map { DailyPlaytime(GameId(Platform.valueOf(it.platform), it.externalId), LocalDate.ofEpochDay(it.epochDay), Duration.ofSeconds(it.seconds)) }
        }

    override fun observeScreenshots(): Flow<List<Screenshot>> = screenshots.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun upsertScreenshots(screenshots: List<Screenshot>) {
        if (screenshots.isNotEmpty()) this.screenshots.upsert(screenshots.map { it.toEntity() })
    }

    override suspend fun deleteScreenshot(id: String) = screenshots.delete(id)

    override suspend fun deletePlatform(platform: Platform) {
        db.withTransaction {
            games.deletePlatform(platform.name)
            activity.deleteSnapshots(platform.name)
            activity.deleteDaily(platform.name)
            screenshots.deleteSynced(platform.name)
        }
    }
}

class RoomAccountStore(db: PlaylogDatabase) : AccountStore {
    private val dao = db.accounts()

    override fun observeAccounts(): Flow<List<Account>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun upsert(account: Account) {
        val lastSynced = account.lastSyncedAt ?: dao.get(account.platform.name)?.lastSyncedAt?.let(Instant::ofEpochMilli)
        dao.upsert(account.copy(lastSyncedAt = lastSynced).toEntity())
    }

    override suspend fun updateSyncState(platform: Platform, state: SyncState, syncedAt: Instant?) {
        val current = dao.get(platform.name) ?: return
        val next = current.toModel().let { it.copy(syncState = state, lastSyncedAt = syncedAt ?: it.lastSyncedAt) }
        dao.upsert(next.toEntity())
    }

    override suspend fun delete(platform: Platform) = dao.delete(platform.name)
}

internal fun GameEntity.toModel() = Game(
    id = GameId(Platform.valueOf(platform), externalId),
    title = title,
    artwork = GameArtwork(coverUrl = coverUrl, heroUrl = heroUrl, iconUrl = iconUrl, bannerUrl = bannerUrl),
    playtime = Duration.ofSeconds(playtimeSeconds),
    recentPlaytime = recentPlaytimeSeconds?.let(Duration::ofSeconds),
    firstPlayedAt = firstPlayedAt?.let(Instant::ofEpochMilli),
    lastPlayedAt = lastPlayedAt?.let(Instant::ofEpochMilli),
    playCount = playCount,
    achievementProgress = achievementProgress,
    devices = devices.split(',').filter { it.isNotBlank() }.toSet(),
    isFavorite = isFavorite,
    isHidden = isHidden,
    status = runCatching { GameStatus.valueOf(status) }.getOrDefault(GameStatus.NONE),
)

internal fun Game.toEntity(favorite: Boolean, hidden: Boolean, status: String) = GameEntity(
    platform = id.platform.name,
    externalId = id.externalId,
    title = title,
    coverUrl = artwork.coverUrl,
    heroUrl = artwork.heroUrl,
    iconUrl = artwork.iconUrl,
    bannerUrl = artwork.bannerUrl,
    playtimeSeconds = playtime.seconds,
    recentPlaytimeSeconds = recentPlaytime?.seconds,
    firstPlayedAt = firstPlayedAt?.toEpochMilli(),
    lastPlayedAt = lastPlayedAt?.toEpochMilli(),
    playCount = playCount,
    achievementProgress = achievementProgress,
    devices = devices.sorted().joinToString(","),
    isFavorite = favorite,
    isHidden = hidden,
    status = status,
)

internal fun DailyPlaytime.toEntity(exact: Boolean) = DailyEntity(
    platform = gameId.platform.name,
    externalId = gameId.externalId,
    epochDay = date.toEpochDay(),
    seconds = playtime.seconds,
    exact = exact,
)

internal fun ScreenshotEntity.toModel() = Screenshot(
    id = id,
    gameId = gameExternalId?.let { GameId(Platform.valueOf(platform), it) },
    platform = Platform.valueOf(platform),
    fullUrl = fullUrl,
    thumbnailUrl = thumbnailUrl,
    takenAt = takenAt?.let(Instant::ofEpochMilli),
    width = width,
    height = height,
    isVideo = isVideo,
    origin = ScreenshotOrigin.valueOf(origin),
)

internal fun Screenshot.toEntity() = ScreenshotEntity(
    id = id,
    platform = platform.name,
    gameExternalId = gameId?.externalId,
    fullUrl = fullUrl,
    thumbnailUrl = thumbnailUrl,
    takenAt = takenAt?.toEpochMilli(),
    width = width,
    height = height,
    isVideo = isVideo,
    origin = origin.name,
)

internal fun AccountEntity.toModel() = Account(
    platform = Platform.valueOf(platform),
    userId = userId,
    displayName = displayName,
    avatarUrl = avatarUrl,
    lastSyncedAt = lastSyncedAt?.let(Instant::ofEpochMilli),
    syncState = when (syncState) {
        "RUNNING" -> SyncState.Running
        "FAILED" -> SyncState.Failed(failure?.let { runCatching { SyncFailure.valueOf(it) }.getOrNull() } ?: SyncFailure.UNKNOWN, failureMessage)
        else -> SyncState.Idle
    },
)

internal fun Account.toEntity(): AccountEntity {
    val failed = syncState as? SyncState.Failed
    return AccountEntity(
        platform = platform.name,
        userId = userId,
        displayName = displayName,
        avatarUrl = avatarUrl,
        lastSyncedAt = lastSyncedAt?.toEpochMilli(),
        syncState = when (syncState) {
            SyncState.Idle -> "IDLE"
            SyncState.Running -> "RUNNING"
            is SyncState.Failed -> "FAILED"
        },
        failure = failed?.reason?.name,
        failureMessage = failed?.message,
    )
}
