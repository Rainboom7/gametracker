package app.playlog.platform.playstation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class NpIdRegionTest {
    private fun npId(raw: String) = Base64.getEncoder().encodeToString(raw.toByteArray())

    @Test
    fun `region is the suffix after the last dot`() {
        assertEquals("TR", PsnApi.regionFromNpId(npId("someone@a6.tr")))
        assertEquals("US", PsnApi.regionFromNpId(npId("player_1@b7.us")))
    }

    @Test
    fun `garbage yields null`() {
        assertNull(PsnApi.regionFromNpId("not base64!"))
        assertNull(PsnApi.regionFromNpId(npId("no-region")))
    }
}
