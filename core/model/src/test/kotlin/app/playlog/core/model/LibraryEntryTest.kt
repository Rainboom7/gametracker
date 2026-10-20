package app.playlog.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class LibraryEntryTest {
    private fun game(platform: Platform, id: String, title: String, hours: Long, cover: String? = null, hidden: Boolean = false) =
        Game(
            id = GameId(platform, id),
            title = title,
            playtime = Duration.ofHours(hours),
            artwork = GameArtwork(coverUrl = cover),
            lastPlayedAt = Instant.ofEpochSecond(hours),
            isHidden = hidden,
        )

    @Test
    fun mergesSameTitleAcrossPlatformsAndSumsPlaytime() {
        val entries = LibraryEntry.group(
            listOf(
                game(Platform.STEAM, "1145360", "Hades", 30, cover = "steam.jpg"),
                game(Platform.NINTENDO, "0100535012974000", "Hades", 50, cover = "switch.jpg"),
                game(Platform.PLAYSTATION, "PPSA1", "Hades II", 5),
            ),
        )
        assertEquals(2, entries.size)
        val hades = entries.first { it.title == "Hades" }
        assertEquals(Duration.ofHours(80), hades.totalPlaytime)
        assertEquals(setOf(Platform.STEAM, Platform.NINTENDO), hades.platforms)
        assertEquals(Platform.NINTENDO, hades.primary.id.platform)
        assertEquals("steam.jpg", hades.coverUrl)
        assertEquals(Duration.ofHours(30), hades.playtimeOn(Platform.STEAM))
    }

    @Test
    fun hiddenGamesAreDropped() {
        val entries = LibraryEntry.group(listOf(game(Platform.STEAM, "1", "Tool", 1, hidden = true)))
        assertEquals(emptyList<LibraryEntry>(), entries)
    }

    @Test
    fun gameIdRoundTrips() {
        val id = GameId(Platform.PLAYSTATION, "concept:10002456")
        assertEquals(id, GameId.decode(id.encode()))
    }

    @Test
    fun fullAchievementsSuggestCompletionUntilMarked() {
        val done = Game(GameId(Platform.PLAYSTATION, "PPSA1"), "Astro Bot", achievementProgress = 100)
        assertEquals(true, LibraryEntry("astro bot", listOf(done)).suggestCompleted)
        val marked = LibraryEntry("astro bot", listOf(done.copy(status = GameStatus.COMPLETED)))
        assertEquals(false, marked.suggestCompleted)
        assertEquals(GameStatus.COMPLETED, marked.status)
    }
}
