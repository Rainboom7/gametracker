package app.playlog.core.data

import app.playlog.core.model.DealPick
import app.playlog.core.model.Platform
import app.playlog.core.model.StoreDealsPage
import app.playlog.core.model.StorePrice
import app.playlog.core.model.StoreQuote
import app.playlog.core.testing.FixedClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WishlistRepositoryTest {
    private val prices = mutableMapOf<String, StorePrice>()
    private val upcoming = mutableMapOf<String, Boolean>()
    private val store = object : StoreDealsSource {
        override val platform = Platform.STEAM
        override val countries = listOf("TR")
        override suspend fun page(country: String, page: Int) = StoreDealsPage(emptyList(), hasMore = false)
        override suspend fun quotes(country: String, ids: List<String>) = ids.associateWith { id ->
            StoreQuote(title = "Game $id", headerUrl = "h$id", storeUrl = "u$id", price = prices[id], comingSoon = upcoming[id])
        }.filterValues { it.price != null || it.comingSoon != null || it.title == "Game 9" }
    }
    private val account = FakeSource(Platform.STEAM, storeCountry = { "TR" }, wishlist = { listOf("1", "9") })
    private val stores = DefaultStoreDealsRepository(
        setOf(store),
        setOf(account),
        InMemoryDealsRegionStore(),
        DefaultLibraryRepository(InMemoryLibraryStore()),
        { "TR" },
    )
    private val repository = DefaultWishlistRepository(InMemoryWishlistStore(), stores, setOf(account), FixedClock())

    private fun pick(id: String, discount: Int) = DealPick(Platform.STEAM, id, "Game $id", "h", "u", discountPercent = discount, priceText = "10 TL")

    @Test
    fun `toggle adds and removes`() = runTest {
        repository.toggle(pick("1", 0))
        assertEquals(listOf("STEAM:1"), repository.observe().first().map { it.key })
        repository.toggle(pick("1", 0))
        assertTrue(repository.observe().first().isEmpty())
    }

    @Test
    fun `a sale notifies once, a deeper cut again, and resets when it ends`() = runTest {
        repository.toggle(pick("1", 0))

        prices["1"] = StorePrice("5 TL", "10 TL", 50)
        assertEquals(listOf(50), repository.checkPrices().map { it.price.discountPercent })
        assertTrue("same sale stays quiet", repository.checkPrices().isEmpty())

        prices["1"] = StorePrice("2 TL", "10 TL", 80)
        assertEquals(listOf(80), repository.checkPrices().map { it.price.discountPercent })

        prices["1"] = StorePrice("10 TL")
        assertTrue(repository.checkPrices().isEmpty())
        assertEquals(0, repository.observe().first().single().notifiedDiscount)

        prices["1"] = StorePrice("5 TL", "10 TL", 50)
        assertEquals(1, repository.checkPrices().size)
    }

    @Test
    fun `a wished game coming out is announced once, even before it has a price`() = runTest {
        repository.toggle(pick("3", 0))
        upcoming["3"] = true
        assertTrue(repository.check().released.isEmpty())
        assertTrue(repository.observe().first().single().comingSoon)

        upcoming["3"] = false
        assertEquals(listOf("STEAM:3"), repository.check().released.map { it.key })
        assertTrue(repository.check().released.isEmpty())
    }

    @Test
    fun `adding a game already on sale does not alert for that sale`() = runTest {
        repository.toggle(pick("1", 40))
        prices["1"] = StorePrice("6 TL", "10 TL", 40)
        assertTrue(repository.checkPrices().isEmpty())
    }

    @Test
    fun `imports the account wishlist with store names, skipping known games`() = runTest {
        prices["1"] = StorePrice("10 TL")
        repository.toggle(pick("9", 0))

        assertEquals(1, repository.importFromAccounts())

        val item = repository.observe().first().first { it.storeId == "1" }
        assertEquals("Game 1", item.title)
        assertEquals("u1", item.storeUrl)
        assertEquals("10 TL", item.price?.priceText)
    }
}
