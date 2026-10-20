package app.playlog.platform.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamProfileInputTest {
    @Test
    fun parsesAllSupportedForms() {
        assertEquals(SteamProfileInput.Id64("76561197960287930"), SteamProfileInput.parse(" 76561197960287930 "))
        assertEquals(SteamProfileInput.Id64("76561197960287930"), SteamProfileInput.parse("https://steamcommunity.com/profiles/76561197960287930/"))
        assertEquals(SteamProfileInput.Vanity("gaben"), SteamProfileInput.parse("https://steamcommunity.com/id/gaben"))
        assertEquals(SteamProfileInput.Vanity("gaben"), SteamProfileInput.parse("gaben"))
    }

    @Test
    fun rejectsGarbage() {
        assertNull(SteamProfileInput.parse(""))
        assertNull(SteamProfileInput.parse("https://example.com/whatever"))
        assertNull(SteamProfileInput.parse("two words"))
    }
}
