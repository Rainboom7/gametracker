package app.playlog.core.database

import app.playlog.core.model.BadgeRecord
import app.playlog.core.model.GameNote
import app.playlog.core.model.MilestoneKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class JournalFileStoresTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `notes survive a restart and empty ones are removed`() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val file = folder.root.resolve("notes.json")
        val note = GameNote(9, "Лучший финал", Instant.ofEpochMilli(1_000))
        FileGameNotesStore(file, io).put("steam:1", note)

        val reopened = FileGameNotesStore(file, io)
        assertEquals(mapOf("steam:1" to note), reopened.observe().first())
        reopened.put("steam:1", GameNote(updatedAt = Instant.EPOCH))
        assertTrue(reopened.observe().first().isEmpty())
    }

    @Test
    fun `badge records round-trip and unknown kinds from newer versions are skipped`() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val file = folder.root.resolve("badges.json")
        file.writeText("""{"FUTURE":{"level":1},"STREAK":{"level":2,"unlockedAt":5,"seenLevel":1}}""")

        val store = FileBadgeStore(file, io)
        assertEquals(mapOf(MilestoneKind.STREAK to BadgeRecord(2, Instant.ofEpochMilli(5), 1)), store.observe().first())
        store.write(mapOf(MilestoneKind.CRITIC to BadgeRecord(1, null, 1)))
        assertEquals(mapOf(MilestoneKind.CRITIC to BadgeRecord(1, null, 1)), FileBadgeStore(file, io).observe().first())
    }
}
