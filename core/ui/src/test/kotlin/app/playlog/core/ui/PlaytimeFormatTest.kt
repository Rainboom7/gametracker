package app.playlog.core.ui

import app.playlog.core.ui.PlaytimeFormat.Value
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class PlaytimeFormatTest {
    private fun hours(duration: Duration) = (PlaytimeFormat.of(duration) as Value.Hours).text

    @Test
    fun `under an hour is shown in whole minutes`() {
        assertEquals(Value.Minutes(0), PlaytimeFormat.of(Duration.ZERO))
        assertEquals(Value.Minutes(0), PlaytimeFormat.of(Duration.ofSeconds(59)))
        assertEquals(Value.Minutes(59), PlaytimeFormat.of(Duration.ofMinutes(59).plusSeconds(59)))
    }

    @Test
    fun `one to ten hours keep one decimal, locale separator`() {
        assertEquals("1", hours(Duration.ofHours(1)))
        assertTrue(hours(Duration.ofMinutes(90)) in setOf("1.5", "1,5"))
        assertTrue(hours(Duration.ofMinutes(4 * 60 + 33)) in setOf("4.5", "4,5", "4.6", "4,6"))
    }

    @Test
    fun `just under ten hours never shows a misleading decimal`() {
        val text = hours(Duration.ofMinutes(9 * 60 + 59))
        assertTrue(text, text == "10" || text.startsWith("9"))
    }

    @Test
    fun `ten hours and more are whole hours, rounded down`() {
        assertEquals("10", hours(Duration.ofHours(10)))
        assertEquals("243", hours(Duration.ofHours(243).plusMinutes(59)))
        assertEquals("10000", hours(Duration.ofHours(10_000)))
    }

    @Test
    fun `daysAgo counts calendar days in the user's zone`() {
        val today = LocalDate.of(2025, 10, 20)
        val lateEvening = Instant.parse("2025-10-19T22:30:00Z")
        assertEquals(1, PlaytimeFormat.daysAgo(lateEvening, today, ZoneOffset.UTC))
        // 22:30 UTC on the 19th is already the 20th in Moscow.
        assertEquals(0, PlaytimeFormat.daysAgo(lateEvening, today, ZoneId.of("Europe/Moscow")))
        assertEquals(-1, PlaytimeFormat.daysAgo(Instant.parse("2025-10-21T10:00:00Z"), today, ZoneOffset.UTC))
    }
}
