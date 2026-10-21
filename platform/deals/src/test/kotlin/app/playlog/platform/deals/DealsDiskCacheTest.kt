package app.playlog.platform.deals

import app.playlog.core.data.CachedDeals
import app.playlog.core.model.DealPick
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class DealsDiskCacheTest {
    @get:Rule
    val temp = TemporaryFolder()

    private var now = 1_000_000_000L

    private fun cache(dir: File) = DealsDiskCache(dir, nowMillis = { now }, maxAgeMillis = 1_000)

    @Test
    fun `picks and tags survive a restart`() = runTest {
        val dir = temp.newFolder()
        val pick = DealPick(588650, "Dead Cells", "h.jpg", listOf("s.jpg"), "desc", 60, "207 руб.", "519 руб.", 96, listOf("Hades"), listOf("Roguelite"), 0.8f)
        cache(dir).apply {
            write(CachedDeals(listOf(pick), Instant.ofEpochMilli(5)))
            putTags(1, mapOf("Roguelite" to 10))
            putAppId("celeste", 504230)
            flush()
        }

        val reopened = cache(dir)
        assertEquals(CachedDeals(listOf(pick), Instant.ofEpochMilli(5)), reopened.read())
        assertEquals(mapOf("Roguelite" to 10), reopened.tags(1))
        assertEquals(504230L, reopened.appId("celeste"))
    }

    @Test
    fun `old tags expire and a corrupt file is ignored`() = runTest {
        val dir = temp.newFolder()
        cache(dir).apply { putTags(1, mapOf("A" to 1)); flush() }
        now += 5_000
        assertNull(cache(dir).tags(1))

        File(dir, "picks.json").writeText("{not json")
        assertNull(cache(dir).read())
    }
}
