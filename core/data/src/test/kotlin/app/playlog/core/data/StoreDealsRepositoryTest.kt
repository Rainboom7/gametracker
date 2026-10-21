package app.playlog.core.data

import app.playlog.core.model.DealPick
import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.Platform
import app.playlog.core.model.RegionSource
import app.playlog.core.model.StoreDealsPage
import app.playlog.core.model.StoreDealsState
import app.playlog.core.model.StoreRegion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class StoreDealsRepositoryTest {
    private fun deal(n: Int) = DealPick(Platform.NINTENDO, "$n", "Game $n", "h", "u", discountPercent = 50, priceText = "1")

    private val requested = mutableListOf<Pair<String, Int>>()
    private var fail = false
    private val store = object : StoreDealsSource {
        override val platform = Platform.NINTENDO
        override val countries = listOf("PL", "US")
        override suspend fun page(country: String, page: Int): StoreDealsPage {
            requested += country to page
            if (fail) throw IOException("offline")
            return StoreDealsPage(listOf(deal(page * 2), deal(page * 2 + 1)), hasMore = page == 0)
        }
    }
    private var accountCountry: String? = "PL"
    private val account = FakeSource(Platform.NINTENDO, storeCountry = { accountCountry })
    private val libraryStore = InMemoryLibraryStore()
    private val regions = InMemoryDealsRegionStore()

    private fun repository(device: String = "RU") =
        DefaultStoreDealsRepository(setOf(store), setOf(account), regions, DefaultLibraryRepository(libraryStore), { device })

    private suspend fun DefaultStoreDealsRepository.ready() = observe(Platform.NINTENDO).first { it !is StoreDealsState.Loading }

    @Test
    fun `uses the account region and pages`() = runTest {
        val repo = repository()

        val first = repo.ready() as StoreDealsState.Ready
        assertEquals(StoreRegion("PL", RegionSource.ACCOUNT), first.region)
        assertTrue(first.hasMore)

        repo.loadMore(Platform.NINTENDO)
        val second = repo.observe(Platform.NINTENDO).first() as StoreDealsState.Ready
        assertEquals(listOf("0", "1", "2", "3"), second.deals.map { it.storeId })
        assertEquals(listOf("PL" to 0, "PL" to 1), requested)
    }

    @Test
    fun `manual region wins and a region without a store is unsupported`() = runTest {
        val repo = repository()
        repo.setRegion(Platform.NINTENDO, "US")
        assertEquals(StoreRegion("US", RegionSource.MANUAL), (repo.ready() as StoreDealsState.Ready).region)

        repo.setRegion(Platform.NINTENDO, null)
        accountCountry = null
        val fresh = repository(device = "ru")
        assertEquals(StoreDealsState.Unsupported(StoreRegion("RU", RegionSource.DEVICE)), fresh.ready())
    }

    @Test
    fun `owned games are left out and failures are reported`() = runTest {
        libraryStore.upsertGames(Platform.STEAM, listOf(Game(GameId(Platform.STEAM, "1"), "Game 1")))
        val repo = repository()
        assertEquals(listOf("0"), (repo.ready() as StoreDealsState.Ready).deals.map { it.storeId })

        fail = true
        repo.refresh(Platform.NINTENDO)
        assertTrue(repo.observe(Platform.NINTENDO).first() is StoreDealsState.Failed)
    }
}
