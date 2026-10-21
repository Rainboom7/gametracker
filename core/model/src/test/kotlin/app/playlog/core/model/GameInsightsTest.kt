package app.playlog.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class GameInsightsTest {
    private val hltb = HowLongToBeat(
        id = 7,
        title = "Elden Ring",
        mainStory = Duration.ofHours(60),
        mainExtra = Duration.ofHours(100),
        completionist = Duration.ofHours(120),
    )

    @Test
    fun `progress against the longest estimate with story time left`() {
        val p = hltb.progressFor(Duration.ofHours(30))!!

        assertEquals(0.25f, p.fraction, 0.001f)
        assertEquals(listOf(0.5f, 100f / 120f, 1f), p.marks)
        assertEquals(BeatProgress.Stage.STARTED, p.stage)
        assertEquals(Duration.ofHours(30), p.leftForStory)
    }

    @Test
    fun `stages follow the estimates and the bar caps at full`() {
        assertEquals(BeatProgress.Stage.STORY_DONE, hltb.progressFor(Duration.ofHours(60))!!.stage)
        assertEquals(BeatProgress.Stage.EXTRAS_DONE, hltb.progressFor(Duration.ofHours(101))!!.stage)
        val beyond = hltb.progressFor(Duration.ofHours(142))!!
        assertEquals(BeatProgress.Stage.COMPLETIONIST, beyond.stage)
        assertEquals(1f, beyond.fraction)
        assertNull(beyond.leftForStory)
    }

    @Test
    fun `no estimates means no progress`() {
        assertNull(HowLongToBeat(1, "Unknown").progressFor(Duration.ofHours(3)))
    }

    @Test
    fun `links cover each owned store, then HowLongToBeat and Metacritic`() {
        val entry = LibraryEntry(
            "elden ring",
            listOf(
                Game(GameId(Platform.PLAYSTATION, "concept:10001130"), "ELDEN RING"),
                Game(GameId(Platform.STEAM, "1245620"), "ELDEN RING"),
                Game(GameId(Platform.NINTENDO, "0100F2C0115B6000"), "ELDEN RING"),
            ),
        )
        val metadata = GameMetadata(
            "elden ring",
            Instant.EPOCH,
            howLongToBeat = hltb,
            criticScore = CriticScore(94, "https://www.metacritic.com/game/pc/elden-ring?ftag=MCD"),
        )

        assertEquals(
            listOf(
                ExternalLink(LinkKind.STEAM_STORE, "https://store.steampowered.com/app/1245620"),
                ExternalLink(LinkKind.PLAYSTATION_STORE, "https://store.playstation.com/concept/10001130"),
                ExternalLink(LinkKind.NINTENDO_ESHOP, "https://ec.nintendo.com/apps/0100F2C0115B6000/US"),
                ExternalLink(LinkKind.HOWLONGTOBEAT, "https://howlongtobeat.com/game/7"),
                ExternalLink(LinkKind.METACRITIC, "https://www.metacritic.com/game/pc/elden-ring"),
            ),
            entry.externalLinks(metadata),
        )
    }

    @Test
    fun `without metadata the integrations fall back to search`() {
        val entry = LibraryEntry("astro bot", listOf(Game(GameId(Platform.PLAYSTATION, "PPSA01234_00"), "ASTRO BOT")))

        assertEquals(
            listOf(
                "https://store.playstation.com/search/ASTRO%20BOT",
                "https://howlongtobeat.com/?q=ASTRO%20BOT",
                "https://www.metacritic.com/search/ASTRO%20BOT/",
            ),
            entry.externalLinks(null).map { it.url },
        )
    }
}
