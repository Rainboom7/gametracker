package app.playlog.feature.library

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.playlog.core.designsystem.theme.PlaylogTheme
import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration

/** Renders [LibraryScreen] from plain state under Robolectric, no emulator needed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h1600dp")
class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val hades = Game(GameId(Platform.STEAM, "1145360"), "Hades", playtime = Duration.ofHours(40))
    private val astro = Game(GameId(Platform.PLAYSTATION, "concept:1"), "ASTRO BOT", playtime = Duration.ofHours(14))
    private val entries = LibraryEntry.group(listOf(hades, astro))

    private val opened = mutableListOf<GameId>()
    private val queries = mutableListOf<String>()
    private var connectClicks = 0

    private fun show(state: LibraryUiState) = compose.setContent {
        PlaylogTheme {
            LibraryScreen(
                state = state,
                onOpenGame = { opened += it },
                onConnect = { connectClicks++ },
                onQueryChange = { queries += it },
                onPlatformSelected = {},
                onSortSelected = {},
                onRefresh = {},
            )
        }
    }

    private fun content(entries: List<LibraryEntry> = this.entries, query: String = "") = LibraryUiState.Content(
        entries = entries,
        nowPlaying = null,
        totalCount = this.entries.size,
        totalPlaytime = Duration.ofHours(54),
        availablePlatforms = setOf(Platform.STEAM, Platform.PLAYSTATION),
        controls = LibraryControls(query = query),
        isSyncing = false,
    )

    @Test
    fun `empty library without accounts offers to connect`() {
        show(LibraryUiState.Empty(hasAccounts = false, isSyncing = false))
        compose.onNodeWithText(context.getString(R.string.library_empty_body)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.library_connect)).performClick()
        assertEquals(1, connectClicks)
    }

    @Test
    fun `empty library during first sync has no connect button`() {
        show(LibraryUiState.Empty(hasAccounts = true, isSyncing = true))
        compose.onNodeWithText(context.getString(R.string.library_empty_syncing)).assertIsDisplayed()
        compose.onAllNodesWithText(context.getString(R.string.library_connect)).assertCountEquals(0)
    }

    @Test
    fun `games are listed and open on tap`() {
        show(content())
        compose.onAllNodesWithText("ASTRO BOT").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("Hades").onFirst().performClick()
        assertEquals(listOf(hades.id), opened)
    }

    @Test
    fun `typing in search reports the query`() {
        show(content())
        compose.onNodeWithText(context.getString(R.string.library_search_hint)).performTextInput("ha")
        assertEquals("ha", queries.last())
    }

    @Test
    fun `search without matches says so`() {
        show(content(entries = emptyList(), query = "halo"))
        compose.onNodeWithText(context.getString(R.string.library_no_results)).assertIsDisplayed()
    }
}
