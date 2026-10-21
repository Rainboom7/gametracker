package app.playlog.core.data

import app.playlog.core.model.DailyPlaytime
import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.GameStatus
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Milestone
import app.playlog.core.model.MilestoneKind
import app.playlog.core.model.PickReason
import app.playlog.core.model.Platform
import app.playlog.core.model.RouletteMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

class DiscoverCalculatorTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.parse("2026-10-21")

    private fun game(
        id: String,
        title: String,
        hours: Long = 0,
        daysAgo: Long? = null,
        platform: Platform = Platform.STEAM,
        progress: Int? = null,
        status: GameStatus = GameStatus.NONE,
        favorite: Boolean = false,
        firstPlayed: String? = null,
        devices: Set<String> = emptySet(),
    ) = Game(
        id = GameId(platform, id),
        title = title,
        playtime = Duration.ofHours(hours),
        lastPlayedAt = daysAgo?.let { today.minusDays(it).atStartOfDay(zone).toInstant() },
        firstPlayedAt = firstPlayed?.let { LocalDate.parse(it).atStartOfDay(zone).toInstant() },
        achievementProgress = progress,
        status = status,
        isFavorite = favorite,
        devices = devices,
    )

    private val library = LibraryEntry.group(
        listOf(
            game("1", "Hades", hours = 40, daysAgo = 2),
            game("0100", "Hades", hours = 12, daysAgo = 40, platform = Platform.NINTENDO),
            game("2", "Celeste", hours = 9, daysAgo = 200, progress = 85),
            game("3", "The Witcher 3", hours = 80, daysAgo = 400),
            game("4", "Disco Elysium", hours = 0),
            game("5", "Outer Wilds", hours = 0, favorite = true),
            game("6", "Portal 2", hours = 15, daysAgo = 500, status = GameStatus.COMPLETED),
            game("7", "Anthem", hours = 0, status = GameStatus.DROPPED),
            game("PPSA1", "Bloodborne", hours = 0, platform = Platform.PLAYSTATION, devices = setOf("PS4")),
        ),
    )

    private fun reasonOf(title: String) =
        DiscoverCalculator.picks(library, today, zone).firstOrNull { it.entry.title == title }?.reason

    @Test
    fun eachTitleLandsOnOneShelf() {
        assertEquals(PickReason.CONTINUE, reasonOf("Hades"))
        assertEquals(PickReason.ALMOST_DONE, reasonOf("Celeste"))
        assertEquals(PickReason.RETURN_TO, reasonOf("The Witcher 3"))
        assertEquals(PickReason.BACKLOG, reasonOf("Disco Elysium"))
        assertEquals(PickReason.BACKLOG, reasonOf("Outer Wilds"))
    }

    @Test
    fun completedDroppedAndNoDataTitlesAreNeverSuggested() {
        assertNull(reasonOf("Portal 2"))
        assertNull(reasonOf("Anthem"))
        assertNull(reasonOf("Bloodborne"))
    }

    @Test
    fun summaryKeepsShelfOrderAndPicksAlmostDoneOnTop() {
        val s = DiscoverCalculator.summarize(library, emptyList(), today, zone)

        assertEquals(
            listOf(PickReason.CONTINUE, PickReason.ALMOST_DONE, PickReason.RETURN_TO, PickReason.BACKLOG),
            s.shelves.map { it.reason },
        )
        assertEquals("Celeste", s.topPick?.entry?.title)
        assertEquals(2, s.backlogCount)
        // Favorites lead the backlog shelf.
        assertEquals("Outer Wilds", s.shelves.last().picks.first().entry.title)
    }

    @Test
    fun playingStatusKeepsAGameInContinueForLonger() {
        val entries = LibraryEntry.group(listOf(game("1", "Elden Ring", hours = 3, daysAgo = 60, status = GameStatus.PLAYING)))
        assertEquals(PickReason.CONTINUE, DiscoverCalculator.picks(entries, today, zone).single().reason)
    }

    @Test
    fun rouletteDrawsOnlyFromBacklogAndForgottenOnThePlatform() {
        val drawn = (0 until 50).mapNotNull {
            DiscoverCalculator.roulette(library, today, zone, RouletteQuery(), emptySet(), Random(it))?.title
        }.toSet()
        assertEquals(setOf("The Witcher 3", "Disco Elysium", "Outer Wilds"), drawn)

        assertNull(DiscoverCalculator.roulette(library, today, zone, RouletteQuery(Platform.PLAYSTATION), emptySet(), Random(1)))
    }

    @Test
    fun rouletteAvoidsRepeatingTheLastResult() {
        val witcherKey = library.first { it.title == "The Witcher 3" }.key
        repeat(20) { seed ->
            val pick = DiscoverCalculator.roulette(library, today, zone, RouletteQuery(recent = listOf(witcherKey)), emptySet(), Random(seed))
            assertNotEquals("The Witcher 3", pick?.title)
        }
    }

    @Test
    fun rouletteMoodsNarrowThePool() {
        val liked = library.first { it.title == "Disco Elysium" }.key
        val comfort = (0 until 30).mapNotNull {
            DiscoverCalculator.roulette(library, today, zone, RouletteQuery(mood = RouletteMood.COMFORT), setOf(liked), Random(it))?.title
        }.toSet()
        // Outer Wilds is a favourite, Disco Elysium is rated 8+.
        assertEquals(setOf("Outer Wilds", "Disco Elysium"), comfort)
        assertNull(DiscoverCalculator.roulette(library, today, zone, RouletteQuery(Platform.PLAYSTATION, RouletteMood.COMFORT), setOf(liked), Random(1)))
    }

    @Test
    fun milestonesCountLevelsAndProgress() {
        val daily = listOf("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04", "2026-10-10", "2026-10-11").map {
            DailyPlaytime(GameId(Platform.STEAM, "1"), LocalDate.parse(it), Duration.ofMinutes(30))
        }
        val m = DiscoverCalculator.milestones(library, daily).associateBy { it.kind }

        assertEquals(80L, m.getValue(MilestoneKind.MARATHON).value)
        assertEquals(2, m.getValue(MilestoneKind.MARATHON).level)
        assertEquals(1L, m.getValue(MilestoneKind.MULTIPLATFORM).value)
        assertEquals(1L, m.getValue(MilestoneKind.FINISHER).value)
        assertEquals(4L, m.getValue(MilestoneKind.STREAK).value)
        assertEquals(0, m.getValue(MilestoneKind.PERFECTIONIST).level)
    }

    @Test
    fun milestoneProgressIsMeasuredFromThePreviousLevel() {
        val m = Milestone(MilestoneKind.MARATHON, value = 75, thresholds = listOf(10, 50, 100))
        assertEquals(2, m.level)
        assertEquals(100L, m.nextTarget)
        assertEquals(0.5f, m.progressToNext, 0.001f)
        assertTrue(Milestone(MilestoneKind.MARATHON, 150, listOf(10, 50, 100)).isMaxed)
    }

    @Test
    fun anniversariesMatchTheSameWeekInEarlierYears() {
        val entries = LibraryEntry.group(
            listOf(
                game("1", "Hades", hours = 40, firstPlayed = "2023-10-19"),
                game("2", "Celeste", hours = 9, firstPlayed = "2020-10-24"),
                game("3", "Stardew Valley", hours = 9, firstPlayed = "2024-09-01"),
                game("4", "Balatro", hours = 9, firstPlayed = "2026-10-20"),
            ),
        )
        val a = DiscoverCalculator.anniversaries(entries, today, zone)

        assertEquals(listOf("Celeste" to 6, "Hades" to 3), a.map { it.entry.title to it.years })
    }

    @Test
    fun longestStreakIgnoresZeroDays() {
        val id = GameId(Platform.STEAM, "1")
        val daily = listOf(
            DailyPlaytime(id, LocalDate.parse("2026-10-01"), Duration.ofMinutes(5)),
            DailyPlaytime(id, LocalDate.parse("2026-10-02"), Duration.ZERO),
            DailyPlaytime(id, LocalDate.parse("2026-10-03"), Duration.ofMinutes(5)),
        )
        assertEquals(1, DiscoverCalculator.longestStreak(daily))
    }
}
