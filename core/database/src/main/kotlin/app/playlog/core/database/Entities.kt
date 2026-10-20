package app.playlog.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Times are epoch milliseconds, durations seconds, dates epoch days: no TypeConverters needed. */
@Entity(tableName = "games", primaryKeys = ["platform", "externalId"])
data class GameEntity(
    val platform: String,
    val externalId: String,
    val title: String,
    val coverUrl: String?,
    val heroUrl: String?,
    val iconUrl: String?,
    val bannerUrl: String?,
    val playtimeSeconds: Long,
    val recentPlaytimeSeconds: Long?,
    val firstPlayedAt: Long?,
    val lastPlayedAt: Long?,
    val playCount: Int?,
    val achievementProgress: Int?,
    /** Comma separated, e.g. "PS4,PS5". */
    val devices: String,
    val isFavorite: Boolean,
    val isHidden: Boolean,
    /** [app.playlog.core.model.GameStatus] name. */
    val status: String,
)

@Entity(
    tableName = "playtime_snapshots",
    indices = [Index(value = ["platform", "externalId", "observedAt"])],
)
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platform: String,
    val externalId: String,
    val observedAt: Long,
    val totalSeconds: Long,
)

@Entity(
    tableName = "daily_playtime",
    primaryKeys = ["platform", "externalId", "epochDay"],
    indices = [Index(value = ["epochDay"])],
)
data class DailyEntity(
    val platform: String,
    val externalId: String,
    val epochDay: Long,
    val seconds: Long,
    /** Reported by the platform (true) or derived from snapshot deltas (false). */
    val exact: Boolean,
)

@Entity(tableName = "screenshots", indices = [Index(value = ["platform", "gameExternalId"])])
data class ScreenshotEntity(
    @PrimaryKey val id: String,
    val platform: String,
    val gameExternalId: String?,
    val fullUrl: String,
    val thumbnailUrl: String?,
    val takenAt: Long?,
    val width: Int?,
    val height: Int?,
    val isVideo: Boolean,
    val origin: String,
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val platform: String,
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val lastSyncedAt: Long?,
    /** IDLE, RUNNING or FAILED. */
    val syncState: String,
    val failure: String?,
    val failureMessage: String?,
)
