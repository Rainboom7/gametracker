package app.playlog.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class WeeklyDigestTest {
    private fun at(text: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(text), ZoneOffset.UTC)
    private val week = LocalDate.parse("2026-10-19")

    @Test
    fun `due from sunday evening until monday noon, once per week`() {
        assertNull(WeeklyDigest.dueWeek(at("2026-10-25T17:59"), null))
        assertEquals(week, WeeklyDigest.dueWeek(at("2026-10-25T18:00"), null))
        assertEquals(week, WeeklyDigest.dueWeek(at("2026-10-26T09:00"), LocalDate.parse("2026-10-12")))
        assertNull(WeeklyDigest.dueWeek(at("2026-10-26T09:00"), week))
        assertNull(WeeklyDigest.dueWeek(at("2026-10-26T12:00"), null))
        assertNull(WeeklyDigest.dueWeek(at("2026-10-22T20:00"), null))
    }
}
