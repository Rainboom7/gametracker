package app.playlog.core.testing

import app.playlog.core.data.AccountStore
import app.playlog.core.model.Account
import app.playlog.core.model.Platform
import app.playlog.core.model.SyncFailure
import app.playlog.core.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Test
import java.time.Instant

/** Behaviour every [AccountStore] must share; see [LibraryStoreContract]. */
abstract class AccountStoreContract {
    protected abstract fun createStore(): AccountStore

    /** Releases resources, e.g. closes the database. */
    protected open fun close() = Unit

    private val store by lazy { createStore() }

    @After
    fun tearDownStore() = close()

    private val syncedAt = Instant.parse("2025-10-20T08:00:00Z")
    private val steam = Account(Platform.STEAM, "76561197960287930", "gaben", "https://avatars/g.jpg", lastSyncedAt = syncedAt)
    private val psn = Account(Platform.PLAYSTATION, "kratos", "kratos")
    private val nintendo = Account(Platform.NINTENDO, "a1b2c3", "Link")

    private suspend fun accounts() = store.observeAccounts().first()

    @Test
    fun `accounts round-trip and are ordered by platform`() = runTest {
        store.upsert(nintendo)
        store.upsert(steam)
        store.upsert(psn)
        assertEquals(listOf(steam, psn, nintendo), accounts())
    }

    @Test
    fun `relinking without lastSyncedAt keeps the stored sync time`() = runTest {
        store.upsert(steam)
        store.upsert(steam.copy(displayName = "Gabe", lastSyncedAt = null))
        val stored = accounts().single()
        assertEquals("Gabe", stored.displayName)
        assertEquals(syncedAt, stored.lastSyncedAt)
    }

    @Test
    fun `sync state round-trips, including failures`() = runTest {
        store.upsert(psn)
        val later = syncedAt.plusSeconds(600)

        store.updateSyncState(Platform.PLAYSTATION, SyncState.Running)
        assertEquals(SyncState.Running, accounts().single().syncState)

        store.updateSyncState(Platform.PLAYSTATION, SyncState.Failed(SyncFailure.AUTH_EXPIRED, "npsso expired"))
        assertEquals(SyncState.Failed(SyncFailure.AUTH_EXPIRED, "npsso expired"), accounts().single().syncState)

        store.updateSyncState(Platform.PLAYSTATION, SyncState.Idle, later)
        val stored = accounts().single()
        assertEquals(SyncState.Idle, stored.syncState)
        assertEquals(later, stored.lastSyncedAt)
    }

    @Test
    fun `sync state of an unlinked platform is ignored`() = runTest {
        store.updateSyncState(Platform.NINTENDO, SyncState.Running)
        assertEquals(emptyList<Account>(), accounts())
    }

    @Test
    fun `delete removes only that account`() = runTest {
        store.upsert(steam)
        store.upsert(psn)
        store.delete(Platform.STEAM)
        assertEquals(listOf(psn), accounts())
        assertNull(accounts().firstOrNull { it.platform == Platform.STEAM })
    }
}
