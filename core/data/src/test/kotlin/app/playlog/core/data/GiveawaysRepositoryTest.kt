package app.playlog.core.data

import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.Giveaway
import app.playlog.core.model.GiveawayStore
import app.playlog.core.model.GiveawaysState
import app.playlog.core.model.Platform
import app.playlog.core.testing.FixedClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class GiveawaysRepositoryTest {
    private val clock = FixedClock()
    private var feed = listOf(
        Giveaway("a", "Celeste", null, "u", GiveawayStore.EPIC, endsAt = clock.instant.plus(Duration.ofDays(2))),
        Giveaway("b", "Next", null, "u", GiveawayStore.EPIC, upcoming = true),
        Giveaway("c", "Hades", null, "u", GiveawayStore.STEAM),
        Giveaway("d", "Over", null, "u", GiveawayStore.GOG, endsAt = clock.instant.minus(Duration.ofHours(1))),
    )
    private val library = InMemoryLibraryStore()
    private val repository = DefaultGiveawaysRepository({ feed }, InMemoryGiveawaysStore(), DefaultLibraryRepository(library), clock)

    @Test
    fun `new live giveaways are announced once, never owned or upcoming ones`() = runTest {
        library.upsertGames(Platform.STEAM, listOf(Game(GameId(Platform.STEAM, "1"), "Hades")))

        assertEquals(listOf("a"), repository.takeNew().map { it.id })
        assertTrue(repository.takeNew().isEmpty())

        feed = feed + Giveaway("e", "Tunic", null, "u", GiveawayStore.EPIC)
        assertEquals(listOf("e"), repository.takeNew().map { it.id })
    }

    @Test
    fun `observe drops ended offers, lists live before upcoming and flags owned games`() = runTest {
        library.upsertGames(Platform.STEAM, listOf(Game(GameId(Platform.STEAM, "1"), "Hades")))

        val ready = repository.observe().first { it is GiveawaysState.Ready } as GiveawaysState.Ready

        assertEquals(listOf("a", "c", "b"), ready.items.map { it.id })
        assertTrue(ready.items.first { it.id == "c" }.owned)
    }
}
