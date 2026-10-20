package app.playlog.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE platform = :platform")
    suspend fun byPlatform(platform: String): List<GameEntity>

    @Upsert
    suspend fun upsert(games: List<GameEntity>)

    @Query("UPDATE games SET isFavorite = :favorite WHERE platform = :platform AND externalId = :externalId")
    suspend fun setFavorite(platform: String, externalId: String, favorite: Boolean)

    @Query("UPDATE games SET isHidden = :hidden WHERE platform = :platform AND externalId = :externalId")
    suspend fun setHidden(platform: String, externalId: String, hidden: Boolean)

    @Query("UPDATE games SET status = :status WHERE platform = :platform AND externalId = :externalId")
    suspend fun setStatus(platform: String, externalId: String, status: String)

    @Query("DELETE FROM games WHERE platform = :platform")
    suspend fun deletePlatform(platform: String)
}

@Dao
interface ActivityDao {
    @Query(
        """
        SELECT s.* FROM playtime_snapshots s
        INNER JOIN (
            SELECT externalId, MAX(observedAt) AS latest FROM playtime_snapshots
            WHERE platform = :platform GROUP BY externalId
        ) l ON s.externalId = l.externalId AND s.observedAt = l.latest
        WHERE s.platform = :platform
        """,
    )
    suspend fun latestSnapshots(platform: String): List<SnapshotEntity>

    @Insert
    suspend fun insertSnapshots(snapshots: List<SnapshotEntity>)

    @Query("SELECT * FROM daily_playtime WHERE platform = :platform AND externalId = :externalId AND epochDay = :epochDay")
    suspend fun daily(platform: String, externalId: String, epochDay: Long): DailyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDaily(entries: List<DailyEntity>)

    /** Derived rows accumulate; exact rows already present are left alone. */
    @Transaction
    suspend fun addDerived(entries: List<DailyEntity>) {
        val merged = entries.mapNotNull { e ->
            val existing = daily(e.platform, e.externalId, e.epochDay)
            when {
                existing == null -> e
                existing.exact -> null
                else -> existing.copy(seconds = existing.seconds + e.seconds)
            }
        }
        putDaily(merged)
    }

    @Query("SELECT * FROM daily_playtime WHERE epochDay BETWEEN :fromDay AND :toDay")
    fun observeDaily(fromDay: Long, toDay: Long): Flow<List<DailyEntity>>

    @Query("DELETE FROM playtime_snapshots WHERE platform = :platform")
    suspend fun deleteSnapshots(platform: String)

    @Query("DELETE FROM daily_playtime WHERE platform = :platform")
    suspend fun deleteDaily(platform: String)
}

@Dao
interface ScreenshotDao {
    @Query("SELECT * FROM screenshots")
    fun observeAll(): Flow<List<ScreenshotEntity>>

    @Upsert
    suspend fun upsert(screenshots: List<ScreenshotEntity>)

    @Query("DELETE FROM screenshots WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM screenshots WHERE platform = :platform AND origin = 'SYNCED'")
    suspend fun deleteSynced(platform: String)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY platform")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE platform = :platform")
    suspend fun get(platform: String): AccountEntity?

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE platform = :platform")
    suspend fun delete(platform: String)
}
