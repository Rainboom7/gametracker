package app.playlog.core.data

import app.playlog.core.model.Platform
import app.playlog.core.model.ReleasesState
import app.playlog.core.model.UpcomingGame
import app.playlog.core.testing.FixedClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

class ReleasesRepositoryTest {
    private val clock = FixedClock(Instant.parse("2026-10-21T12:00:00Z"))
    private val regions = InMemoryDealsRegionStore()
    private val stores = DefaultStoreDealsRepository(emptySet(), emptySet(), regions, DefaultLibraryRepository(InMemoryLibraryStore()), { "RU" })
    private val asked = mutableMapOf<Platform, String>()
    private var nintendoDown = false

    private fun game(platform: Platform, id: String) = UpcomingGame(platform, id, "Game $id", "h", "u", date = null, dateText = null, tags = emptyList())

    private val repository = DefaultReleasesRepository(
        sources = mapOf(
            Platform.STEAM to UpcomingSource { asked[Platform.STEAM] = it; listOf(game(Platform.STEAM, "1")) },
            Platform.NINTENDO to UpcomingSource {
                asked[Platform.NINTENDO] = it
                if (nintendoDown) throw IOException("offline")
                listOf(game(Platform.NINTENDO, "2"))
            },
        ),
        stores = stores,
        clock = clock,
    )

    private suspend fun ready() = repository.observe().first { it !is ReleasesState.Loading }

    @Test
    fun `merges platforms, each in its own store region`() = runTest {
        regions.set(Platform.STEAM, "KZ")
        regions.set(Platform.NINTENDO, "PL")

        val state = ready() as ReleasesState.Ready

        assertEquals(setOf("1", "2"), state.games.map { it.storeId }.toSet())
        assertEquals(mapOf(Platform.STEAM to "KZ", Platform.NINTENDO to "PL"), asked)
    }

    @Test
    fun `one store failing keeps its previous games and the rest`() = runTest {
        ready()
        nintendoDown = true

        repository.refresh()

        val state = repository.observe().first() as ReleasesState.Ready
        assertTrue(state.games.any { it.platform == Platform.NINTENDO })
        assertTrue(state.games.any { it.platform == Platform.STEAM })
    }
}
