package app.playlog.core.data

import app.playlog.core.model.Account
import app.playlog.core.model.DailyPlaytime
import app.playlog.core.model.Game
import app.playlog.core.model.Platform
import app.playlog.core.model.Screenshot
import app.playlog.platform.api.AccountLinker
import app.playlog.platform.api.GamePlatformSource
import app.playlog.platform.api.LinkFieldType
import app.playlog.platform.api.LinkFlow
import app.playlog.platform.api.SourceCapability
import java.time.LocalDate

class FakeSource(
    override val platform: Platform,
    override val capabilities: Set<SourceCapability> = setOf(SourceCapability.TOTAL_PLAYTIME),
    var linked: Boolean = true,
    private val storeCountry: suspend () -> String? = { null },
) : GamePlatformSource {
    override suspend fun storeCountry(): String? = storeCountry.invoke()

    var library: List<Game> = emptyList()
    var daily: List<DailyPlaytime> = emptyList()
    var screenshots: List<Screenshot> = emptyList()
    var libraryError: Exception? = null
    var screenshotError: Exception? = null

    override suspend fun fetchLibrary(): List<Game> {
        libraryError?.let { throw it }
        return library
    }

    override suspend fun fetchScreenshots(): List<Screenshot> {
        screenshotError?.let { throw it }
        return screenshots
    }

    override suspend fun fetchDailyPlaytime(from: LocalDate): List<DailyPlaytime> = daily.filter { !it.date.isBefore(from) }

    override val linker = object : AccountLinker {
        override suspend fun start() = LinkFlow(emptyList(), emptyList())
        override suspend fun complete(flow: LinkFlow, input: Map<LinkFieldType, String>) = current()!!
        override suspend fun current() = if (linked) Account(platform, "user", "${platform.displayName} user") else null
        override suspend fun unlink() {
            linked = false
        }
    }
}
