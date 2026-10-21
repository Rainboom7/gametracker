package app.playlog.core.data

import app.playlog.core.model.DealPick
import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Platform
import app.playlog.core.model.PlayFeature
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class AskEngineTest {
    private fun pick(id: Long, title: String, review: Int? = null, tags: List<String> = emptyList()) = DealPick(
        platform = Platform.STEAM,
        storeId = id.toString(),
        title = title,
        headerUrl = "",
        storeUrl = DealPick.steamStoreUrl(id),
        discountPercent = 0,
        priceText = "",
        reviewPercent = review,
        tags = tags,
    )

    private val library = LibraryEntry.group(
        listOf(
            Game(GameId(Platform.STEAM, "1426210"), "It Takes Two", playtime = Duration.ofHours(12)),
            Game(GameId(Platform.NINTENDO, "0100F8F0000A2000"), "Overcooked! 2"),
            Game(GameId(Platform.PLAYSTATION, "concept:9"), "Unravel Two", playtime = Duration.ofHours(6)),
        ),
    )

    private val found = listOf(
        pick(1426210, "It Takes Two"),
        pick(728880, "Overcooked! 2"),
        pick(1222700, "A Way Out"),
        pick(1092790, "Inscryption"),
    )

    @Test
    fun `store hits split into owned on any platform and the rest`() {
        val answer = AskEngine.answer(AskParser.parse("на двоих на одном экране"), emptyList(), library, found, emptyMap())

        assertEquals(listOf("It Takes Two", "Overcooked! 2"), answer.owned.map { it.title })
        assertEquals(listOf("A Way Out", "Inscryption"), answer.store.map { it.title })
    }

    @Test
    fun `unplayed and platform filters narrow the owned list and drop the store`() {
        val answer = AskEngine.answer(AskParser.parse("из моих не начатых на свитч"), emptyList(), library, found, emptyMap())

        assertEquals(listOf("Overcooked! 2"), answer.owned.map { it.title })
        assertEquals(emptyList<DealPick>(), answer.store)
    }

    @Test
    fun `query keeps the strongest tags and sends play modes as categories`() {
        val query = AskEngine.storeQuery(AskParser.parse("уютная игра на двоих на одном экране"), listOf("Cooking"), "RU")

        assertEquals(listOf("Cozy", "Wholesome", "Local Co-Op"), query.tags)
        assertEquals(setOf(PlayFeature.SPLIT_SCREEN_COOP), query.features)
        assertEquals("", query.text)

        assertEquals("celeste", AskEngine.storeQuery(AskParser.parse("Celeste"), emptyList(), "RU").text)
    }

    @Test
    fun `taste and reviews reorder near ties and fill shared tags`() {
        val ranked = AskEngine.rank(
            listOf(pick(1, "Plain", review = 60, tags = listOf("Sports")), pick(2, "Fits", review = 95, tags = listOf("Roguelike", "Action"))),
            mapOf("Roguelike" to 1.0, "Action" to 0.5),
        )

        assertEquals(listOf("Fits", "Plain"), ranked.map { it.title })
        assertEquals(listOf("Roguelike", "Action"), ranked.first().sharedTags)
    }
}
