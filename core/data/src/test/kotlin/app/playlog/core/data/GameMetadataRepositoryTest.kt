package app.playlog.core.data

import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.GameMetadata
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Platform
import app.playlog.core.testing.FixedClock
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.time.Duration

class GameMetadataRepositoryTest {
    private val clock = FixedClock()
    private val store = InMemoryMetadataStore()
    private val entry = LibraryEntry(
        "hades",
        listOf(
            Game(GameId(Platform.NINTENDO, "0100535012974000"), "Hades"),
            Game(GameId(Platform.STEAM, "1145360"), "Hades"),
        ),
    )
    private val queries = mutableListOf<MetadataQuery>()
    private var fail = false

    private val repository = DefaultGameMetadataRepository(
        source = { q ->
            queries += q
            if (fail) throw IOException("offline")
            GameMetadata(q.key, clock.now(), developer = "Supergiant")
        },
        store = store,
        clock = clock,
        ttl = Duration.ofDays(14),
    )

    @Test
    fun `fetches on a cache miss with the Steam appid`() = runTest {
        val emissions = repository.observe(entry).toList()

        assertEquals(listOf(null, GameMetadata("hades", clock.now(), developer = "Supergiant")), emissions)
        assertEquals(MetadataQuery("hades", "Hades", steamAppId = 1145360), queries.single())
        assertEquals("Supergiant", store.get("hades")?.developer)
    }

    @Test
    fun `fresh cache is served without a lookup`() = runTest {
        store.put(GameMetadata("hades", clock.now().minus(Duration.ofDays(3))))

        assertEquals(1, repository.observe(entry).toList().size)
        assertEquals(0, queries.size)
    }

    @Test
    fun `stale cache is shown first, then replaced`() = runTest {
        val old = GameMetadata("hades", clock.now().minus(Duration.ofDays(30)))
        store.put(old)

        val emissions = repository.observe(entry).toList()

        assertEquals(old, emissions.first())
        assertEquals("Supergiant", emissions.last()?.developer)
    }

    @Test
    fun `failures keep the old record and are not cached`() = runTest {
        fail = true
        assertEquals(listOf<GameMetadata?>(null), repository.observe(entry).toList())
        assertNull(store.get("hades"))

        fail = false
        assertEquals("Supergiant", repository.observe(entry).toList().last()?.developer)
    }
}
