package app.playlog.core.data

import app.playlog.core.model.Platform
import app.playlog.core.model.PlayFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskParserTest {
    @Test
    fun `two players on one screen is split-screen co-op`() {
        val intent = AskParser.parse("игра на 2 на 1 экране")

        assertEquals(setOf(PlayFeature.SPLIT_SCREEN_COOP), intent.features)
        assertEquals(listOf("Local Co-Op"), intent.tags)
        assertEquals(2, intent.players)
        assertEquals("", intent.leftover)
    }

    @Test
    fun `players alone mean the same couch, online flips it`() {
        assertEquals(setOf(PlayFeature.SPLIT_SCREEN_COOP), AskParser.parse("во что поиграть вдвоем").features)

        val online = AskParser.parse("кооператив с другом по сети")
        assertEquals(setOf(PlayFeature.ONLINE_COOP), online.features)
        assertEquals(listOf("Online Co-Op"), online.tags)
    }

    @Test
    fun `versus on one screen is local pvp`() {
        val intent = AskParser.parse("файтинг 1 на 1 на одном экране")

        assertEquals(setOf(PlayFeature.LOCAL_PVP), intent.features)
        assertTrue("Fighting" in intent.tags)
    }

    @Test
    fun `genres and moods map to Steam tags in order`() {
        assertEquals(listOf("Cozy", "Wholesome", "Relaxing", "Short"), AskParser.parse("Уютная, спокойная игра на вечер").tags)
        assertEquals(listOf("Horror", "Co-op"), AskParser.parse("страшный хоррор с друзьями").tags.take(2))
    }

    @Test
    fun `like a title borrows its tags instead of parsing the name`() {
        val intent = AskParser.parse("рогалик как Hades")

        assertEquals("hades", intent.similarTo)
        assertEquals(listOf("Roguelike", "Roguelite"), intent.tags)
    }

    @Test
    fun `library, platform and price words`() {
        val intent = AskParser.parse("что-нибудь из моих не начатых на свитч")
        assertTrue(intent.ownedOnly)
        assertTrue(intent.unplayed)
        assertEquals(setOf(Platform.NINTENDO), intent.platforms)

        val cheap = AskParser.parse("бесплатный шутер")
        assertTrue(cheap.free)
        assertEquals(listOf("Shooter"), cheap.tags)
        assertTrue(AskParser.parse("стратегия со скидкой").onSale)
    }

    @Test
    fun `unknown words stay as leftover for a title search`() {
        val intent = AskParser.parse("Celeste")

        assertFalse(intent.understood)
        assertEquals("celeste", intent.leftover)
    }
}
