package app.playlog.core.data

import app.playlog.core.model.DailyPlaytime
import app.playlog.core.model.GameId
import app.playlog.core.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate

class PlayInsightsCalculatorTest {
    // A Wednesday.
    private val today = LocalDate.parse("2026-10-21")
    private val game = GameId(Platform.STEAM, "1")

    private fun day(date: String, minutes: Long) = DailyPlaytime(game, LocalDate.parse(date), Duration.ofMinutes(minutes))

    @Test
    fun `calendar covers whole weeks from a Monday up to today`() {
        val insights = PlayInsightsCalculator.insights(emptyList(), today)

        assertEquals(LocalDate.parse("2025-10-20"), insights.days.first().date)
        assertEquals(DayOfWeek.MONDAY, insights.days.first().date.dayOfWeek)
        assertEquals(today, insights.days.last().date)
        assertEquals(52 * 7 + 3, insights.days.size)
    }

    @Test
    fun `streaks, active days and weekday averages`() {
        val rows = listOf(
            day("2026-10-17", 120), day("2026-10-17", 60), // Saturday, two games
            day("2026-10-18", 60),
            day("2026-10-19", 30),
            day("2026-10-21", 15),
            day("2024-01-01", 999), // outside the window
        )

        val insights = PlayInsightsCalculator.insights(rows, today)

        assertEquals(4, insights.activeDays)
        assertEquals(3, insights.longestStreak)
        assertEquals(Duration.ofMinutes(285), insights.total)
        assertEquals(Duration.ofMinutes(180).dividedBy(52), insights.weekdayAverage[DayOfWeek.SATURDAY])
        assertEquals(Duration.ofMinutes(180), insights.days.single { it.date == LocalDate.parse("2026-10-17") }.playtime)
    }
}
