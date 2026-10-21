package app.playlog.feature.library

import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.GameStatus
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class LibraryFilterTest {
    private val now = Instant.parse("2025-10-20T12:00:00Z")

    private fun game(platform: Platform, id: String, title: String, hours: Long, lastPlayed: Instant? = null, firstPlayed: Instant? = null) =
        Game(GameId(platform, id), title, playtime = Duration.ofHours(hours), lastPlayedAt = lastPlayed, firstPlayedAt = firstPlayed)

    private val hadesSteam = game(Platform.STEAM, "1145360", "Hades", 40, now.minusSeconds(3_600), Instant.parse("2021-01-01T00:00:00Z"))
    private val hadesSwitch = game(Platform.NINTENDO, "0100535012974000", "Hades", 10, now.minusSeconds(86_400 * 3))
    private val zelda = game(Platform.NINTENDO, "0100F2C0115B6000", "The Legend of Zelda: Tears of the Kingdom", 120, now.minusSeconds(86_400 * 2))
    private val pokemon = game(Platform.NINTENDO, "01008DB008C2C000", "Pokémon Scarlet", 5)
    private val astro = game(Platform.PLAYSTATION, "concept:1", "ASTRO BOT", 14, now.minusSeconds(86_400 * 10), Instant.parse("2024-09-06T00:00:00Z"))
    private val alanWake = game(Platform.PLAYSTATION, "concept:2", "alan wake 2", 14)

    private val entries = LibraryEntry.group(listOf(hadesSteam, hadesSwitch, zelda, pokemon, astro, alanWake))

    private fun titles(query: String = "", platform: Platform? = null, sort: LibrarySort = LibrarySort.TITLE) =
        LibraryFilter.apply(entries, query, platform, sort).map { it.title }

    @Test
    fun `cross-platform copies are one entry with summed playtime`() {
        val hades = entries.single { it.title == "Hades" }
        assertEquals(Duration.ofHours(50), hades.totalPlaytime)
        assertEquals(setOf(Platform.STEAM, Platform.NINTENDO), hades.platforms)
    }

    @Test
    fun `search ignores case, accents and punctuation`() {
        assertEquals(listOf("Pokémon Scarlet"), titles("pokemon"))
        assertEquals(listOf("The Legend of Zelda: Tears of the Kingdom"), titles("zelda tears"))
        assertEquals(listOf("ASTRO BOT"), titles("  Astro "))
    }

    @Test
    fun `blank query keeps everything`() {
        assertEquals(entries.size, titles("   ").size)
    }

    @Test
    fun `query with no match gives an empty list`() {
        assertEquals(emptyList<String>(), titles("halo"))
    }

    @Test
    fun `platform filter keeps merged entries that include the platform`() {
        assertEquals(listOf("Hades"), titles(platform = Platform.STEAM))
        assertEquals(listOf("Hades", "Pokémon Scarlet", "The Legend of Zelda: Tears of the Kingdom"), titles(platform = Platform.NINTENDO))
    }

    @Test
    fun `title sort is case-insensitive`() {
        assertEquals(listOf("alan wake 2", "ASTRO BOT", "Hades"), titles().take(3))
    }

    @Test
    fun `playtime sort is descending, ties broken by title`() {
        assertEquals(
            listOf("The Legend of Zelda: Tears of the Kingdom", "Hades", "alan wake 2", "ASTRO BOT", "Pokémon Scarlet"),
            titles(sort = LibrarySort.PLAYTIME),
        )
    }

    @Test
    fun `last played sort puts never-played games last`() {
        assertEquals(
            listOf("Hades", "The Legend of Zelda: Tears of the Kingdom", "ASTRO BOT", "alan wake 2", "Pokémon Scarlet"),
            titles(sort = LibrarySort.LAST_PLAYED),
        )
    }

    @Test
    fun `first played sort is newest first with unknown dates last`() {
        assertEquals(listOf("ASTRO BOT", "Hades"), titles(sort = LibrarySort.FIRST_PLAYED).take(2))
    }

    @Test
    fun `now playing is the latest title within the window`() {
        assertEquals("Hades", LibraryFilter.nowPlaying(entries, now)?.title)
        assertNull(LibraryFilter.nowPlaying(entries, now.plus(Duration.ofDays(2))))
        assertEquals(
            "Hades",
            LibraryFilter.nowPlaying(entries, now.plus(Duration.ofDays(2)), window = Duration.ofDays(7))?.title,
        )
    }

    @Test
    fun `multi-platform filter and sort put cross-platform titles first`() {
        assertEquals(listOf("Hades"), LibraryFilter.apply(entries, "", null, LibrarySort.TITLE, LibraryFilters(multiPlatformOnly = true)).map { it.title })
        assertEquals("Hades", titles(sort = LibrarySort.PLATFORM_COUNT).first())
    }

    @Test
    fun `status and favorite filters combine`() {
        val marked = LibraryEntry.group(
            listOf(hadesSteam.copy(status = GameStatus.COMPLETED, isFavorite = true), zelda.copy(status = GameStatus.COMPLETED), astro),
        )
        val completed = LibraryFilters(status = GameStatus.COMPLETED)

        assertEquals(
            listOf("Hades", "The Legend of Zelda: Tears of the Kingdom"),
            LibraryFilter.apply(marked, "", null, LibrarySort.TITLE, completed).map { it.title },
        )
        assertEquals(listOf("Hades"), LibraryFilter.apply(marked, "", null, LibrarySort.TITLE, completed.copy(favoritesOnly = true)).map { it.title })
        assertEquals(2, completed.copy(favoritesOnly = true).activeCount)
    }

    @Test
    fun `achievements sort puts unknown progress last`() {
        val withProgress = LibraryEntry.group(listOf(zelda, astro.copy(achievementProgress = 40), alanWake.copy(achievementProgress = 90)))
        assertEquals(
            listOf("alan wake 2", "ASTRO BOT", "The Legend of Zelda: Tears of the Kingdom"),
            LibraryFilter.apply(withProgress, "", null, LibrarySort.ACHIEVEMENTS).map { it.title },
        )
    }

    @Test
    fun `platform summaries count merged titles and per-platform hours`() {
        assertEquals(
            listOf(
                PlatformSummary(Platform.STEAM, 1, Duration.ofHours(40)),
                PlatformSummary(Platform.PLAYSTATION, 2, Duration.ofHours(28)),
                PlatformSummary(Platform.NINTENDO, 3, Duration.ofHours(135)),
            ),
            LibraryFilter.platformSummaries(entries),
        )
    }

    @Test
    fun `now playing on an empty library is null`() {
        assertNull(LibraryFilter.nowPlaying(emptyList(), now))
    }
}
