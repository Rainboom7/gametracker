package app.playlog.core.data

import app.playlog.core.common.AppClock
import app.playlog.core.model.Badge
import app.playlog.core.model.BadgeRecord
import app.playlog.core.model.GameNote
import app.playlog.core.model.Milestone
import app.playlog.core.model.MilestoneKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant

interface BadgeStore {
    fun observe(): Flow<Map<MilestoneKind, BadgeRecord>>

    suspend fun write(records: Map<MilestoneKind, BadgeRecord>)
}

class InMemoryBadgeStore : BadgeStore {
    private val state = MutableStateFlow<Map<MilestoneKind, BadgeRecord>>(emptyMap())

    override fun observe(): Flow<Map<MilestoneKind, BadgeRecord>> = state

    override suspend fun write(records: Map<MilestoneKind, BadgeRecord>) {
        state.value = records
    }
}

interface GameNotesStore {
    fun observe(): Flow<Map<String, GameNote>>

    /** A null or empty [note] removes the entry. */
    suspend fun put(key: String, note: GameNote?)
}

class InMemoryGameNotesStore : GameNotesStore {
    private val state = MutableStateFlow<Map<String, GameNote>>(emptyMap())

    override fun observe(): Flow<Map<String, GameNote>> = state

    override suspend fun put(key: String, note: GameNote?) = state.update { all ->
        if (note == null || note.isEmpty) all - key else all + (key to note)
    }
}

interface GameNotesRepository {
    fun observeAll(): Flow<Map<String, GameNote>>

    fun observe(key: String): Flow<GameNote?>

    suspend fun setRating(key: String, rating: Int?)

    suspend fun setText(key: String, text: String)
}

class DefaultGameNotesRepository(
    private val store: GameNotesStore,
    private val clock: AppClock,
) : GameNotesRepository {
    override fun observeAll() = store.observe()

    override fun observe(key: String): Flow<GameNote?> = store.observe().map { it[key] }.distinctUntilChanged()

    override suspend fun setRating(key: String, rating: Int?) {
        require(rating == null || rating in GameNote.RATING_RANGE) { "Rating out of range: $rating" }
        edit(key) { it.copy(rating = rating) }
    }

    override suspend fun setText(key: String, text: String) = edit(key) { it.copy(text = text.trim()) }

    private suspend fun edit(key: String, change: (GameNote) -> GameNote) {
        val current = store.observe().first()[key] ?: GameNote(updatedAt = clock.now())
        store.put(key, change(current).copy(updatedAt = clock.now()))
    }
}

interface BadgesRepository {
    /** Unlocked badges first (recent ones on top), then locked ones by progress. */
    fun observeBadges(): Flow<List<Badge>>

    /**
     * Records level-ups and returns the badges whose new level was not shown yet, marking them shown.
     * The very first run only remembers the current levels, so an existing library is not a burst of alerts.
     */
    suspend fun takeUnlocks(): List<Badge>

    /** The badges screen was opened: everything currently unlocked counts as seen. */
    suspend fun markSeen()
}

class DefaultBadgesRepository(
    private val discover: DiscoverRepository,
    private val screenshots: ScreenshotRepository,
    private val notes: GameNotesRepository,
    private val store: BadgeStore,
    private val clock: AppClock,
    private val recentFor: Duration = RECENT,
) : BadgesRepository {

    private fun milestones(): Flow<List<Milestone>> = combine(
        discover.observeSummary(),
        screenshots.observeScreenshots().map { it.size.toLong() }.distinctUntilChanged(),
        notes.observeAll().map { all -> all.values.count { it.rating != null }.toLong() }.distinctUntilChanged(),
    ) { summary, shots, rated ->
        val extra = mapOf(
            MilestoneKind.PHOTOGRAPHER to Milestone(MilestoneKind.PHOTOGRAPHER, shots, DiscoverCalculator.THRESHOLDS.getValue(MilestoneKind.PHOTOGRAPHER)),
            MilestoneKind.CRITIC to Milestone(MilestoneKind.CRITIC, rated, DiscoverCalculator.THRESHOLDS.getValue(MilestoneKind.CRITIC)),
        )
        summary.milestones.filter { it.kind !in extra } + extra.values
    }

    override fun observeBadges(): Flow<List<Badge>> = combine(milestones(), store.observe()) { milestones, records ->
        val now = clock.now()
        milestones.map { m ->
            val record = records[m.kind]
            val unlockedAt = record?.takeIf { it.level == m.level }?.unlockedAt
            val recent = m.level > 0 && records.isNotEmpty() &&
                (record == null || m.level > record.level || (unlockedAt != null && Duration.between(unlockedAt, now) < recentFor))
            Badge(m, unlockedAt, recent)
        }.sortedWith(ORDER)
    }

    override suspend fun takeUnlocks(): List<Badge> {
        val milestones = milestones().first()
        val records = store.observe().first()
        val updated = record(milestones, records, clock.now(), seen = records.isEmpty())
        val fresh = milestones.filter { m -> m.level > updated.getValue(m.kind).seenLevel }
        store.write(updated.mapValues { (_, r) -> r.copy(seenLevel = maxOf(r.seenLevel, r.level)) })
        return fresh.map { Badge(it, updated.getValue(it.kind).unlockedAt, isRecent = true) }
    }

    override suspend fun markSeen() {
        val updated = record(milestones().first(), store.observe().first(), clock.now(), seen = true)
        store.write(updated)
    }

    private fun record(
        milestones: List<Milestone>,
        records: Map<MilestoneKind, BadgeRecord>,
        now: Instant,
        seen: Boolean,
    ): Map<MilestoneKind, BadgeRecord> = records + milestones.associate { m ->
        val old = records[m.kind]
        val record = when {
            old == null -> BadgeRecord(m.level, unlockedAt = if (records.isEmpty()) null else now.takeIf { m.level > 0 }, seenLevel = if (seen) m.level else 0)
            m.level > old.level -> old.copy(level = m.level, unlockedAt = now)
            // A level can drop when a game leaves the library; keep the time of the level that remains.
            m.level < old.level -> old.copy(level = m.level, unlockedAt = null, seenLevel = minOf(old.seenLevel, m.level))
            else -> old
        }
        m.kind to if (seen) record.copy(seenLevel = maxOf(record.seenLevel, m.level)) else record
    }

    companion object {
        val RECENT: Duration = Duration.ofDays(7)

        private val ORDER = compareByDescending<Badge> { it.level > 0 }
            .thenByDescending { it.isRecent }
            .thenByDescending { it.unlockedAt }
            .thenByDescending { it.milestone.progressToNext }
    }
}
