package app.playlog.core.database

import androidx.test.core.app.ApplicationProvider
import app.playlog.core.data.AccountStore
import app.playlog.core.data.LibraryStore
import app.playlog.core.testing.AccountStoreContract
import app.playlog.core.testing.LibraryStoreContract
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The same contract the in-memory stores pass in :core:data, here against a real SQLite (Robolectric).

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomLibraryStoreContractTest : LibraryStoreContract() {
    private val db = PlaylogDatabase.inMemory(ApplicationProvider.getApplicationContext())

    override fun createStore(): LibraryStore = RoomLibraryStore(db)

    override fun close() = db.close()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomAccountStoreContractTest : AccountStoreContract() {
    private val db = PlaylogDatabase.inMemory(ApplicationProvider.getApplicationContext())

    override fun createStore(): AccountStore = RoomAccountStore(db)

    override fun close() = db.close()
}
