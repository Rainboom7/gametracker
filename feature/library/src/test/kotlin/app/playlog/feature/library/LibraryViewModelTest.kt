package app.playlog.feature.library

import app.playlog.core.data.DefaultAccountRepository
import app.playlog.core.data.DefaultLibraryRepository
import app.playlog.core.data.InMemoryAccountStore
import app.playlog.core.data.InMemoryLibraryStore
import app.playlog.core.data.InMemoryUserSettingsRepository
import app.playlog.core.model.Account
import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.Platform
import app.playlog.core.testing.FakeSyncScheduler
import app.playlog.core.testing.FixedClock
import app.playlog.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Duration

class LibraryViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val clock = FixedClock()
    private val libraryStore = InMemoryLibraryStore()
    private val accountStore = InMemoryAccountStore()
    private val scheduler = FakeSyncScheduler()

    private val viewModel by lazy {
        LibraryViewModel(
            library = DefaultLibraryRepository(libraryStore),
            accounts = DefaultAccountRepository(emptySet(), accountStore, libraryStore),
            syncScheduler = scheduler,
            clock = clock,
            settings = InMemoryUserSettingsRepository(),
        )
    }

    private val hades = Game(
        GameId(Platform.STEAM, "1145360"),
        "Hades",
        playtime = Duration.ofHours(40),
        lastPlayedAt = clock.instant.minusSeconds(3_600),
    )
    private val hadesSwitch = Game(GameId(Platform.NINTENDO, "0100535012974000"), "Hades", playtime = Duration.ofHours(10))
    private val astro = Game(GameId(Platform.PLAYSTATION, "concept:1"), "ASTRO BOT", playtime = Duration.ofHours(14))

    private suspend fun seed() {
        libraryStore.upsertGames(Platform.STEAM, listOf(hades))
        libraryStore.upsertGames(Platform.NINTENDO, listOf(hadesSwitch))
        libraryStore.upsertGames(Platform.PLAYSTATION, listOf(astro))
    }

    private suspend fun content(predicate: (LibraryUiState.Content) -> Boolean = { true }) =
        viewModel.uiState.first { it is LibraryUiState.Content && predicate(it) } as LibraryUiState.Content

    @Test
    fun `no games and no accounts asks to connect a platform`() = runTest {
        val state = viewModel.uiState.first { it != LibraryUiState.Loading }
        assertEquals(LibraryUiState.Empty(hasAccounts = false, isSyncing = false), state)
    }

    @Test
    fun `linked account during the first sync shows the syncing empty state`() = runTest {
        accountStore.upsert(Account(Platform.STEAM, "765", "gaben"))
        scheduler.isSyncing.value = true
        val state = viewModel.uiState.first { it is LibraryUiState.Empty && it.hasAccounts }
        assertEquals(LibraryUiState.Empty(hasAccounts = true, isSyncing = true), state)
    }

    @Test
    fun `content merges platforms and sums totals`() = runTest {
        seed()
        val state = content()
        assertEquals(2, state.totalCount)
        assertEquals(Duration.ofHours(64), state.totalPlaytime)
        assertEquals(setOf(Platform.STEAM, Platform.PLAYSTATION, Platform.NINTENDO), state.availablePlatforms)
        assertEquals(listOf("Hades", "ASTRO BOT"), state.entries.map { it.title })
        assertEquals("Hades", state.nowPlaying?.title)
    }

    @Test
    fun `search filters entries and hides the now-playing card`() = runTest {
        seed()
        viewModel.onQueryChange("astro")
        val state = content { it.controls.query == "astro" }
        assertEquals(listOf("ASTRO BOT"), state.entries.map { it.title })
        assertNull(state.nowPlaying)
        assertEquals("totals describe the whole library, not the search", 2, state.totalCount)
    }

    @Test
    fun `platform filter and sort are applied`() = runTest {
        seed()
        viewModel.onPlatformSelected(Platform.PLAYSTATION)
        assertEquals(listOf("ASTRO BOT"), content { it.controls.platform == Platform.PLAYSTATION }.entries.map { it.title })

        viewModel.onPlatformSelected(null)
        viewModel.onSortSelected(LibrarySort.TITLE)
        val sorted = content { it.controls.platform == null && it.controls.sort == LibrarySort.TITLE }
        assertEquals(listOf("ASTRO BOT", "Hades"), sorted.entries.map { it.title })
    }

    @Test
    fun `hidden games disappear from the library`() = runTest {
        seed()
        libraryStore.setHidden(astro.id, true)
        assertEquals(listOf("Hades"), content { it.totalCount == 1 }.entries.map { it.title })
    }

    @Test
    fun `sync progress is reflected in content`() = runTest {
        seed()
        scheduler.isSyncing.value = true
        content { it.isSyncing }
    }

    @Test
    fun `refresh requests a sync of every platform`() {
        viewModel.refresh()
        assertEquals(listOf<Platform?>(null), scheduler.requests)
    }
}
