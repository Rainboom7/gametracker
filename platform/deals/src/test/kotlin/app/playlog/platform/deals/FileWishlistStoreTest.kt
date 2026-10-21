package app.playlog.platform.deals

import app.playlog.core.model.Platform
import app.playlog.core.model.StorePrice
import app.playlog.core.model.WishlistItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class FileWishlistStoreTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `round trips items and survives a corrupt file`() = runTest {
        val file = File(temp.root, "wishlist.json")
        val item = WishlistItem(
            Platform.NINTENDO, "7001", "Hollow Knight", "h", "u", Instant.ofEpochMilli(5),
            StorePrice("32,00 zł", "64,00 zł", 50, 32.0, Instant.ofEpochMilli(9)), Instant.ofEpochMilli(7), 50,
        )

        FileWishlistStore(file).write(listOf(item))
        assertEquals(listOf(item), FileWishlistStore(file).read())

        file.writeText("{broken")
        assertEquals(emptyList<WishlistItem>(), FileWishlistStore(file).read())
    }
}
