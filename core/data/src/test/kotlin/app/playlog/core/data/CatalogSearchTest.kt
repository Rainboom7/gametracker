package app.playlog.core.data

import app.playlog.core.model.DealPick
import app.playlog.core.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogSearchTest {
    private fun pick(platform: Platform, id: String, title: String) = DealPick(platform, id, title, "h", "u", discountPercent = 0, priceText = "")

    @Test
    fun `stores are interleaved by rank and the same title becomes one game`() {
        val steam = listOf(pick(Platform.STEAM, "1", "Hades"), pick(Platform.STEAM, "2", "Hades II"))
        val eshop = listOf(pick(Platform.NINTENDO, "7", "Hades™"), pick(Platform.NINTENDO, "8", "Hades: Deluxe Edition"))
        val ps = listOf(pick(Platform.PLAYSTATION, "p", "Hades II (PS5)"))

        val games = StoreCatalogSearch.merge(listOf(steam, ps, eshop), owned = setOf("hades"))

        assertEquals(listOf("Hades", "Hades II"), games.map { it.title })
        assertEquals(listOf(Platform.STEAM, Platform.NINTENDO), games[0].platforms)
        assertEquals(listOf(Platform.PLAYSTATION, Platform.STEAM), games[1].platforms)
        assertEquals(listOf(true, false), games.map { it.owned })
    }
}
