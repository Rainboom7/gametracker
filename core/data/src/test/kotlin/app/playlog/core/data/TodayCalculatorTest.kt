package app.playlog.core.data

import app.playlog.core.model.DailyPlaytime
import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.Giveaway
import app.playlog.core.model.GiveawayStore
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Platform
import app.playlog.core.model.Screenshot
import app.playlog.core.model.StorePrice
import app.playlog.core.model.TodayItem
import app.playlog.core.model.WishlistItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TodayCalculatorTest {
    private val today = LocalDate.parse("2026-10-21")
    private val hades = GameId(Platform.STEAM, "1")
    private val tunic = GameId(Platform.PLAYSTATION, "2")
    private val entries = listOf(
        LibraryEntry("hades", listOf(Game(hades, "Hades", firstPlayedAt = Instant.parse("2020-10-21T10:00:00Z")))),
        LibraryEntry("tunic", listOf(Game(tunic, "Tunic", firstPlayedAt = Instant.parse("2023-10-21T10:00:00Z")))),
    )

    @Test
    fun `memories group play by title and year, captures lead, anniversaries do not repeat`() {
        val daily = listOf(
            DailyPlaytime(hades, LocalDate.parse("2024-10-21"), Duration.ofHours(2)),
            DailyPlaytime(hades, LocalDate.parse("2024-10-21"), Duration.ofMinutes(30)),
            DailyPlaytime(hades, LocalDate.parse("2024-10-22"), Duration.ofHours(5)),
        )
        val shots = listOf(Screenshot("s", tunic, Platform.PLAYSTATION, "u", takenAt = Instant.parse("2025-10-21T20:00:00Z")))

        val items = TodayCalculator.memories(entries, daily, shots, today, ZoneOffset.UTC)

        assertEquals(
            listOf(
                TodayItem.ShotsOnThisDay(shots, 1, entries[1]),
                TodayItem.PlayedOnThisDay(entries[0], 2, Duration.ofMinutes(150)),
            ),
            items,
        )
    }

    @Test
    fun `only offers ending within their window are listed`() {
        val now = Instant.parse("2026-10-21T12:00:00Z")
        fun wish(id: String, ends: Instant?) = WishlistItem(
            Platform.STEAM, id, id, "h", "u", now, price = StorePrice("1", "2", 50, 1.0, ends),
        )
        val wished = listOf(wish("soon", now.plus(Duration.ofHours(30))), wish("later", now.plus(Duration.ofDays(5))), wish("none", null))
        val free = listOf(
            Giveaway("g", "Free", null, "u", GiveawayStore.EPIC, endsAt = now.plus(Duration.ofHours(3))),
            Giveaway("o", "Owned", null, "u", GiveawayStore.EPIC, endsAt = now.plus(Duration.ofHours(3)), owned = true),
        )

        assertEquals(
            listOf(TodayItem.SaleEndingSoon(wished[0]), TodayItem.GiveawayEndingSoon(free[0])),
            TodayCalculator.endingSoon(wished, free, now),
        )
    }
}
