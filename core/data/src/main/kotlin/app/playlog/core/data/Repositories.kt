package app.playlog.core.data

import app.playlog.core.common.AppClock
import app.playlog.core.model.Account
import app.playlog.core.model.ActivityBucket
import app.playlog.core.model.ActivityPeriod
import app.playlog.core.model.ActivitySummary
import app.playlog.core.model.GameId
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Platform
import app.playlog.core.model.ProfileStats
import app.playlog.core.model.Screenshot
import app.playlog.platform.api.GamePlatformSource
import app.playlog.platform.api.LinkFieldType
import app.playlog.platform.api.LinkFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDate

interface LibraryRepository {
    fun observeLibrary(): Flow<List<LibraryEntry>>

    /** The merged entry that contains [id]. */
    fun observeEntry(id: GameId): Flow<LibraryEntry?>

    fun observeProfileStats(): Flow<ProfileStats>

    suspend fun setFavorite(entry: LibraryEntry, favorite: Boolean)

    suspend fun setHidden(entry: LibraryEntry, hidden: Boolean)
}

interface ActivityRepository {
    fun observeSummary(period: ActivityPeriod, anchor: LocalDate): Flow<ActivitySummary>

    /** Daily buckets for the last [days] days of one merged title. */
    fun observeEntryDays(entry: LibraryEntry, days: Int = 14): Flow<List<ActivityBucket>>

    fun observeStreak(): Flow<Int>
}

interface ScreenshotRepository {
    fun observeScreenshots(): Flow<List<Screenshot>>

    fun observeForEntry(entry: LibraryEntry): Flow<List<Screenshot>>

    /** Stores manual imports (photo picker / SAF); the Android layer builds the [Screenshot]s. */
    suspend fun addImported(screenshots: List<Screenshot>)

    suspend fun delete(id: String)
}

interface AccountRepository {
    fun observeAccounts(): Flow<List<Account>>

    /** Platforms the build supports, linked or not. */
    val availablePlatforms: Set<Platform>

    suspend fun startLink(platform: Platform): LinkFlow

    suspend fun completeLink(platform: Platform, flow: LinkFlow, input: Map<LinkFieldType, String>): Account

    suspend fun unlink(platform: Platform, deleteData: Boolean)
}

/** Background sync trigger; WorkManager on Android. */
interface SyncScheduler {
    val isSyncing: Flow<Boolean>

    /** Enqueues a sync of [platform], or of everything when null. */
    fun requestSync(platform: Platform? = null)

    fun setPeriodicSync(enabled: Boolean)
}

class DefaultLibraryRepository(private val store: LibraryStore) : LibraryRepository {
    private val library = store.observeGames().map(LibraryEntry::group).distinctUntilChanged()

    override fun observeLibrary(): Flow<List<LibraryEntry>> = library

    override fun observeEntry(id: GameId): Flow<LibraryEntry?> =
        library.map { entries -> entries.firstOrNull { e -> e.games.any { it.id == id } } }.distinctUntilChanged()

    override fun observeProfileStats(): Flow<ProfileStats> = library.map { entries ->
        ProfileStats(
            gameCount = entries.size,
            totalPlaytime = entries.fold(Duration.ZERO) { acc, e -> acc + e.totalPlaytime },
            completedCount = entries.count { it.achievementProgress == 100 },
        )
    }

    override suspend fun setFavorite(entry: LibraryEntry, favorite: Boolean) {
        entry.games.forEach { store.setFavorite(it.id, favorite) }
    }

    override suspend fun setHidden(entry: LibraryEntry, hidden: Boolean) {
        entry.games.forEach { store.setHidden(it.id, hidden) }
    }
}

class DefaultActivityRepository(
    private val store: LibraryStore,
    private val library: LibraryRepository,
    private val clock: AppClock,
) : ActivityRepository {

    override fun observeSummary(period: ActivityPeriod, anchor: LocalDate): Flow<ActivitySummary> {
        val from = ActivityCalculator.range(period, ActivityCalculator.previousAnchor(period, anchor)).start
        val to = ActivityCalculator.range(period, anchor).endInclusive
        return combine(store.observeDaily(from, to), library.observeLibrary()) { daily, entries ->
            ActivityCalculator.summarize(period, anchor, daily, entries)
        }
    }

    override fun observeEntryDays(entry: LibraryEntry, days: Int): Flow<List<ActivityBucket>> {
        val today = today()
        val ids = entry.games.mapTo(HashSet()) { it.id }
        return store.observeDaily(today.minusDays(days - 1L), today).map { daily ->
            ActivityCalculator.lastDays(daily.filter { it.gameId in ids }, today, days)
        }
    }

    override fun observeStreak(): Flow<Int> {
        val today = today()
        return store.observeDaily(today.minusDays(STREAK_WINDOW_DAYS), today).map { ActivityCalculator.currentStreak(it, today) }
    }

    private fun today() = LocalDate.ofInstant(clock.now(), clock.zone())

    private companion object {
        const val STREAK_WINDOW_DAYS = 366L
    }
}

class DefaultScreenshotRepository(private val store: LibraryStore) : ScreenshotRepository {
    override fun observeScreenshots(): Flow<List<Screenshot>> =
        store.observeScreenshots().map { list -> list.sortedByDescending { it.takenAt } }

    override fun observeForEntry(entry: LibraryEntry): Flow<List<Screenshot>> {
        val ids = entry.games.mapTo(HashSet()) { it.id }
        return observeScreenshots().map { list -> list.filter { it.gameId in ids } }
    }

    override suspend fun addImported(screenshots: List<Screenshot>) = store.upsertScreenshots(screenshots)

    override suspend fun delete(id: String) = store.deleteScreenshot(id)
}

class DefaultAccountRepository(
    sources: Set<GamePlatformSource>,
    private val accounts: AccountStore,
    private val library: LibraryStore,
) : AccountRepository {
    private val sources = sources.associateBy { it.platform }

    override val availablePlatforms: Set<Platform> = this.sources.keys

    override fun observeAccounts(): Flow<List<Account>> = accounts.observeAccounts()

    override suspend fun startLink(platform: Platform): LinkFlow = source(platform).linker.start()

    override suspend fun completeLink(platform: Platform, flow: LinkFlow, input: Map<LinkFieldType, String>): Account {
        val account = source(platform).linker.complete(flow, input)
        accounts.upsert(account)
        return account
    }

    override suspend fun unlink(platform: Platform, deleteData: Boolean) {
        source(platform).linker.unlink()
        accounts.delete(platform)
        if (deleteData) library.deletePlatform(platform)
    }

    private fun source(platform: Platform) = sources[platform] ?: error("No source for $platform")
}
