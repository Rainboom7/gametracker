package app.playlog.feature.recap

import app.playlog.core.model.ActivityPeriod
import app.playlog.core.model.ActivitySummary
import app.playlog.core.model.DayTotal
import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Platform
import app.playlog.core.model.TopGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class RecapPagesTest {
    private val start = LocalDate.parse("2026-10-12")
    private val end = LocalDate.parse("2026-10-18")

    private fun top(title: String, hours: Long, share: Float) =
        TopGame(LibraryEntry(title, listOf(Game(GameId(Platform.STEAM, title), title))), Duration.ofHours(hours), share)

    private fun summary(total: Duration, tops: List<TopGame>, perPlatform: Map<Platform, Duration>) = ActivitySummary(
        period = ActivityPeriod.WEEK,
        start = start,
        endInclusive = end,
        total = total,
        previousTotal = Duration.ofHours(4),
        buckets = emptyList(),
        perPlatform = perPlatform,
        topGames = tops,
        gamesPlayed = tops.size,
        activeDays = 4,
        bestDay = DayTotal(start.plusDays(5), Duration.ofHours(3)),
    )

    @Test
    fun `quiet period is just intro and empty card`() {
        val pages = RecapPages.build(summary(Duration.ZERO, emptyList(), emptyMap()), longestStreak = 3, taste = listOf("Roguelike"))

        assertEquals(listOf(RecapPage.Intro(ActivityPeriod.WEEK, start, end), RecapPage.Empty), pages)
    }

    @Test
    fun `full week tells the story in order and ends with the shareable card`() {
        val tops = (1..7).map { top("Game $it", 8L - it, 0.1f) }
        val pages = RecapPages.build(
            summary(Duration.ofHours(10), tops, mapOf(Platform.STEAM to Duration.ofHours(7), Platform.NINTENDO to Duration.ofHours(3))),
            longestStreak = 5,
            taste = listOf("Roguelike", "Co-op", "Indie", "Puzzle"),
        )

        assertEquals(
            listOf(
                RecapPage.Intro::class, RecapPage.Total::class, RecapPage.TopGameOf::class, RecapPage.Ranking::class,
                RecapPage.Platforms::class, RecapPage.Rhythm::class, RecapPage.Vibe::class, RecapPage.Outro::class,
            ),
            pages.map { it::class },
        )
        assertEquals(5, (pages[3] as RecapPage.Ranking).games.size)
        val platforms = pages[4] as RecapPage.Platforms
        assertEquals(Platform.STEAM, platforms.leader)
        assertEquals(0.7f, platforms.shares.first().second, 0.001f)
        assertEquals(listOf("Roguelike", "Co-op", "Indie"), (pages[6] as RecapPage.Vibe).tags)
        val outro = (pages.last() as RecapPage.Outro).summary
        assertEquals("Game 1", outro.topGame)
        assertEquals(7, outro.gamesPlayed)
    }

    @Test
    fun `single game and no taste skip ranking and vibe`() {
        val pages = RecapPages.build(
            summary(Duration.ofHours(2), listOf(top("Hades", 2, 1f)), mapOf(Platform.STEAM to Duration.ofHours(2), Platform.PLAYSTATION to Duration.ZERO)),
            longestStreak = 0,
            taste = emptyList(),
        )

        assertTrue(pages.none { it is RecapPage.Ranking || it is RecapPage.Vibe })
        assertEquals(listOf(Platform.STEAM), (pages.first { it is RecapPage.Platforms } as RecapPage.Platforms).shares.map { it.first })
    }
}
