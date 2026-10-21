package app.playlog.core.data

import app.playlog.core.model.DealPick
import app.playlog.core.model.DealsState
import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.GameStatus
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Platform
import app.playlog.core.testing.FixedClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DealsRepositoryTest {
    private val clock = FixedClock(Instant.parse("2026-10-21T12:00:00Z"))
    private val store = InMemoryLibraryStore()
    private val cache = InMemoryDealsCache()
    private val pick = DealPick(588650, "Dead Cells", "h.jpg", discountPercent = 60, priceText = "207 руб.")

    private var calls = 0
    private var fail = false
    private var lastSeeds: List<TasteSeed> = emptyList()
    private var lastOwned: Ownership? = null
    private val source = DealsSource { seeds, owned ->
        calls++
        lastSeeds = seeds
        lastOwned = owned
        if (fail) throw IOException("offline")
        listOf(pick)
    }
    private val repository = DefaultDealsRepository(source, DefaultLibraryRepository(store), cache, clock)

    private suspend fun seedLibrary() = store.upsertGames(
        Platform.STEAM,
        listOf(Game(GameId(Platform.STEAM, "1145360"), "Hades", playtime = Duration.ofHours(40), lastPlayedAt = clock.instant)),
    )

    private suspend fun ready() = repository.observe().first { it !is DealsState.Loading }

    @Test
    fun `computes picks from the library and caches them`() = runTest {
        seedLibrary()

        assertEquals(DealsState.Ready(listOf(pick), clock.instant), ready())
        assertEquals(listOf("Hades"), lastSeeds.map { it.title })
        assertEquals(setOf(1145360L), lastOwned?.steamAppIds)
        assertEquals(listOf(pick), cache.read()?.picks)
    }

    @Test
    fun `fresh cache is shown without recomputing`() = runTest {
        seedLibrary()
        cache.write(CachedDeals(listOf(pick), clock.instant.minus(Duration.ofHours(1))))

        assertEquals(DealsState.Ready(listOf(pick), clock.instant.minus(Duration.ofHours(1))), ready())
        assertEquals(0, calls)
    }

    @Test
    fun `failure keeps the last good picks`() = runTest {
        seedLibrary()
        cache.write(CachedDeals(listOf(pick), clock.instant.minus(Duration.ofDays(2))))
        fail = true

        repository.refresh()

        val state = repository.observe().first()
        assertTrue(state is DealsState.Failed)
        assertEquals(listOf(pick), (state as DealsState.Failed).stale)
    }

    @Test
    fun `empty library has no taste`() = runTest {
        assertEquals(DealsState.NoTaste, ready())
        assertEquals(0, calls)
    }

    @Test
    fun `seeds favour recent, long and favourite games and skip barely played ones`() {
        val today = LocalDate.parse("2026-10-21")
        fun g(id: String, title: String, hours: Long, daysAgo: Long, favorite: Boolean = false, status: GameStatus = GameStatus.NONE) =
            Game(
                GameId(Platform.NINTENDO, id),
                title,
                playtime = Duration.ofHours(hours),
                lastPlayedAt = today.minusDays(daysAgo).atStartOfDay(ZoneOffset.UTC).toInstant(),
                isFavorite = favorite,
                status = status,
            )
        val entries = LibraryEntry.group(
            listOf(
                g("1", "Old Epic", 100, 800),
                g("2", "Current", 30, 3),
                g("3", "Loved", 10, 30, favorite = true),
                g("4", "Dropped", 30, 3, status = GameStatus.DROPPED),
                g("5", "Tried", 0, 1),
            ),
        )

        val seeds = TasteProfile.seeds(entries, today, ZoneOffset.UTC)

        assertEquals(listOf("Loved", "Current", "Old Epic", "Dropped"), seeds.map { it.title })
        assertTrue(seeds.all { it.steamAppId == null })
    }
}
