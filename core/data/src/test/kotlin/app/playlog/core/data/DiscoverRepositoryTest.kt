package app.playlog.core.data

import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.PickReason
import app.playlog.core.model.Platform
import app.playlog.core.testing.FixedClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

class DiscoverRepositoryTest {
    private val clock = FixedClock(Instant.parse("2026-10-21T12:00:00Z"))
    private val store = InMemoryLibraryStore()
    private val repository = DefaultDiscoverRepository(store, DefaultLibraryRepository(store), clock, Random(7))

    @Test
    fun summaryAndRouletteReadTheStoredLibrary() = runTest {
        store.upsertGames(
            Platform.STEAM,
            listOf(
                Game(GameId(Platform.STEAM, "1"), "Hades", playtime = Duration.ofHours(3), lastPlayedAt = clock.instant.minusSeconds(86_400)),
                Game(GameId(Platform.STEAM, "2"), "Disco Elysium"),
            ),
        )

        val summary = repository.observeSummary().first()

        assertEquals(listOf(PickReason.CONTINUE, PickReason.BACKLOG), summary.shelves.map { it.reason })
        assertEquals("Disco Elysium", repository.roulette(null)?.title)
        assertEquals(null, repository.roulette(Platform.NINTENDO))
    }
}
