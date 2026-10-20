package app.playlog.core.data

import app.playlog.core.model.ActivityPeriod
import app.playlog.core.model.DailyPlaytime
import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class ActivityCalculatorTest {
    private val hadesSteam = GameId(Platform.STEAM, "1145360")
    private val hadesSwitch = GameId(Platform.NINTENDO, "0100535012974000")
    private val astro = GameId(Platform.PLAYSTATION, "PPSA08338_00")
    private val entries = LibraryEntry.group(
        listOf(Game(hadesSteam, "Hades"), Game(hadesSwitch, "Hades"), Game(astro, "Astro Bot")),
    )

    private fun d(id: GameId, date: String, minutes: Long) = DailyPlaytime(id, LocalDate.parse(date), Duration.ofMinutes(minutes))

    // 2025-10-20 is a Monday.
    private val daily = listOf(
        d(hadesSteam, "2025-10-20", 60),
        d(hadesSwitch, "2025-10-21", 30),
        d(astro, "2025-10-21", 45),
        d(astro, "2025-10-26", 15),
        d(astro, "2025-10-15", 120), // previous week
    )

    @Test
    fun weekHasSevenDailyBucketsAndComparesWithPreviousWeek() {
        val s = ActivityCalculator.summarize(ActivityPeriod.WEEK, LocalDate.parse("2025-10-23"), daily, entries)

        assertEquals(LocalDate.parse("2025-10-20"), s.start)
        assertEquals(LocalDate.parse("2025-10-26"), s.endInclusive)
        assertEquals(7, s.buckets.size)
        assertEquals(Duration.ofMinutes(150), s.total)
        assertEquals(Duration.ofMinutes(120), s.previousTotal)
        assertEquals(Duration.ofMinutes(75), s.buckets[1].total)
        assertEquals(Duration.ofMinutes(30), s.buckets[1].perPlatform[Platform.NINTENDO])
    }

    @Test
    fun topGamesMergeTitlesAcrossPlatforms() {
        val s = ActivityCalculator.summarize(ActivityPeriod.WEEK, LocalDate.parse("2025-10-23"), daily, entries)

        assertEquals(listOf("Hades", "Astro Bot"), s.topGames.map { it.entry.title })
        assertEquals(Duration.ofMinutes(90), s.topGames[0].playtime)
        assertEquals(0.6f, s.topGames[0].share, 0.001f)
    }

    @Test
    fun monthIsSplitIntoWeeksClippedToTheMonth() {
        val s = ActivityCalculator.summarize(ActivityPeriod.MONTH, LocalDate.parse("2025-10-23"), daily, entries)

        // Oct 2025: Wed 1st … Fri 31st → 1-5, 6-12, 13-19, 20-26, 27-31
        assertEquals(5, s.buckets.size)
        assertEquals(LocalDate.parse("2025-10-01"), s.buckets.first().start)
        assertEquals(LocalDate.parse("2025-10-05"), s.buckets.first().endInclusive)
        assertEquals(LocalDate.parse("2025-10-31"), s.buckets.last().endInclusive)
        assertEquals(Duration.ofMinutes(270), s.total)
    }

    @Test
    fun yearHasTwelveMonths() {
        val s = ActivityCalculator.summarize(ActivityPeriod.YEAR, LocalDate.parse("2025-10-23"), daily, entries)
        assertEquals(12, s.buckets.size)
        assertEquals(Duration.ofMinutes(270), s.buckets[9].total)
    }

    @Test
    fun lastDaysAreOldestFirstWithZeros() {
        val days = ActivityCalculator.lastDays(daily.filter { it.gameId == astro }, LocalDate.parse("2025-10-26"), 14)
        assertEquals(14, days.size)
        assertEquals(LocalDate.parse("2025-10-13"), days.first().start)
        assertEquals(Duration.ofMinutes(15), days.last().total)
        assertEquals(Duration.ZERO, days[days.size - 2].total)
    }

    @Test
    fun streakCountsConsecutiveDaysEndingTodayOrYesterday() {
        assertEquals(2, ActivityCalculator.currentStreak(daily, LocalDate.parse("2025-10-21")))
        assertEquals(2, ActivityCalculator.currentStreak(daily, LocalDate.parse("2025-10-22")))
        assertEquals(0, ActivityCalculator.currentStreak(daily, LocalDate.parse("2025-10-24")))
    }
}
